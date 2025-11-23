package com.movodream.localguru.data_collection.presentation



import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.core.constants.AppConstants
import com.data.local.AppDatabase
import com.data.local.entity.DraftEntity
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.logger.Logger
import com.movodream.localguru.data_collection.model.FormSchema
import com.movodream.localguru.data_collection.repository.DraftRepository
import com.movodream.localguru.data_collection.ui.activities.DynamicFormActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FormViewModel(app: Application) : AndroidViewModel(app) {


    // -------------------------------------------------------------
    // Room Database + Repository initialization INSIDE ViewModel
    // -------------------------------------------------------------
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(getApplication())
    }

    private val repository by lazy {
        DraftRepository(database.draftDao())
    }
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }

    private val _locationFetchState = MutableLiveData<Boolean>()
    val locationFetchState = _locationFetchState
    sealed class FormState {
        object Idle : FormState()
        data class ValidationError(val errors: Map<String, String>) : FormState()
        data class ReadyToSubmit(val payload: JSONObject) : FormState()
        data class SubmitResult(val success: Boolean, val message: String?) : FormState()
    }

    val photoUris = mutableMapOf<String, MutableList<Uri>>()  // stores URIs per field

    val schemaLive = MutableLiveData<FormSchema?>()
    // current values for all fields
    val valuesLive = MutableLiveData<MutableMap<String, Any?>>().apply { value = mutableMapOf() }
    // in-memory draft store per tab (optimized json-like map)
    val draftPerTab = MutableLiveData<MutableMap<String, Map<String, Any?>>>().apply { value = mutableMapOf() }
    val formState = MutableLiveData<FormState>(FormState.Idle)

    fun loadSchemaFromString(jsonStr: FormSchema?) {
        viewModelScope.launch(Dispatchers.Default) {
//            val j = JSONObject(jsonStr)
//            val parsed = Parser.parseFormSchema(j)
            schemaLive.postValue(jsonStr)
            val map = valuesLive.value ?: mutableMapOf()
            jsonStr!!.tabs.flatMap { it.fields }.forEach { if (!map.containsKey(it.id)) map[it.id] = "" }
            valuesLive.postValue(map)
        }
    }

    fun updateValue(fieldId: String, value: Any?) {
        val m = valuesLive.value ?: mutableMapOf()
        m[fieldId] = value
        valuesLive.postValue(m)
    }


    // Validate a specific tab; returns error map empty if ok
    fun validateTab(tabId: String): Map<String, String> {
        val schema = schemaLive.value ?: return mapOf("form" to "Schema missing")
        val tab = schema.tabs.firstOrNull { it.id == tabId } ?: return mapOf()
        val errors = mutableMapOf<String, String>()
        val map = valuesLive.value ?: mapOf()
        tab.fields.forEach { f ->
            val v = map[f.id]
            if (f.required) {
                val empty = when (v) {
                    null -> true
                    is String -> v.trim().isEmpty()
                    is Collection<*> -> v.isEmpty()
                    else -> false
                }
                if (empty) errors[f.id] = "${f.label} is required"
            }
            if (f.type == "photo_list") {
                val col = v as? Collection<*>
                if (f.required && f.minItems != null && (col == null || col.size < f.minItems!!))
                    errors[f.id] = "Requires at least ${f.minItems} photos"
            }

            f.minLength?.let {
                if (v is String && (f.required || v.isNotEmpty())) { // ✅ Only validate if required or user entered something
                    if ( v.length < it) {
                        errors[f.id] = "${f.label} must be at least $it characters"
                    }
                }
            }

            // ---------------------------
            // REGEX VALIDATION
            // ---------------------------
            if ((f.type == "text" || f.type == "textarea") && !f.regex.isNullOrEmpty()) {
                if (v is String && v.isNotEmpty()) {

                    val regex = try {
                        Regex(f.regex)
                    } catch (e: Exception) {
                        null // avoid crash if backend sends invalid regex
                    }

                    if (regex != null && !regex.matches(v)) {
                        val msg =  "Invalid input"
                        errors[f.id] = msg
                    }
                }
            }

            // ---------------------------
            // NUMBER FIELD VALIDATION
            // ---------------------------
            if (f.type == "number") {
                val raw = v

                // Required check (already handled above), but ensure skip if optional empty
                if (raw == null || raw.toString().trim().isEmpty()) {
                    if (f.required) {
                        errors[f.id] = "${f.label} is required"
                    }
                    return@forEach // skip further checks
                }

                val num = raw.toString().toDoubleOrNull()
                if (num == null) {
                    errors[f.id] =  "${f.label}: Invalid number"
                    return@forEach
                }

                // Min check
                f.min?.let { min ->
                    if (num < min) {
                        errors[f.id] = "${f.label} must be ≥ $min"
                        return@forEach
                    }
                }

                // Max check
                f.max?.let { max ->
                    if (num > max) {
                        errors[f.id] = "${f.label} must be ≤ $max"
                        return@forEach
                    }
                }

//                // Precision check
//                f.precision?.let { p ->
//                    val decimal = raw.toString().substringAfter('.', "")
//                    if (decimal.length > p) {
//                        errors[f.id] = "${f.label} must have max $p decimal places"
//                        return@forEach
//                    }
//                }
            }
        }

        return errors
    }

    // Save draft for a tab in-memory (optimized map)
    fun saveDraftForTab(tabId: String) {
        val map = valuesLive.value ?: return
        // create optimized map: only store fields of this tab
        val schema = schemaLive.value ?: return
        val tab = schema.tabs.firstOrNull { it.id == tabId } ?: return
        val partial = mutableMapOf<String, Any?>()
        tab.fields.forEach { f ->
            map[f.id]?.let { partial[f.id] = it }
        }
        val drafts = draftPerTab.value ?: mutableMapOf()
        drafts[tabId] = partial
        draftPerTab.postValue(drafts)
    }

    // -------------------------------------------------------------
    // Save draft: Only store simple values + FILE PATHS, no Base64
    // -------------------------------------------------------------
    fun saveDraftToRoom(poiId: String) {
        viewModelScope.launch(Dispatchers.IO) {

            val schema = schemaLive.value ?: return@launch
            val values = valuesLive.value ?: return@launch

            val valuesObj = JSONObject()

            values.forEach { (fieldId, v) ->

                // PHOTO LIST → Save files, store file paths
                if (v is List<*>) {

                    val arr = JSONArray()

                    val uriList = photoUris[fieldId] ?: emptyList()

                    val filePaths = uriList.map { uri ->
                        saveImageToInternal(uri)
                    }

                    filePaths.forEach { arr.put(it) }
                    valuesObj.put(fieldId, arr)
                }
                else {
                    valuesObj.put(fieldId, v ?: JSONObject.NULL)
                }
            }

            val root = JSONObject().apply {
                put("values", valuesObj)
            }

            val entity = DraftEntity(
                poiId = poiId,
                formId = schema.formId,
                draftJson = root.toString(),
                updatedAt = System.currentTimeMillis()
            )

            repository.saveDraft(entity)
        }
    }

    // -------------------------------------------------------------
    // Load draft: Convert file path → Uri
    // -------------------------------------------------------------
    fun loadDraft(poiId: String, onLoaded: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {

            val draft = repository.loadDraft(poiId)

            if (draft == null) {
                withContext(Dispatchers.Main) { onLoaded() }
                return@launch
            }

            val json = JSONObject(draft.draftJson)
            val valuesObj = json.getJSONObject("values")

            val restored = mutableMapOf<String, Any?>()

            valuesObj.keys().forEach { key ->
                val v = valuesObj.get(key)

                if (v is JSONArray) {
                    val uriList = mutableListOf<Uri>()

                    for (i in 0 until v.length()) {
                        val path = v.getString(i)
                        uriList.add(Uri.fromFile(File(path)))
                    }

                    photoUris[key] = uriList
                    restored[key] = uriList.map { it.toString() }
                } else {
                    restored[key] = v
                }
            }

            valuesLive.postValue(restored)

            withContext(Dispatchers.Main) { onLoaded() }
        }
    }

    // Build full payload merging draftPerTab + current values
