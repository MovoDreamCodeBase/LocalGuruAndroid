package com.movodream.localguru.data_collection.presentation



import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.data.local.AppDatabase
import com.data.local.entity.DraftEntity
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.movodream.localguru.data_collection.model.FormSchema
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.repository.DraftRepository
import com.network.client.ResponseHandler
import com.network.model.ResponseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
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

    private lateinit var selectedPOIData : TaskItem
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

//    fun loadSchemaFromString(jsonStr: FormSchema?) {
//        viewModelScope.launch(Dispatchers.Default) {
////            val j = JSONObject(jsonStr)
////            val parsed = Parser.parseFormSchema(j)
//            schemaLive.postValue(jsonStr)
//            val map = valuesLive.value ?: mutableMapOf()
//            jsonStr!!.tabs.flatMap { it.fields }.forEach { if (!map.containsKey(it.id)) map[it.id] = "" }
//            valuesLive.postValue(map)
//        }
//    }
fun loadSchemaFromString(jsonStr: FormSchema?) {
//    viewModelScope.launch(Dispatchers.Default) {
//
//        if (jsonStr == null) return@launch
//
//        // Save schema
//        schemaLive.postValue(jsonStr)
//
//        // Start from existing values (e.g., after draft load)
//        val existing = valuesLive.value ?: mutableMapOf()
//        val merged = mutableMapOf<String, Any?>()
//        merged.putAll(existing)
//
//        // Ensure every field id has some initial value
//        jsonStr.tabs.flatMap { it.fields }.forEach { field ->
//            if (!merged.containsKey(field.id)) {
//                merged[field.id] = when (field.type) {
//                    "checkbox_group" -> emptyList<String>()
//                    "photo_list" -> emptyList<String>()
//                    else -> ""
//                }
//            }
//        }
//
//        valuesLive.postValue(merged)
//    }
    if (jsonStr == null) return

    // 1) set schema immediately
    schemaLive.value = jsonStr

    // 2) start from existing values (e.g., after draft load)
    val existing = valuesLive.value ?: mutableMapOf()
    val merged = mutableMapOf<String, Any?>()
    merged.putAll(existing)

    // 3) ensure every field id has some initial value
    jsonStr.tabs.flatMap { it.fields }.forEach { field ->
        if (!merged.containsKey(field.id)) {
            merged[field.id] = when (field.type) {
                "checkbox_group" -> mutableListOf<String>()
                else -> ""
            }
        }
    }

    valuesLive.value = merged
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

        // ----------------------------------------------------
        // Get existing notification list count
        // ----------------------------------------------------
        val notificationList =
            (valuesLive.value?.get("notifications") as? List<*>) ?: emptyList<Any>()

        // If ≥1 notification exists → skip validating input fields of notification section
        val skipNotificationInputs = notificationList.isNotEmpty()

        // Fields that should be skipped when notifications already exist
        val notificationInputIds = setOf(
            "notificationCategories",
            "userTargetGroup",
            "triggerType",
            "notificationPriorityLevel",
            "notificationLanguageAvailability",
            "notificationCategory",
        )

        tab.fields.forEach { f ->
            val v = map[f.id]

            // ----------------------------------------------------
            // SKIP NOTIFICATION INPUT VALIDATION IF LIST HAS 1+
            // ----------------------------------------------------
            if (skipNotificationInputs && f.id in notificationInputIds) {
                return@forEach   //  completely skip required, regex, min/max
            }

            // ----------------------------------------------------
            // REQUIRED VALIDATION
            // ----------------------------------------------------
            if (f.required) {
                val empty = when (v) {
                    null -> true
                    is String -> v.trim().isEmpty()
                    is Collection<*> -> v.isEmpty()
                    else -> false
                }
                if (empty) errors[f.id] = "${f.label} is required"
            }

            // ----------------------------------------------------
            // PHOTO LIST VALIDATION
            // ----------------------------------------------------
            if (f.type == "photo_list") {
                val col = v as? Collection<*>
                if (f.required && f.minItems != null && (col == null || col.size < f.minItems!!))
                    errors[f.id] = "Requires at least ${f.minItems} photos"
            }

            // ----------------------------------------------------
            // MIN LENGTH VALIDATION
            // ----------------------------------------------------
            f.minLength?.let {
                if (v is String && (f.required || v.isNotEmpty())) {
                    if (v.length < it) errors[f.id] = "${f.label} must be at least $it characters"
                }
            }

            // ----------------------------------------------------
// MAX LENGTH VALIDATION
// ----------------------------------------------------
            f.maxLength?.let { max ->
                if (v is String && v.isNotEmpty()) {
                    if (v.length > max) {
                        errors[f.id] = "${f.label} must not exceed $max characters"
                    }
                }
            }


            // ----------------------------------------------------
            // REGEX VALIDATION (Text & Textarea)
            // ----------------------------------------------------
            if ((f.type == "text" || f.type == "textarea") && !f.regex.isNullOrEmpty()) {
                if (v is String && v.isNotEmpty()) {
                    val regex = try { Regex(f.regex) } catch (_: Exception) { null }
                    val output = v.replace(Regex("\\s+"), " ").trim()
                    if (regex != null && !regex.matches(output)) {
                        if (f.errorMessage.isNullOrBlank()) {
                            errors[f.id] = "Invalid input"
                        } else {
                            errors[f.id] = f.errorMessage
                        }

                    }
                }
            }

            // ----------------------------------------------------
            // NUMBER VALIDATION
            // ----------------------------------------------------
            if (f.type == "number") {

                val raw = v

                if (raw == null || raw.toString().trim().isEmpty()) {
                    if (f.required) errors[f.id] = "${f.label} is required"
                    return@forEach
                }

                val num = raw.toString().toDoubleOrNull()
                if (num == null) {
                    errors[f.id] = "${f.label}: Invalid number"
                    return@forEach
                }

                f.min?.let { if (num < it) errors[f.id] = "${f.label} must be ≥ $it" }
                f.max?.let { if (num > it) errors[f.id] = "${f.label} must be ≤ $it" }
            }

            // ----------------------------------------------------
            // NOTIFICATION LIST VALIDATION (ONLY this is required)
            // ----------------------------------------------------
            if (f.type == "notification_list") {
                val list = notificationList

                if (f.required && list.isEmpty()) {
                    errors[f.id] = "Please add at least 1 notification"
                    return@forEach
                }

                f.minItems?.let { min ->
                    if (list.size < min) {
                        errors[f.id] = "Please add at least $min notifications"
                        return@forEach
                    }
                }

                f.maxItems?.let { max ->
                    if (list.size > max) {
                        errors[f.id] = "Maximum $max notifications allowed"
                        return@forEach
                    }
                }
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

                // Find field type from schema
                val fieldType = schema.tabs
                    .flatMap { it.fields }
                    .firstOrNull { it.id == fieldId }
                    ?.type

                // -----------------------------
                // NOTIFICATION LIST
                // -----------------------------
                if (fieldId == "notifications" && v is List<*>) {
                    val arr = JSONArray()
                    v.forEach { item ->
                        if (item is Map<*, *>) {
                            arr.put(JSONObject(item))
                        }
                    }
                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                // -----------------------------
                // PHOTO LIST → store URI strings
                // -----------------------------
                if (fieldType == "photo_list") {
                    val uriList = photoUris[fieldId] ?: emptyList()
                    val arr = JSONArray()
                    uriList.forEach { uri -> arr.put(uri.toString()) }
                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                // -----------------------------
                // CHECKBOX GROUP → store plain strings
                // -----------------------------
                if (fieldType == "checkbox_group" && v is List<*>) {
                    val arr = JSONArray()
                    v.forEach { arr.put(it.toString()) }
                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                // -----------------------------
                // NORMAL FIELD
                // -----------------------------
                valuesObj.put(fieldId, v ?: JSONObject.NULL)
            }

            val root = JSONObject().apply { put("values", valuesObj) }

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
            val schema = schemaLive.value  // may be null, handle safely




            valuesObj.keys().forEach { key ->
                val v = valuesObj.get(key)

                val fieldType = schema?.tabs
                    ?.flatMap { it.fields }
                    ?.firstOrNull { it.id == key }
                    ?.type

                     // -------------------------------------------------------
            // 1️⃣ FIX ROOT CAUSE: CLEAN any JSONObject → Map
            // -------------------------------------------------------
            if (v is JSONObject) {
                val cleanMap = mutableMapOf<String, Any?>()
                v.keys().forEach { k2 ->
                    cleanMap[k2] = v.get(k2)
                }
                restored[key] = cleanMap
                return@forEach
            }

                // -----------------------------
                // RESTORE NOTIFICATION LIST
                // -----------------------------
                if (key == "notifications" && v is JSONArray) {
                    val list = mutableListOf<Map<String, Any?>>()
                    for (i in 0 until v.length()) {
                        val obj = v.getJSONObject(i)
                        val map = mutableMapOf<String, Any?>()
                        obj.keys().forEach { k -> map[k] = obj.get(k) }
                        list.add(map)
                    }
                    restored[key] = list
                    return@forEach
                }

                // -----------------------------
                // RESTORE CHECKBOX GROUP
                // -----------------------------
                if (fieldType == "checkbox_group" && v is JSONArray) {
                    val list = mutableListOf<String>()
                    for (i in 0 until v.length()) {
                        list.add(v.getString(i))
                    }
                    restored[key] = list
                    return@forEach
                }

                // -----------------------------
                // RESTORE PHOTO LIST (URI strings)
                // -----------------------------
                if (fieldType == "photo_list" && v is JSONArray) {

                    val uriList = mutableListOf<Uri>()
                    val strList = mutableListOf<String>()

                    for (i in 0 until v.length()) {

                        val raw = v.getString(i)

                        // REMOVE file:// prefix
                        val cleanPath = raw.replace("file://", "")

                        val file = File(cleanPath)

                        if (file.exists()) {
                            val uri = Uri.fromFile(file)
                            uriList.add(uri)
                            strList.add(uri.toString())   // <-- use Uri string
                        }
                    }

                    photoUris[key] = uriList
                    restored[key] = strList
                    return@forEach
                }




                // -----------------------------
                // NORMAL VALUE
                // -----------------------------
                restored[key] = v
            }

            valuesLive.postValue(restored)
            withContext(Dispatchers.Main) { onLoaded() }
        }
    }




    // ---------------------------------------------------------
    // BUILD PAYLOAD (WITH BASE64 PHOTOS)
    // Runs on IO thread safely.
    // ---------------------------------------------------------
//    suspend fun buildPayloadAsync(): JSONObject = withContext(Dispatchers.IO) {
//
//        val schema = schemaLive.value!!
//        val valuesObj = JSONObject()
//        val current = valuesLive.value ?: mapOf()
//
//        // Skip raw notification input fields if we already have notifications list
//        val notificationList = current["notifications"] as? List<*>
//        val hasNotifications = notificationList?.isNotEmpty() == true
//
//        val skipNotificationFields = setOf(
//            "notificationCategories",
//            "userTargetGroup",
//            "triggerType",
//            "notificationPriorityLevel",
//            "notificationLanguageAvailability",
//        )
//
//        current.forEach { (key, v) ->
//
//            if (hasNotifications && key in skipNotificationFields)
//                return@forEach
//
//            val fieldType = schema.tabs
//                .flatMap { it.fields }
//                .firstOrNull { it.id == key }
//                ?.type
//
//            // -----------------------------
//            // NOTIFICATION LIST
//            // -----------------------------
//            if (key == "notifications" && v is List<*>) {
//                val arr = JSONArray()
//                v.forEach { item ->
//                    if (item is Map<*, *>) arr.put(JSONObject(item))
//                }
//                valuesObj.put(key, arr)
//                return@forEach
//            }
//
//            // -----------------------------
//            // CHECKBOX GROUP
//            // -----------------------------
//            if (fieldType == "checkbox_group" && v is List<*>) {
//                val arr = JSONArray()
//                v.forEach { arr.put(it.toString()) }
//                valuesObj.put(key, arr)
//                return@forEach
//            }
//
//            // -----------------------------
//            // PHOTO LIST → BASE64
//            // -----------------------------
//            if (fieldType == "photo_list") {
//                val arr = JSONArray()
//
//                // try to get URIs from in-memory cache
//                var uriList = photoUris[key]
//
//                // fallback to valuesLive if cache empty
//                if (uriList == null || uriList.isEmpty()) {
//                    val restoredStrings = (v as? List<*>)?.map { it.toString() } ?: emptyList()
//                    uriList = restoredStrings.map { Uri.parse(it) }.toMutableList()
//                }
//
//                val base64List = uriList.mapNotNull { uri ->
//                    try { uriToBase64(uri) } catch (e: Exception) { null }
//                }
//
//                base64List.forEach { arr.put(it) }
//
//                valuesObj.put(key, arr)
//                return@forEach
//            }
//
//
//            // -----------------------------
//            // NORMAL FIELD
//            // -----------------------------
//          //  valuesObj.put(key, v ?: JSONObject.NULL)
//
//            val cleaned = cleanJsonValue(v)
//            valuesObj.put(key, cleaned)
//
//        }
//        valuesObj.put("movodreamId", schema.movodreamId)
//        valuesObj.put("categoryId", selectedPOIData.categoryId)
//        valuesObj.put("poiName", selectedPOIData.poiName)
//        valuesObj.put("poiId", selectedPOIData.poiId)
//        valuesObj.put("agentId", selectedPOIData.agentId)
//        Log.d("SUBMIT DATA :", valuesObj.toString(10))
//        return@withContext valuesObj
//    }

    suspend fun buildPayloadAsync(): Map<String, Any> = withContext(Dispatchers.IO) {

        val schema = schemaLive.value!!
        val current = valuesLive.value ?: mapOf()

        val payloadMap = linkedMapOf<String, Any>()

        // Skip raw notification input fields if we already have notifications list
        val notificationList = current["notifications"] as? List<*>
        val hasNotifications = notificationList?.isNotEmpty() == true

        val skipNotificationFields = setOf(
            "notificationCategories",
            "userTargetGroup",
            "triggerType",
            "notificationPriorityLevel",
            "notificationLanguageAvailability",
        )

        current.forEach { (key, v) ->

            if (hasNotifications && key in skipNotificationFields)
                return@forEach

            val fieldType = schema.tabs
                .flatMap { it.fields }
                .firstOrNull { it.id == key }
                ?.type

            // -----------------------------------------
            // NOTIFICATION LIST
            // -----------------------------------------
            if (key == "notifications" && v is List<*>) {
                payloadMap[key] = v.map { it as Map<*, *> }
                return@forEach
            }

            // -----------------------------------------
            // CHECKBOX GROUP
            // -----------------------------------------
            if (fieldType == "checkbox_group" && v is List<*>) {
                payloadMap[key] = v.map { it.toString() }
                return@forEach
            }

            // -----------------------------------------
            // PHOTO LIST → BASE64 array
            // -----------------------------------------
            if (fieldType == "photo_list") {
                var uriList = photoUris[key]

                if (uriList.isNullOrEmpty()) {
                    val restoredStrings = (v as? List<*>)?.map { it.toString() } ?: emptyList()
                    uriList = restoredStrings.map { Uri.parse(it) }.toMutableList()
                }

                val base64List = uriList.mapNotNull { uri ->
                    try { uriToBase64(uri) } catch (_: Exception) { null }
                }

               // payloadMap[key] = base64List
                payloadMap[key] = ""
                return@forEach
            }
            // -----------------------------
// PHOTO LIST → RESIZE IF > 1MB → BASE64
// -----------------------------
//            if (fieldType == "photo_list") {
//
//                var uriList = photoUris[key]
//
//                // fallback to valuesLive if cache empty
//                if (uriList.isNullOrEmpty()) {
//                    uriList = (v as? List<*>)?.map { Uri.parse(it.toString()) }?.toMutableList()
//                }
//
//                val base64Arr = mutableListOf<String>()
//
//                uriList?.forEach { uri ->
//                    try {
//                        val base64 = resizeIfRequiredAndConvert(uri)
//                        base64Arr.add(base64)
//                    } catch (_: Exception) { }
//                }
//
//                payloadMap[key] = base64Arr
//                return@forEach
//            }

            // -----------------------------------------
            // NORMAL FIELD
            // -----------------------------------------
            payloadMap[key] = cleanJsonValue(v)
        }

        // -----------------------------------------
        // ADD STATIC META VALUES FROM SCHEMA
        // -----------------------------------------
        payloadMap["movodreamId"] = schema.movodreamId
        payloadMap["categoryId"] = selectedPOIData.categoryId
        payloadMap["poiName"] = selectedPOIData.poiName
        payloadMap["PoiId"] = ""+selectedPOIData.poiId
        payloadMap["AgentId"] = selectedPOIData.agentId
        //payloadMap["AgentId"] = "109918"

        Log.d("SUBMIT MAP", JSONObject(payloadMap as Map<*, *>).toString(10))

        return@withContext payloadMap
    }

    private fun cleanJsonValue(v: Any?): Any = when (v) {
        null -> ""                                // or JSONObject.NULL if needed
        is String, is Number, is Boolean -> v
        is List<*> -> v.map { cleanJsonValue(it) }        // List<Any>
        is Map<*, *> -> v.mapValues { (_, value) -> cleanJsonValue(value) } // Map<String, Any>
        is JSONObject ->
            v.keys().asSequence()
                .associateWith { k -> cleanJsonValue(v.get(k)) }     // Make clean Map<String, Any>

        else -> v.toString()
    }



//    private fun cleanJsonValue(value: Any?): Any? {
//
//        return when (value) {
//
//            null -> JSONObject.NULL
//
//            is JSONObject -> {
//                val obj = JSONObject()
//                value.keys().forEach { k ->
//                    obj.put(k, cleanJsonValue(value.get(k)))
//                }
//                obj
//            }
//
//            is Map<*, *> -> {
//                val obj = JSONObject()
//                value.forEach { (k, v) ->
//                    obj.put(k.toString(), cleanJsonValue(v))
//                }
//                obj
//            }
//
//            is List<*> -> {
//                val arr = JSONArray()
//                value.forEach { arr.put(cleanJsonValue(it)) }
//                arr
//            }
//
//            is Number, is Boolean, is String -> value
//
//            else -> value.toString()
//        }
//    }





    fun submit(poiId: String, selectedPOI: TaskItem?) {
        selectedPOIData = selectedPOI!!
        //viewModelScope.launch(Dispatchers.IO) {
           // val payload = buildPayloadAsync()
           // callSubmitPOIDataAPI()
        callSubmitPOIDataMultiPart()
            //repository.deleteDraft(poiId)

       // }
    }


    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())


    //  Add this for handling multiple gallery image selection
    fun addPhotoUris(fieldId: String, uris: List<Uri>) {
        val app = getApplication<Application>()
        val list = photoUris.getOrPut(fieldId) { mutableListOf() }
        val savedPaths = mutableListOf<String>()

        uris.forEach { uri ->
            try {
                val input = app.contentResolver.openInputStream(uri) ?: return@forEach
                val bytes = input.readBytes()
                input.close()

                val file = File(app.filesDir, "photo_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)

                val fileUri = Uri.fromFile(file)
                list.add(fileUri)
                savedPaths.add(file.absolutePath)

            } catch (_: Exception) {}
        }

        // SAVE ONLY INTERNAL FILE PATHS
        updateValue(fieldId, savedPaths)
        notifyFieldChanged(fieldId)
    }


    //  (Optional) Allow user to remove a photo from the list
    fun removePhotoUri(fieldId: String, uri: Uri) {
        val list = photoUris[fieldId] ?: mutableListOf()
        list.remove(uri)

        photoUris[fieldId] = list

        updateValue(fieldId, list.map { it.toString() })
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


    private fun uriToBase64(uri: Uri): String {
        val inputStream = getApplication<Application>()
            .contentResolver.openInputStream(uri) ?: return ""

        val bytes = inputStream.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
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
    // ---------------------------------------------------------
// ADD NOTIFICATION ENTRY (VM)
// ---------------------------------------------------------
    fun addNotification(): Map<String, String> {

        val values = valuesLive.value ?: mutableMapOf()
        val errors = validateNotificationInput()

        // return errors to UI if any
        if (errors.isNotEmpty()) return errors

        val category = values["notificationCategories"]?.toString().orEmpty()
        val target = values["userTargetGroup"]?.toString().orEmpty()
        val trigger = values["triggerType"]?.toString().orEmpty()
        val priority = values["notificationPriorityLevel"]?.toString().orEmpty()
        val languages = values["notificationLanguageAvailability"]?.toString()?.trim().orEmpty()

        val newItem = mapOf(
            "notificationCategories" to category,
            "userTargetGroup" to target,
            "triggerType" to trigger,
            "notificationPriorityLevel" to priority,
            "notificationLanguageAvailability" to languages
        )

        // Fetch current list
        val currentList =
            (values["notifications"] as? List<Map<String, Any?>>)?.toMutableList()
                ?: mutableListOf()

        currentList.add(newItem)

        // Update VM
        updateValue("notifications", currentList)
        notifyFieldChanged("notifications")

        // Clear input fields
        updateValue("notificationCategories", "")
        updateValue("userTargetGroup", "")
        updateValue("triggerType", "")
        updateValue("notificationPriorityLevel", "")
        updateValue("notificationLanguageAvailability", "")

        return emptyMap() // success
    }


    fun validateNotificationInput(): Map<String, String> {

        val v = valuesLive.value ?: return mapOf()
        val e = mutableMapOf<String, String>()

        // Required selects
        if ((v["notificationCategories"] as? String).isNullOrBlank())
            e["notificationCategories"] = "Please select a notification category"

        if ((v["userTargetGroup"] as? String).isNullOrBlank())
            e["userTargetGroup"] = "Please select user target group"

        if ((v["triggerType"] as? String).isNullOrBlank())
            e["triggerType"] = "Please select trigger type"

        if ((v["notificationPriorityLevel"] as? String).isNullOrBlank())
            e["notificationPriorityLevel"] = "Please select priority level"

        // Text field
        val lang = v["notificationLanguageAvailability"]?.toString()?.trim().orEmpty()

        if (lang.isEmpty())
            e["notificationLanguageAvailability"] = "Language list is required"
        else {
            val regex = Regex("^[A-Za-z,\\s]{0,200}$")
            if (!regex.matches(lang)) {
                e["notificationLanguageAvailability"] = "Invalid language list"
            }
        }

        return e
    }


    fun removeNotification(index: Int) {
        val values = valuesLive.value ?: return

        val currentList =
            (values["notifications"] as? List<Map<String, Any?>>)?.toMutableList()
                ?: mutableListOf()

        if (index !in currentList.indices) return

        // Remove item
        currentList.removeAt(index)

        // Update VM state
        updateValue("notifications", currentList)

        // Trigger UI refresh
        notifyFieldChanged("notifications")
    }

    var submitPOIResponse =
        MutableLiveData<ResponseHandler<ResponseData<Int>?>>()
    fun callSubmitPOIDataAPI() {
        viewModelScope.launch {
            try {
                submitPOIResponse.value = ResponseHandler.Loading

                // ⚡ Build payload (safe, suspending)
                val payload: Map<String, Any> = buildPayloadAsync()

                // ⚡ Call API
                val response = repository.submitPOIData(payload)

                submitPOIResponse.value = response

            } catch (e: Exception) {
                submitPOIResponse.value = ResponseHandler.OnFailed(500,
                     message = e.message ?: "Something went wrong","500"
                )
            }
        }
    }


    fun deleteDraftAfterSubmit(poiId: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDraft(poiId)   // delete from DB

            withContext(Dispatchers.Main) { // return to Main thread
                onDone()                    // callback to Activity
            }
        }
    }


    private fun resizeIfRequiredAndConvert(uri: Uri): String {
        val context = getApplication<Application>()
        val oneMB = 1 * 1024 * 1024

        // Step 1 — decode bounds only (no memory allocation)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        // Step 2 — calculate optimal sample size
        options.inSampleSize = calculateInSampleSize(options, 1024, 1024) // max 1024px image
        options.inJustDecodeBounds = false

        // Step 3 — decode scaled bitmap
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return ""

        // Step 4 — compress until below 1MB
        var quality = 90
        var compressed: ByteArray

        do {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            compressed = baos.toByteArray()
            quality -= 7
        } while (compressed.size > oneMB && quality > 30)

        return Base64.encodeToString(compressed, Base64.NO_WRAP)
    }


    suspend fun buildMultipartPayload(): Pair<RequestBody, List<MultipartBody.Part>> =
        withContext(Dispatchers.IO) {

            val schema = schemaLive.value!!
            val current = valuesLive.value ?: emptyMap<String, Any?>()

            val payload = linkedMapOf<String, Any?>()
            val galleryPhotos = mutableListOf<MultipartBody.Part>() // <-- SINGLE KEY

            val notificationList = current["notifications"] as? List<*>
            val hasNotifications = notificationList?.isNotEmpty() == true

            val skipNotificationFields = setOf(
                "notificationCategories",
                "userTargetGroup",
                "triggerType",
                "notificationPriorityLevel",
                "notificationLanguageAvailability"
            )

            current.forEach { (key, v) ->

                val fieldType = schema.tabs.flatMap { it.fields }
                    .firstOrNull { it.id == key }?.type

                if (hasNotifications && key in skipNotificationFields) return@forEach

                when {
                    key == "notifications" && v is List<*> -> {
                        payload[key] = v
                    }

                    fieldType == "checkbox_group" && v is List<*> -> {
                        payload[key] = v.map { it.toString() }
                    }

                    fieldType == "photo_list" -> {
                        var uriList = photoUris[key]
                        if (uriList.isNullOrEmpty()) {
                            uriList = (v as? List<*>)?.map { Uri.parse(it.toString()) }?.toMutableList()
                        }

                        uriList?.forEachIndexed { index, uri ->
                            // ⚡ All images go under SAME key: GalleryPhotos
                            val part = createMultipart("GalleryPhotos", uri)
                            galleryPhotos.add(part)
                        }
                    }

                    else -> payload[key] = cleanJsonValue(v)
                }
            }

            // STATIC META VALUES
            payload["movodreamId"] = schema.movodreamId
            payload["categoryId"] = selectedPOIData.categoryId
            payload["poiName"] = selectedPOIData.poiName
            payload["PoiId"] = selectedPOIData.poiId.toString()
            payload["AgentId"] = selectedPOIData.agentId

            val requestBody =
                JSONObject(payload as Map<*, *>)
                    .toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

            return@withContext Pair(requestBody, galleryPhotos)
        }

    fun callSubmitPOIDataMultiPart() {
        viewModelScope.launch {
            submitPOIResponse.value = ResponseHandler.Loading
            try {
                val (jsonPart, fileParts) = buildMultipartPayload()
                val response = repository.submitPOIData(jsonPart, fileParts)
                submitPOIResponse.value = response
            } catch (e: Exception) {
                submitPOIResponse.value = ResponseHandler.OnFailed(
                    500, e.message ?: "Something went wrong", "500"
                )
            }
        }
    }

    private fun createMultipart(key: String, uri: Uri): MultipartBody.Part {
        val context = getApplication<Application>()
        val bytes = prepareImageForUpload(context, uri) // resized ≤ 2MB

        val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"

        return MultipartBody.Part.createFormData(key, fileName, body)
    }

    /**
     * Compress image until size ≤ 2 MB
     */
    private fun prepareImageForUpload(context: Context, uri: Uri): ByteArray {

        val TWO_MB = 2 * 1024 * 1024

        // Step 1 — load bitmap scaled
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        options.inSampleSize = calculateInSampleSize(options, 1920, 1920) // HD safe resolution
        options.inJustDecodeBounds = false

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return ByteArray(0)

        // Step 2 — compress iteratively until < 2MB
        var quality = 95
        var byteArray: ByteArray

        do {
            val baos = ByteArrayOutputStream()
            baos.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
                byteArray = it.toByteArray()
            }
            quality -= 5
        } while (byteArray.size > TWO_MB && quality > 25)

        return byteArray
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }


}