//    fun buildPayload(): JSONObject {
//        val schema = schemaLive.value ?: throw IllegalStateException("schema missing")
//        val payload = JSONObject()
//        payload.put("form_id", schema.formId)
//        payload.put("submitted_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
//        val all = org.json.JSONObject()
//        // merge drafts first then current values to ensure any unsaved fields included
//        draftPerTab.value?.forEach { (_, tabMap) ->
//            tabMap.forEach { (k, v) -> all.put(k, v.toString()) }
//        }
//        val cur = valuesLive.value ?: mapOf()
//        cur.forEach { (k, v) ->
//            when (v) {
//                is Uri -> all.put(k, v.toString())
//                is Collection<*> -> {
//                    val arr = org.json.JSONArray()
//                    v.forEach { arr.put(it.toString()) }
//                    all.put(k, arr)
//                }
//                else -> all.put(k, v ?: org.json.JSONObject.NULL)
//            }
//        }
//        payload.put("values", all)
//        return payload
//    }

    // ---------------------------------------------------------
    // BUILD PAYLOAD (WITH BASE64 PHOTOS)
    // Runs on IO thread safely.
    // ---------------------------------------------------------
    suspend fun buildPayloadAsync(): JSONObject = withContext(Dispatchers.IO) {

        val schema = schemaLive.value!!
        val payload = JSONObject()
        payload.put("form_id", schema.formId)
        payload.put("submitted_at", now())

        val valuesObj = JSONObject()
        val current = valuesLive.value ?: mapOf()

        current.forEach { (key, v) ->

            // Photo list
            if (v is Collection<*>) {
                val arr = JSONArray()

                val uriList = photoUris[key] ?: emptyList()

                val base64List = uriList.map { uri ->
                    async { fileToBase64(saveImageToInternal(uri)) }
                }.awaitAll()

                base64List.forEach { arr.put(it) }
                valuesObj.put(key, arr)
            }
            // Normal values
            else {
                valuesObj.put(key, v ?: JSONObject.NULL)
            }
        }

        payload.put("values", valuesObj)
        Log.d("SUBMIT DATA : ",payload.toString())
        return@withContext payload
    }

    fun submit( poiId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val payload = buildPayloadAsync()
            repository.deleteDraft(poiId)
            formState.postValue(FormState.SubmitResult(true, "Submitted"))
        }
    }

    fun fetchLocation(activity: DynamicFormActivity, fieldId: String) {

    }
    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())


    //  Add this for handling multiple gallery image selection
    fun addPhotoUris(fieldId: String, uris: List<Uri>) {
        val list = photoUris.getOrPut(fieldId) { mutableListOf() }
        list.addAll(uris)
        updateValue(fieldId, list.map { it.toString() })
        notifyFieldChanged(fieldId)
    }

    //  (Optional) Allow user to remove a photo from the list
    fun removePhotoUri(fieldId: String, uri: Uri) {
        photoUris[fieldId]?.remove(uri)
        updateValue(fieldId, photoUris[fieldId]?.map { it.toString() })
        notifyFieldChanged(fieldId)
    }

    private val _fieldChangeLive = MutableLiveData<String>()
    val fieldChangeLive get() = _fieldChangeLive

    fun notifyFieldChanged(fieldId: String) {
        _fieldChangeLive.postValue(fieldId)
    }

    // ---------------------------------------------------------
    // BASE64 CONVERSION (SUSPEND)
    // ---------------------------------------------------------
    private fun saveImageToInternal(uri: Uri): String {
        val app = getApplication<Application>()
        val bytes = app.contentResolver.openInputStream(uri)?.readBytes() ?: ByteArray(0)
        val file = File(app.filesDir, "draft_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private suspend fun fileToBase64(path: String): String =
        withContext(Dispatchers.IO) {
            val bytes = File(path).readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

    fun fetchAccurateLocation(latFieldId: String, lngFieldId: String) {
        _locationFetchState.postValue(false)

        val context = getApplication<Application>()

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .build()

        try {
            fusedClient.getCurrentLocation(request, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        updateValue(latFieldId, loc.latitude)
                        updateValue(lngFieldId, loc.longitude)
                        _locationFetchState.postValue(true)
                        notifyFieldChanged(latFieldId)
                        notifyFieldChanged(lngFieldId)
                    } else {
                        _locationFetchState.postValue(false)
                    }
                }
                .addOnFailureListener {
                    _locationFetchState.postValue(false)
                }
        } catch (e: SecurityException) {
            // permission not available
            _locationFetchState.postValue(false)
        }
    }

}
