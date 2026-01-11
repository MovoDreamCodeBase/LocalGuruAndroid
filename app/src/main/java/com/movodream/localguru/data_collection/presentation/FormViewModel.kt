package com.movodream.localguru.data_collection.presentation


import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Parcelable
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.core.utils.DebugLog
import com.core.utils.Utils
import com.data.local.AppDatabase
import com.data.local.entity.DraftEntity
import com.data.remote.model.DeletePhotoRequest
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.movodream.localguru.data_collection.model.FieldSchema
import com.movodream.localguru.data_collection.model.FormSchema
import com.movodream.localguru.data_collection.model.Option
import com.movodream.localguru.data_collection.model.PhotoWithMeta
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.repository.DraftRepository
import com.network.client.ResponseHandler
import com.network.model.BulkSubPoiItem
import com.network.model.ResponseData
import com.network.model.ResponseListData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.collections.forEachIndexed
import kotlin.collections.isNullOrEmpty

class FormViewModel(app: Application) : AndroidViewModel(app) {


    // -------------------------------------------------------------
    // Room Database + Repository initialization INSIDE ViewModel
    // -------------------------------------------------------------
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(getApplication())
    }

    private lateinit var selectedPOIData: TaskItem
    private lateinit var assignPOIList: List<TaskItem>
    private val repository by lazy {
        DraftRepository(database.draftDao())
    }
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }


    var _isRevisionState = MutableLiveData<Boolean>()
    var _isAddPOIState = MutableLiveData<Boolean>()



    var deleteGalleryPhoto =
        MutableLiveData<ResponseHandler<ResponseData<Int>?>>()
    val photoUris = mutableMapOf<String, MutableList<Uri>>()  // stores URIs per field

    //  existing photos coming from server as URLs
    val photoUrls = mutableMapOf<String, MutableList<String>>()
    val schemaLive = MutableLiveData<FormSchema?>()

    // current values for all fields
    val valuesLive = MutableLiveData<MutableMap<String, Any?>>().apply { value = mutableMapOf() }

    // in-memory draft store per tab (optimized json-like map)
    val draftPerTab =
        MutableLiveData<MutableMap<String, Map<String, Any?>>>().apply { value = mutableMapOf() }
    val categoryOptionsLive = MutableLiveData<List<Option>>()

    var currentSubPoiIndex: Int = 0

    private val _progressPercent = MutableLiveData<Int>()
    val progressPercent: LiveData<Int> = _progressPercent


    private fun extractCategoryOptions(schema: FormSchema) {
        val categoryField = schema.tabs
            .flatMap { it.fields }
            .firstOrNull { it.id == "category" && it.type == "select" }

        categoryOptionsLive.postValue(categoryField?.options ?: emptyList())
    }

    fun loadSchemaFromString(jsonStr: FormSchema?) {
        if (jsonStr == null) return

        // 1) set schema immediately
        schemaLive.value = jsonStr
// 🔥 ONLY CATEGORY OPTIONS
        extractCategoryOptions(jsonStr)
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

        calculateProgress()
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

                // 🔥 Source of truth for photos
                val uriCount = photoUris[f.id]?.size ?: 0

                if (f.required && f.minItems != null && uriCount < f.minItems!!) {
                    errors[f.id] = "Requires at least ${f.minItems} photos"
                }
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
                    val regex = try {
                        Regex(f.regex)
                    } catch (_: Exception) {
                        null
                    }
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
// SUB POI LIST (SAVE DRAFT) — FIXED
// -----------------------------
                if (fieldType == "sub_poi_list" && v is List<*>) {

                    val subPoiArray = JSONArray()

                    v.forEach { item ->
                        if (item is Map<*, *>) {

                            val subPoiObj = JSONObject()

                            item.forEach { (k, value) ->

                                when (k) {

                                    // ------------------------------------------------
                                    // 1️⃣ SUB POI PHOTO URIs
                                    // ------------------------------------------------
                                    "subPoiGalleryPhotos" -> {
                                        val photosArr = JSONArray()
                                        (value as? List<*>)?.forEach {
                                            photosArr.put(it.toString())
                                        }
                                        subPoiObj.put("subPoiGalleryPhotos", photosArr)
                                    }

                                    // ------------------------------------------------
                                    // 🔥 2️⃣ SUB POI PHOTO METADATA — FIX
                                    // ------------------------------------------------
                                    "subPoiGalleryPhotos__meta" -> {

                                        val metaArr = JSONArray()

                                        when (value) {

                                            // Case A: Fresh objects (runtime)
                                            is List<*> -> {
                                                value.forEach { meta ->
                                                    when (meta) {
                                                        is PhotoWithMeta -> {
                                                            metaArr.put(
                                                                JSONObject()
                                                                    .put("uri", meta.uri)
                                                                    .put("label", meta.label)
                                                                    .put("description", meta.description)
                                                                    .put("latitude", meta.latitude)
                                                                    .put("longitude", meta.longitude)
                                                            )
                                                        }

                                                        // Case B: Reloaded from draft (Map form)
                                                        is Map<*, *> -> {
                                                            metaArr.put(
                                                                JSONObject()
                                                                    .put("uri", meta["uri"])
                                                                    .put("label", meta["label"])
                                                                    .put("description", meta["description"])
                                                                    .put("latitude", meta["latitude"])
                                                                    .put("longitude", meta["longitude"])
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // ✅ ALWAYS JSONArray — NEVER STRING
                                        subPoiObj.put("subPoiGalleryPhotos__meta", metaArr)
                                    }

                                    // ------------------------------------------------
                                    // 3️⃣ NORMAL FIELDS
                                    // ------------------------------------------------
                                    else -> {
                                        subPoiObj.put(k.toString(), value ?: JSONObject.NULL)
                                    }
                                }
                            }

                            subPoiArray.put(subPoiObj)
                        }
                    }

                    valuesObj.put(fieldId, subPoiArray)
                    return@forEach
                }






                if (fieldType == "photo_list") {

                    val metaList = photoMetaMap[fieldId] ?: emptyList()

                    // -----------------------------
                    // 1️⃣ SAVE META (SOURCE OF TRUTH)
                    // -----------------------------
                    val metaArr = JSONArray()
                    metaList.forEach { p ->
                        metaArr.put(
                            JSONObject()
                                .put("uri", p.uri)
                                .put("label", p.label)
                                .put("description", p.description)
                                .put("latitude", p.latitude)
                                .put("longitude", p.longitude)
                        )
                    }
                    valuesObj.put("${fieldId}__meta", metaArr)

                    // -----------------------------
                    // 2️⃣ DERIVE photo_list FROM META ONLY
                    // -----------------------------
                    val arr = JSONArray()
                    metaList.forEach { p ->
                        arr.put(p.uri) // 🔥 SAME COUNT, SAME ORDER
                    }
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
// OPERATIONAL HOURS
// -----------------------------
                if (fieldType == "operational_hours" && v is List<*>) {

                    val arr = JSONArray()

                    v.filterIsInstance<FormViewModel.OperationalDay>().forEach { day ->

                        val dayObj = JSONObject()
                        dayObj.put("day", day.day.name)
                        dayObj.put("isClosed", day.isClosed)
                        dayObj.put("isOpen24Hours", day.isOpen24Hours)

                        val slotsArr = JSONArray()
                        day.slots.forEach { slot ->
                            val slotObj = JSONObject()
                            slotObj.put("open", slot.open)
                            slotObj.put("close", slot.close)
                            slotsArr.put(slotObj)
                        }

                        dayObj.put("slots", slotsArr)
                        arr.put(dayObj)
                    }

                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                // -----------------------------
// HOLIDAY EXCEPTIONS
// -----------------------------
                if (fieldType == "holiday_list" && v is List<*>) {

                    val arr = JSONArray()

                    v.forEach { item ->
                        if (item is HolidayException) {
                            val obj = JSONObject()
                            obj.put("name", item.name)
                            obj.put("date", item.date)
                            obj.put("isClosed", item.isClosed)
                            obj.put("isOpen24Hours", item.isOpen24Hours)

                            val slotsArr = JSONArray()
                            item.slots.forEach { slot ->
                                val s = JSONObject()
                                s.put("open", slot.open)
                                s.put("close", slot.close)
                                slotsArr.put(s)
                            }
                            obj.put("slots", slotsArr)

                            arr.put(obj)
                        }
                    }

                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                if (fieldType == "seasonal_list" && v is List<*>) {

                    val arr = JSONArray()

                    v.forEach {
                        val s = it as SeasonalHours
                        val obj = JSONObject()

                        obj.put("name", s.name)
                        obj.put("startDate", s.startDate)
                        obj.put("endDate", s.endDate)
                        obj.put("isClosed", s.isClosed)
                        obj.put("isOpen24Hours", s.isOpen24Hours)

                        val slotsArr = JSONArray()
                        s.slots.forEach { slot ->
                            val o = JSONObject()
                            o.put("open", slot.open)
                            o.put("close", slot.close)
                            slotsArr.put(o)
                        }
                        obj.put("slots", slotsArr)

                        arr.put(obj)
                    }

                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                if (fieldType == "custom_metadata_list" && v is List<*>) {
                    val arr = JSONArray()
                    v.forEach {
                        if (it is FormViewModel.CustomMetadata) {
                            arr.put(
                                JSONObject()
                                    .put("label", it.label)
                                    .put("type", it.type.name)
                                    .put("value", it.value)
                            )
                        }
                    }
                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                // -----------------------------
// TEXT LIST (Dos / Donts / Guidelines / Etiquettes)
// -----------------------------
                if (fieldType == "text_list" && v is List<*>) {

                    val arr = JSONArray()
                    v.forEach { item ->
                        if (!item.toString().isBlank()) {
                            arr.put(item.toString())
                        }
                    }

                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                if (fieldType == "event_list" && v is List<*>) {

                    val arr = JSONArray()

                    v.forEach {
                        val e = it as FormViewModel.Event
                        val obj = JSONObject()

                        obj.put("name", e.name)
                        obj.put("description", e.eventDescription)
                        obj.put("date", e.date)

                        val slotsArr = JSONArray()
                        e.slots.forEach { slot ->
                            slotsArr.put(
                                JSONObject()
                                    .put("open", slot.open)
                                    .put("close", slot.close)
                            )
                        }

                        obj.put("slots", slotsArr)
                        arr.put(obj)
                    }

                    valuesObj.put(fieldId, arr)
                    return@forEach
                }
                if (fieldType == "facility_list" && v is List<*>) {

                    val arr = JSONArray()

                    v.forEach {
                        val f = it as FormViewModel.FacilityPoint
                        arr.put(
                            JSONObject()
                                .put("label", f.label)
                                .put("description", f.description)
                                .put("latitude", f.latitude)
                                .put("longitude", f.longitude)
                                .put("landmark", f.landmark)
                        )
                    }

                    valuesObj.put(fieldId, arr)
                    return@forEach
                }

                if (fieldType == "address_list" && v is List<*>) {

                    val arr = JSONArray()

                    v.forEach {
                        val f = it as FormViewModel.SecondaryAddress
                        arr.put(
                            JSONObject()
                                .put("address", f.address)
                                .put("latitude", f.latitude)
                                .put("longitude", f.longitude)
                                .put("landmark", f.landmark)
                        )
                    }

                    valuesObj.put(fieldId, arr)
                    return@forEach
                }
                if (fieldType == "source_verification" && v is List<*>) {

                val arr = JSONArray()

                v.filterIsInstance<FormViewModel.SourceVerification>()
                    .forEach { s ->
                        if (s.source.isNotBlank()) {
                            arr.put(
                                JSONObject()
                                    .put("source", s.source)
                                    .put("comment", s.comment)
                            )
                        }
                    }

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
                formId = ""+_progressPercent.value,
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
// RESTORE SUB POI LIST
// -----------------------------
                if (fieldType == "sub_poi_list" && v is JSONArray) {

                    val subPoiList = mutableListOf<Map<String, Any?>>()

                    for (i in 0 until v.length()) {
                        val obj = v.getJSONObject(i)
                        val subPoiMap = mutableMapOf<String, Any?>()

                        obj.keys().forEach { subKey ->
                            val subVal = obj.get(subKey)

                            when (subKey) {

                                "subPoiGalleryPhotos" -> {
                                    val photos = mutableListOf<String>()
                                    if (subVal is JSONArray) {
                                        for (j in 0 until subVal.length()) {
                                            photos.add(subVal.getString(j))
                                        }
                                    }
                                    subPoiMap["subPoiGalleryPhotos"] = photos
                                }

                                "subPoiGalleryPhotos__meta" -> {
                                    val metaList = mutableListOf<PhotoWithMeta>()
                                    if (subVal is JSONArray) {
                                        for (j in 0 until subVal.length()) {
                                            val o = subVal.getJSONObject(j)
                                            metaList.add(
                                                PhotoWithMeta(
                                                    uri = o.getString("uri"),
                                                    label = o.optString("label"),
                                                    description = o.optString("description"),
                                                    latitude = o.optDouble("latitude"),
                                                    longitude = o.optDouble("longitude")
                                                )
                                            )
                                        }
                                    }
                                    subPoiMap["subPoiGalleryPhotos__meta"] = metaList
                                }

                                else -> subPoiMap[subKey] = subVal
                            }
                        }

                        subPoiList.add(subPoiMap)
                    }

                    restored[key] = subPoiList
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
// RESTORE PHOTO METADATA
// -----------------------------
                if (key.endsWith("__meta") && v is JSONArray) {

                    val fieldId = key.removeSuffix("__meta")

                    val metaList = mutableListOf<PhotoWithMeta>()
                    val uriStrings = mutableListOf<String>()
                    val localUris = mutableListOf<Uri>()

                    for (i in 0 until v.length()) {
                        val o = v.getJSONObject(i)
                        val uriStr = o.getString("uri")

                        metaList.add(
                            PhotoWithMeta(
                                uri = uriStr,
                                label = o.optString("label"),
                                description = o.optString("description"),
                                latitude = o.optDouble("latitude"),
                                longitude = o.optDouble("longitude")
                            )
                        )

                        uriStrings.add(uriStr)

                        if (uriStr.startsWith("file://")) {
                            val file = File(uriStr.removePrefix("file://"))
                            if (file.exists()) {
                                localUris.add(Uri.fromFile(file))
                            }
                        }
                    }

                    // 1️⃣ Metadata
                    photoMetaMap[fieldId] = metaList

                    // 2️⃣ Upload URIs
                    photoUris[fieldId] = localUris

                    // 3️⃣ UI value (adapter reads THIS)
                    restored[fieldId] = uriStrings

                    return@forEach
                }




// -----------------------------
// RESTORE OPERATIONAL HOURS
// -----------------------------
                if (fieldType == "operational_hours" && v is JSONArray) {

                    val list = mutableListOf<FormViewModel.OperationalDay>()

                    for (i in 0 until v.length()) {
                        val obj = v.getJSONObject(i)

                        val day = FormViewModel.WeekDay.valueOf(obj.getString("day"))
                        val isClosed = obj.optBoolean("isClosed", true)
                        val isOpen24 = obj.optBoolean("isOpen24Hours", false)

                        val slots = mutableListOf<FormViewModel.TimeSlot>()
                        val slotsArr = obj.optJSONArray("slots") ?: JSONArray()

                        for (j in 0 until slotsArr.length()) {
                            val s = slotsArr.getJSONObject(j)
                            slots.add(
                                FormViewModel.TimeSlot(
                                    open = s.getInt("open"),
                                    close = s.getInt("close")
                                )
                            )
                        }

                        list.add(
                            FormViewModel.OperationalDay(
                                day = day,
                                isClosed = isClosed,
                                isOpen24Hours = isOpen24,
                                slots = slots
                            )
                        )
                    }

                    restored[key] = list
                    return@forEach
                }
// -----------------------------
// LOAD HOLIDAY EXCEPTIONS
// -----------------------------
                if (fieldType == "holiday_list" && v is JSONArray) {

                    val list = mutableListOf<HolidayException>()

                    for (i in 0 until v.length()) {
                        val obj = v.getJSONObject(i)

                        val holiday = HolidayException(
                            name = obj.getString("name"),
                            date = obj.getString("date"),
                            isClosed = obj.optBoolean("isClosed"),
                            isOpen24Hours = obj.optBoolean("isOpen24Hours")
                        )

                        val slotsArr = obj.optJSONArray("slots") ?: JSONArray()
                        for (j in 0 until slotsArr.length()) {
                            val s = slotsArr.getJSONObject(j)
                            holiday.slots.add(
                                TimeSlot(
                                    open = s.getInt("open"),
                                    close = s.getInt("close")
                                )
                            )
                        }

                        list.add(holiday)
                    }

                    restored[key] = list
                    return@forEach
                }
                if (fieldType == "seasonal_list" && v is JSONArray) {

                    val list = mutableListOf<SeasonalHours>()

                    for (i in 0 until v.length()) {
                        val obj = v.getJSONObject(i)

                        val s = SeasonalHours(
                            name = obj.getString("name"),
                            startDate = obj.getString("startDate"),
                            endDate = obj.getString("endDate"),
                            isClosed = obj.optBoolean("isClosed"),
                            isOpen24Hours = obj.optBoolean("isOpen24Hours")
                        )

                        val slotsArr = obj.optJSONArray("slots") ?: JSONArray()
                        for (j in 0 until slotsArr.length()) {
                            val slot = slotsArr.getJSONObject(j)
                            s.slots.add(
                                TimeSlot(
                                    open = slot.getInt("open"),
                                    close = slot.getInt("close")
                                )
                            )
                        }

                        list.add(s)
                    }

                    restored[key] = list
                    return@forEach
                }

                if (fieldType == "custom_metadata_list" && v is JSONArray) {
                    val list = mutableListOf<FormViewModel.CustomMetadata>()
                    for (i in 0 until v.length()) {
                        val o = v.getJSONObject(i)
                        list.add(
                            FormViewModel.CustomMetadata(
                                label = o.optString("label"),
                                type = FormViewModel.MetadataType.valueOf(o.optString("type")),
                                value = o.optString("value")
                            )
                        )
                    }
                    restored[key] = list
                    return@forEach
                }
// -----------------------------
// RESTORE TEXT LIST
// -----------------------------
                if (fieldType == "text_list" && v is JSONArray) {

                    val list = mutableListOf<String>()
                    for (i in 0 until v.length()) {
                        val value = v.optString(i)
                        if (value.isNotBlank()) {
                            list.add(value)
                        }
                    }

                    restored[key] = list
                    return@forEach
                }
                if (fieldType == "event_list" && v is JSONArray) {

                val list = mutableListOf<FormViewModel.Event>()

                for (i in 0 until v.length()) {
                    val obj = v.getJSONObject(i)

                    val e = FormViewModel.Event(
                        name = obj.getString("name"),
                        eventDescription = obj.getString("description"),
                        date = obj.getString("date")
                    )

                    val slotsArr = obj.optJSONArray("slots") ?: JSONArray()
                    for (j in 0 until slotsArr.length()) {
                        val s = slotsArr.getJSONObject(j)
                        e.slots.add(
                            FormViewModel.TimeSlot(
                                open = s.getInt("open"),
                                close = s.getInt("close")
                            )
                        )
                    }

                    list.add(e)
                }

                restored[key] = list
                return@forEach
            }
                if (fieldType == "facility_list" && v is JSONArray) {

                    val list = mutableListOf<FormViewModel.FacilityPoint>()

                    for (i in 0 until v.length()) {
                        val o = v.getJSONObject(i)
                        list.add(
                            FormViewModel.FacilityPoint(
                                label = o.getString("label"),
                                description = o.getString("description"),
                                latitude = o.getString("latitude"),
                                longitude = o.getString("longitude"),
                                landmark = o.optString("landmark")
                            )
                        )
                    }

                    restored[key] = list
                    return@forEach
                }
                if (fieldType == "address_list" && v is JSONArray) {

                    val list = mutableListOf<FormViewModel.SecondaryAddress>()

                    for (i in 0 until v.length()) {
                        val o = v.getJSONObject(i)
                        list.add(
                            FormViewModel.SecondaryAddress(
                                address = o.getString("address"),
                                latitude = o.getString("latitude"),
                                longitude = o.getString("longitude"),
                                landmark = o.optString("landmark")
                            )
                        )
                    }

                    restored[key] = list
                    return@forEach
                }
                if (fieldType == "source_verification" && v is JSONArray) {

                    val list = mutableListOf<FormViewModel.SourceVerification>()

                    for (i in 0 until v.length()) {
                        val obj = v.getJSONObject(i)
                        list.add(
                            FormViewModel.SourceVerification(
                                source = obj.getString("source"),
                                comment = obj.optString("comment")
                            )
                        )
                    }

                    restored[key] = list
                    return@forEach
                }



                // -----------------------------
// NORMAL VALUE (skip photo_list)
// -----------------------------
                if (fieldType != "photo_list") {
                    restored[key] = v
                }

            }




            valuesLive.postValue(restored)
            calculateProgress()

            withContext(Dispatchers.Main) { onLoaded() }
        }
    }

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
                    try {
                        uriToBase64(uri)
                    } catch (_: Exception) {
                        null
                    }
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
        payloadMap["PoiId"] = "" + selectedPOIData.poiId
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



    fun submit(poiId: String, selectedPOI: TaskItem?) {
        selectedPOIData = selectedPOI!!
        callSubmitPOIDataMultiPart()
    }

    //  Add this for handling multiple gallery image selection
    fun addPhotoUris(fieldId: String, uris: List<Uri>) {
        val app = getApplication<Application>()

        // local new photos
        val localList = photoUris.getOrPut(fieldId) { mutableListOf() }

        uris.forEach { uri ->
            try {
                val input = app.contentResolver.openInputStream(uri) ?: return@forEach
                val bytes = input.readBytes()
                input.close()

                val file = File(app.filesDir, "photo_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)

                val fileUri = Uri.fromFile(file)
                localList.add(fileUri)
            } catch (_: Exception) {
            }
        }

        // build merged list for UI: [server URLs] + [local file paths]
        val merged = mutableListOf<String>()
        photoUrls[fieldId]?.let { merged.addAll(it) }                      // URLs
        merged.addAll(localList.map { it.toString() })                     // local files

        updateValue(fieldId, merged)
        notifyFieldChanged(fieldId)
    }


    //   Allow user to remove a photo from the list
    fun removePhotoUri(fieldId: String, uri: Uri) {
        val uriStr = uri.toString()
        val isRemote = uriStr.startsWith("http", ignoreCase = true)

        if (isRemote) {
            // remove from server URL bucket
            val urls = photoUrls[fieldId] ?: mutableListOf()
            urls.remove(uriStr)
            photoUrls[fieldId] = urls
        } else {
            // remove from local file bucket
            val locals = photoUris[fieldId] ?: mutableListOf()
            locals.remove(uri)
            photoUris[fieldId] = locals
        }

        // rebuild merged list for UI
        val merged = mutableListOf<String>()
        photoUrls[fieldId]?.let { merged.addAll(it) }
        photoUris[fieldId]?.let { merged.addAll(it.map { u -> u.toString() }) }

        updateValue(fieldId, merged)
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

    var createPOIResponse =
        MutableLiveData<ResponseHandler<ResponseData<Int>?>>()
    var addPOISubPOIResponse =
        MutableLiveData<ResponseHandler<List<BulkSubPoiItem>?>>()

    var bulkSubPOIResponse =
        MutableLiveData<ResponseHandler<List<BulkSubPoiItem>?>>()
    fun deleteDraftAfterSubmit(poiId: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDraft(poiId)   // delete from DB

            withContext(Dispatchers.Main) { // return to Main thread
                onDone()                    // callback to Activity
            }
        }
    }




    suspend fun buildMultipartPayload(): Pair<RequestBody, List<MultipartBody.Part>> =
        withContext(Dispatchers.IO) {

            val schema = schemaLive.value!!
            val current = valuesLive.value ?: emptyMap<String, Any?>()

            val payload = linkedMapOf<String, Any?>()
            val galleryPhotos = mutableListOf<MultipartBody.Part>() // <-- SINGLE KEY

            val combinedDescArr = JSONArray()

            val uid = randomDigits(9) // 🔥 ONE uid for entire request
            var globalPhotoIndex = 0  // 🔥 CONTINUOUS index

            val notificationList = current["notifications"] as? List<*>
            val hasNotifications = notificationList?.isNotEmpty() == true

            val skipNotificationFields = setOf(
                "notificationCategories",
                "userTargetGroup",
                "triggerType",
                "notificationPriorityLevel",
                "notificationLanguageAvailability"
            )

            val skipSubPoiKeys = setOf(
                "subPoiName",
                "subPoiLatitude",
                "subPoiLongitude",
                "subPoiPhysicalAddress",
                "subPoiGalleryPhotos",
                "subPoiLocalGuruRemarks",
                "addSubPois"
            )
            val skipOperationalHourInternalKeys = setOf(
                "holidayExceptions",
                "seasonalHours"
            )



            current.forEach { (key, v) ->

                val fieldType = schema.tabs.flatMap { it.fields }
                    .firstOrNull { it.id == key }?.type

                if (hasNotifications && key in skipNotificationFields) return@forEach
                if (key in skipSubPoiKeys) return@forEach

                if (key in skipOperationalHourInternalKeys) return@forEach

                //  SKIP ALL "__other" KEYS
                if (key.endsWith("__other")) return@forEach

                //  HANDLE SELECT WITH "OTHER"
                if (fieldType == "select" && v?.toString() == "Other") {
                    val otherValue = current["${key}__other"]?.toString()?.trim()
                    payload[key] = otherValue ?: ""
                    return@forEach
                }

                // ✅ CUSTOM METADATA (ADD HERE)
                if (key == "customMetadata" && v is List<*>) {
                    val metaArray = JSONArray()

                    v.forEach { item ->
                        if (item is FormViewModel.CustomMetadata &&
                            item.label.isNotBlank() &&
                            item.value.isNotBlank()
                        ) {
                            metaArray.put(
                                JSONObject()
                                    .put("label", item.label.trim())
                                    .put("type", item.type.name)
                                    .put("value", item.value.trim())
                            )
                        }
                    }

                    if (metaArray.length() > 0) {
                        payload[key] = metaArray
                    }
                    return@forEach
                }
                if (fieldType == "text_list" && v is List<*>) {
                    val list = v
                        .mapNotNull { it?.toString()?.trim() }
                        .filter { it.isNotEmpty() }

                    if (list.isNotEmpty()) {
                        payload[key] = list
                    }
                    return@forEach
                }
// ======================================================
// FACILITY LIST (Parking / Washroom / Drinking Water)
// ======================================================
                if (fieldType == "facility_list" && v is List<*>) {

                    val arr = JSONArray()

                    v.forEach { item ->
                        if (item is FormViewModel.FacilityPoint) {

                            arr.put(
                                JSONObject()
                                    .put("label", item.label.trim())
                                    .put("description", item.description.trim())
                                    .put("latitude", item.latitude)
                                    .put("longitude", item.longitude)
                                    .put("landmark", item.landmark.trim())
                            )
                        }
                    }

                    // ✅ Only add if not empty
                    if (arr.length() > 0) {
                        payload[key] = arr
                    }

                    return@forEach
                }

                if (fieldType == "address_list" && v is List<*>) {

                    val arr = JSONArray()

                    v.forEach { item ->
                        if (item is FormViewModel.SecondaryAddress) {

                            arr.put(
                                JSONObject()
                                    .put("address", item.address.trim())
                                    .put("latitude", item.latitude)
                                    .put("longitude", item.longitude)
                                    .put("landmark", item.landmark.trim())
                            )
                        }
                    }

                    // ✅ Only add if not empty
                    if (arr.length() > 0) {
                        payload[key] = arr
                    }

                    return@forEach
                }


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
                            uriList = (v as? List<*>)
                                ?.mapNotNull {
                                    val s = it?.toString()
                                    if (!s.isNullOrBlank()) Uri.parse(s) else null
                                }
                                ?.toMutableList()

                        }

                        val metaList = photoMetaMap[key] ?: emptyList()

                        uriList?.forEachIndexed { index, uri ->

                            val file = File(uri.path ?: "")
                            if (!file.exists()) return@forEachIndexed

                            // 🔹 SAFE metadata access
                            val meta = metaList.getOrNull(index)

                            combinedDescArr.put(
                                JSONObject()
                                    .put("label", meta?.label ?: "")
                                    .put("description", meta?.description ?: "")
                                    .put("latitude", meta?.latitude?.toString() ?: "")
                                    .put("longitude", meta?.longitude?.toString() ?: "")
                            )

                            val partName = "galleryphotos[${uid}_${globalPhotoIndex}]"
                            val part = createMultipart(partName, uri)
                            galleryPhotos.add(part)

                            globalPhotoIndex++
                        }

                    }







                    else -> payload[key] = cleanJsonValue(v)
                }
            }
            val events =
                current.get("events") as? List<FormViewModel.Event>
                    ?: emptyList()

            if (events.isNotEmpty()) {

                val arr = JSONArray()

                events.forEach { e ->
                    val obj = JSONObject()
                    obj.put("name", e.name)
                    obj.put("description", e.eventDescription)
                    obj.put("date", e.date)

                    val slotsArr = JSONArray()
                    e.slots.forEach { slot ->
                        slotsArr.put(
                            JSONObject()
                                .put("open", minutesToHHMM(slot.open))
                                .put("close", minutesToHHMM(slot.close))
                        )
                    }

                    obj.put("slots", slotsArr)
                    arr.put(obj)
                }

                payload["events"] = arr
            }

            val sourceVerification =
                current["sourceVerification"] as? List<FormViewModel.SourceVerification>
                    ?: emptyList()

            if (sourceVerification.isNotEmpty()) {

                val arr = JSONArray()

                sourceVerification.forEach {
                    if (it.source.isNotBlank()) {
                        arr.put(
                            JSONObject()
                                .put("source", it.source)
                                .put("comment", it.comment)
                        )
                    }
                }

                payload["sourceVerification"] = arr
            }


            val operationalHours = current["operationalHours"]
            if (operationalHours is List<*> && operationalHours.isNotEmpty()) {
                payload["operationalHours"] = buildOperationalHours()
            }

            // STATIC META VALUES
            payload["movodreamId"] = schema.movodreamId
            payload["categoryId"] = selectedPOIData.categoryId

            payload["poiName"] = selectedPOIData.poiName
            payload["PoiId"] = selectedPOIData.poiId.toString()
            payload["AgentId"] = selectedPOIData.agentId
            payload["isRevision"] = if (_isRevisionState.value == true) "Y" else "N"

            val finalPayload = JSONObject().apply {

                // ✅ Payload MUST be STRING
                put(
                    "Payload",
                    JSONObject(payload as Map<*, *>).toString()
                )

                // ✅ Required top-level keys
                put("uid", uid)
                put("PoiId", selectedPOIData.poiId.toString())
                put("AgentId", selectedPOIData.agentId)

                // ✅ image_descriptions MUST be STRING
                if (combinedDescArr.length() > 0) {
                    put("image_descriptions", combinedDescArr.toString())
                }

            }

            val requestBody =
                finalPayload.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

            return@withContext Pair(requestBody, galleryPhotos)
        }

    fun callSubmitPOIDataMultiPart() {
        viewModelScope.launch {
            submitPOIResponse.value = ResponseHandler.Loading
            try {
                val (jsonPart, fileParts) = buildMultipartPayload()
                if (_isRevisionState.value == true) {
                    val response = repository.updatePOIData(jsonPart, fileParts)
                    submitPOIResponse.value = response
                } else {
                    val response = repository.submitPOIData(jsonPart, fileParts)
                    submitPOIResponse.value = response
                }
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

    // ✅ 1.7 MB limit (in bytes)
    val MAX_SIZE_BYTES = (1.7 * 1024 * 1024).toLong()

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

        // Step 2 — compress iteratively until ≤ 1.7 MB
        var quality = 95
        var byteArray: ByteArray

        do {
            val baos = ByteArrayOutputStream()
            baos.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
                byteArray = it.toByteArray()
            }
            quality -= 5
        } while (byteArray.size.toLong() > MAX_SIZE_BYTES && quality > 25)

        return byteArray
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
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

    fun callDeletePhotoAPI(imgId: String, agentId: String, poiId: String) {

        if (imgId.contains("|")) {
            val (url, idStr) = imgId.split("|")
            val imgId = idStr.toInt()

            // convert imgId -> List<Int>
            val deleteList = listOf(imgId)
            val request =
                DeletePhotoRequest(poiId, agentId, deleteList)
            viewModelScope.launch(Utils.coroutineContext) {
                deleteGalleryPhoto.value = ResponseHandler.Loading
                deleteGalleryPhoto.value = repository.deleteGalleryPhoto(request)
            }
        }
    }


    fun applyAssignedPoiOptions(assignedPois: List<TaskItem>) {
        val currentSchema = schemaLive.value ?: return

        val updatedTabs = currentSchema.tabs.map { tab ->
            val updatedFields = tab.fields.map { field ->
                if (field.id == "mainPOI" && field.type == "select") {
                    field.copy(
                        options = buildMainPoiOptions(assignedPois)
                    )
                } else {
                    field
                }
            }
            tab.copy(fields = updatedFields)
        }

        schemaLive.postValue(
            currentSchema.copy(tabs = updatedTabs)
        )
    }
    private fun buildMainPoiOptions(
        assignedPois: List<TaskItem>
    ): List<Option> {
        return assignedPois.map {
            Option(
                value = it.poiName, // stored
                label = it.poiName,
                id = it.poiId// shown
            )
        }
    }

//    suspend fun buildMultipartSubPOIPayload(): Pair<RequestBody, List<MultipartBody.Part>> =
//        withContext(Dispatchers.IO) {
//
//            val schema = schemaLive.value!!
//            val current = valuesLive.value ?: emptyMap<String, Any?>()
//
//            val payload = linkedMapOf<String, Any?>()
//            val galleryPhotos = mutableListOf<MultipartBody.Part>() // <-- SINGLE KEY
//
//
//            current.forEach { (key, v) ->
//
//                val fieldType = schema.tabs.flatMap { it.fields }
//                    .firstOrNull { it.id == key }?.type
//                //  HANDLE SELECT WITH "OTHER"
//                if (fieldType == "select" && v?.toString() == "Other") {
//                    val otherValue = current["${key}__other"]?.toString()?.trim()
//                    payload[key] = otherValue ?: ""
//                    return@forEach
//                }
//
//                when {
//
//
//                    fieldType == "checkbox_group" && v is List<*> -> {
//                        payload[key] = v.map { it.toString() }
//                    }
//
//                    fieldType == "photo_list" -> {
//                        //  keep existing URLs (already uploaded images on server)
//                        val serverUrls = photoUrls[key] ?: mutableListOf()
//                        if (serverUrls.isNotEmpty()) {
//                            //  payload["uploadedPhotos"] = serverUrls
//
//                        }
//                        var uriList = photoUris[key]
//                        if (uriList.isNullOrEmpty()) {
//                            uriList =
//                                (v as? List<*>)?.map { Uri.parse(it.toString()) }?.toMutableList()
//                        }
//
//                        uriList?.forEachIndexed { index, uri ->
//                            // ⚡ All images go under SAME key: GalleryPhotos
//                            val part = createMultipart("GalleryPhotos", uri)
//                            galleryPhotos.add(part)
//                        }
//                    }
//
//
//                    else -> payload[key] = cleanJsonValue(v)
//                }
//            }
//
//
//            // STATIC META VALUES
//            val selecteMainPOI = payload["mainPOI"]
//            var foundPoi: TaskItem? = null
//
//            if (assignPOIList != null) {
//                for (item in assignPOIList!!) {
//                    if (item.poiName == selecteMainPOI) {
//                        foundPoi = item
//                        break   // stop loop once found
//                    }
//                }
//            }
//
//            if (foundPoi != null) {
//                payload["categoryId"] = foundPoi.categoryId
//                payload["poiName"] = foundPoi.poiName
//                payload["PoiId"] = foundPoi.poiId.toString()
//                payload["AgentId"] = foundPoi.agentId
//            }
//
//
//
//            val requestBody =
//                JSONObject(payload as Map<*, *>)
//                    .toString()
//                    .toRequestBody("application/json".toMediaTypeOrNull())
//
//            return@withContext Pair(requestBody, galleryPhotos)
//        }

    suspend fun buildCreatePOIPayload(): Map<String,Any> =
        withContext(Dispatchers.IO) {

            val schema = schemaLive.value!!
            val current = valuesLive.value ?: emptyMap<String, Any?>()

            val payload = linkedMapOf<String, Any>()



            current.forEach { (key, v) ->

                val fieldType = schema.tabs.flatMap { it.fields }
                    .firstOrNull { it.id == key }?.type
                //  HANDLE SELECT WITH "OTHER"
                if (fieldType == "select" && v?.toString() == "Other") {
                    val otherValue = current["${key}__other"]?.toString()?.trim()
                    payload[key] = otherValue ?: ""
                    return@forEach
                }

                when {


                    fieldType == "checkbox_group" && v is List<*> -> {
                        payload[key] = v.map { it.toString() }
                    }

                    //  Convert latitude & longitude to String
                    key.equals("latitude", true) && v is Double -> {
                        payload[key] = v.toString()
                    }

                    key.equals("longitude", true) && v is Double -> {
                        payload[key] = v.toString()
                    }

                    //  Fallback for String lat/long (safety)
                    (key.equals("latitude", true) || key.equals("longitude", true)) -> {
                        payload[key] = v?.toString().orEmpty()
                    }


                    else -> payload[key] = cleanJsonValue(v)
                }
            }

            if (_isAddPOIState.value == true) {
                getSelectedCategoryId()?.let {
                    payload["categoryId"] = it
                }
                payload["agentId"] = assignPOIList[0].agentId
                payload["progress"] = 0
                payload["poiId"] = generate4DigitInt()
                payload["revisionRequired"] = false
                payload["contactNo"] = ""
                payload["revisionMessage"] = ""
                payload["taskPriority"] = "Medium"
                payload["taskStatus"] = "Pending"
            }




            return@withContext payload
        }


    fun generate4DigitInt(): Int {
        val timePart = (System.currentTimeMillis() % 100).toInt()   // 0–99
        val randomPart = (0..99).random()                           // 0–99
        return timePart * 100 + randomPart                          // 0000–9999
    }

    suspend fun buildMultipartSubPOIPayloadAsBulk():
            Pair<RequestBody, List<MultipartBody.Part>> =
        withContext(Dispatchers.IO) {

            val schema = schemaLive.value!!
            val current = valuesLive.value ?: emptyMap<String, Any?>()

            val payload = linkedMapOf<String, Any?>()
           // val photoUris = mutableListOf<Uri>()
            val galleryPhotos = mutableListOf<MultipartBody.Part>() // <-- SINGLE KEY

            val combinedDescArr = JSONArray()

            val uid = randomDigits(9) // 🔥 ONE uid for entire request
            var globalPhotoIndex = 0  // 🔥 CONTINUOUS index
            // -------------------------------------------------
            // 1️⃣ EXISTING SINGLE PAYLOAD LOGIC (UNCHANGED)
            // -------------------------------------------------
            current.forEach { (key, v) ->

                val fieldType = schema.tabs.flatMap { it.fields }
                    .firstOrNull { it.id == key }?.type

                // HANDLE "Other"
                if (fieldType == "select" && v?.toString() == "Other") {
                    payload[key] =
                        current["${key}__other"]?.toString()?.trim().orEmpty()
                    return@forEach
                }

                when {
                    fieldType == "checkbox_group" && v is List<*> -> {
                        payload[key] = v.map { it.toString() }
                    }

                    fieldType == "photo_list" -> {

                        var uriList = photoUris[key]
                        if (uriList.isNullOrEmpty()) {
                            uriList = (v as? List<*>)
                                ?.mapNotNull {
                                    val s = it?.toString()
                                    if (!s.isNullOrBlank()) Uri.parse(s) else null
                                }
                                ?.toMutableList()

                        }

                        val metaList = photoMetaMap[key] ?: emptyList()

                        uriList?.forEachIndexed { index, uri ->

                            val file = File(uri.path ?: "")
                            if (!file.exists()) return@forEachIndexed

                            // 🔹 SAFE metadata access
                            val meta = metaList.getOrNull(index)

                            combinedDescArr.put(
                                JSONObject()
                                    .put("label", meta?.label ?: "")
                                    .put("description", meta?.description ?: "")
                                    .put("latitude", meta?.latitude?.toString() ?: "")
                                    .put("longitude", meta?.longitude?.toString() ?: "")
                            )

                            val partName = "galleryphotos[${uid}_${globalPhotoIndex}]"
                            val part = createMultipart(partName, uri)
                            galleryPhotos.add(part)

                            globalPhotoIndex++
                        }

                    }

                    else -> payload[key] = cleanJsonValue(v)
                }
            }

            // STATIC META VALUES
            val selecteMainPOI = payload["mainPOI"]
            var foundPoi: TaskItem? = null

            if (assignPOIList != null) {
                for (item in assignPOIList!!) {
                    if (item.poiName == selecteMainPOI) {
                        foundPoi = item
                        break   // stop loop once found
                    }
                }
            }

            if (foundPoi != null) {
                payload["categoryId"] = foundPoi.categoryId
                payload["poiName"] = foundPoi.poiName
                payload["PoiId"] = foundPoi.poiId.toString()
                payload["AgentId"] = foundPoi.agentId
            }


            val bulkItem = JSONObject().apply {
                put("Payload", JSONObject(payload as Map<*, *>).toString())
                put("uid", uid)
                put("PoiId", payload["PoiId"])
                put("AgentId", payload["AgentId"])
                put("image_descriptions", combinedDescArr.toString())
            }



            val bulkArray = JSONArray().apply { put(bulkItem) }





            val requestBody =
                bulkArray.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

            return@withContext Pair(requestBody, galleryPhotos)
        }

    fun submitSubPOI(selectedPOI: List<TaskItem>?) {
        if (selectedPOI != null) {
            assignPOIList = selectedPOI
        }
        viewModelScope.launch {
            addPOISubPOIResponse.value = ResponseHandler.Loading
            try {

                if(_isAddPOIState.value ==true){

                }else {
                    val (jsonPart, fileParts) = buildMultipartSubPOIPayloadAsBulk()
                    val response = repository.addBulkSubPOIData(jsonPart, fileParts)
                    addPOISubPOIResponse.value = response
                }

            } catch (e: Exception) {
                addPOISubPOIResponse.value = ResponseHandler.OnFailed(
                    500, e.message ?: "Something went wrong", "500"
                )
            }
        }
    }

    fun createPOI(selectedPOI: List<TaskItem>?) {
        if (selectedPOI != null) {
            assignPOIList = selectedPOI
        }
        viewModelScope.launch {
            createPOIResponse.value = ResponseHandler.Loading
            try {

                if(_isAddPOIState.value ==true){
                    val response = repository.createPOI(buildCreatePOIPayload())
                    createPOIResponse.value = response
                }

            } catch (e: Exception) {
                createPOIResponse.value = ResponseHandler.OnFailed(
                    500, e.message ?: "Something went wrong", "500"
                )
            }
        }
    }

    fun getSelectedCategoryId(): Int? {
        val selectedValue = valuesLive.value?.get("category") as? String
        return categoryOptionsLive.value
            ?.firstOrNull { it.value == selectedValue }
            ?.id
    }

    //sub poi
    private fun getFieldSchema(fieldId: String): FieldSchema? {
        return schemaLive.value
            ?.tabs
            ?.flatMap { it.fields }
            ?.firstOrNull { it.id == fieldId }
    }

    fun validateSubPoiInput(): Map<String, String> {

        val values = valuesLive.value ?: return emptyMap()
        val errors = mutableMapOf<String, String>()

        fun checkRequired(fieldId: String, message: String) {
            val field = getFieldSchema(fieldId) ?: return
            if (!field.required) return

            val value = values[fieldId]
            val isEmpty = when (value) {
                null -> true
                is String -> value.trim().isEmpty()
                is Number -> false
                else -> false
            }

            if (isEmpty) errors[fieldId] = message
        }

        checkRequired("subPoiName", "Sub POI name is required")
        checkRequired("description", "Description is required")
        checkRequired("subPoiLatitude", "Latitude is required")
        checkRequired("subPoiLongitude", "Longitude is required")
        checkRequired("subPoiPhysicalAddress", "Address is required")
        checkRequired("subPoiLocalGuruRemarks", "Remark is required")

        // ✅ Correct photo validation
        val photoField = getFieldSchema("subPoiGalleryPhotos")
        if (photoField?.required == true) {

            val localPhotos = photoUris["subPoiGalleryPhotos"] ?: emptyList()
            val serverPhotos = photoUrls["subPoiGalleryPhotos"] ?: emptyList()

            if (localPhotos.isEmpty() && serverPhotos.isEmpty()) {
                errors["subPoiGalleryPhotos"] = "At least one photo is required"
            }
        }

        return errors
    }


    fun validateSubPoiListForDraft(tabId: String): Map<String, String> {

        val schema = schemaLive.value ?: return emptyMap()
        val tab = schema.tabs.firstOrNull { it.id == tabId } ?: return emptyMap()
        val values = valuesLive.value ?: emptyMap()

        // Find ONLY the sub_poi_list field
        val listField = tab.fields.firstOrNull { it.type == "sub_poi_list" }
            ?: return emptyMap()

        // If list itself is NOT required → skip validation
        if (listField.required != true) return emptyMap()

        val list =
            (values[listField.id] as? List<*>) ?: emptyList<Any>()

        val min = listField.minItems ?: 0

        return if (list.size < min) {
            mapOf(listField.id to "Please add at least $min Sub POI")
        } else {
            emptyMap()
        }
    }


    fun addSubPoi(): Map<String, String> {

        val errors = validateSubPoiInput()
        if (errors.isNotEmpty()) return errors

        val v = valuesLive.value ?: mutableMapOf()

        // ------------------------------------------------
        // 1️⃣ Collect photo URIs
        // ------------------------------------------------
        val photoUrisList = mutableListOf<String>()

        photoUrls["subPoiGalleryPhotos"]?.let { photoUrisList.addAll(it) }
        photoUris["subPoiGalleryPhotos"]?.let {
            photoUrisList.addAll(it.map { uri -> uri.toString() })
        }

        // ------------------------------------------------
        // 2️⃣ Collect photo METADATA (FREEZE COPY)
        // ------------------------------------------------
        val metaList =
            photoMetaMap["subPoiGalleryPhotos"]
                ?.map { it.copy() }   // 🔥 important: deep copy
                ?: emptyList()

        // ------------------------------------------------
        // 3️⃣ Build Sub-POI object
        // ------------------------------------------------
        val subPoi = mutableMapOf<String, Any?>(
            "subPoiName" to v["subPoiName"],
            "description" to v["description"],
            "latitude" to v["subPoiLatitude"],
            "longitude" to v["subPoiLongitude"],
            "physicalAddress" to v["subPoiPhysicalAddress"],
            "localGuruRemarks" to v["subPoiLocalGuruRemarks"],
            "pinVerifiedViaGps" to "Y",
            "localityTown" to v["localityTown"],
            "regionState" to v["regionState"],
            "country" to v["country"],
            "subPoiGalleryPhotos" to photoUrisList,
            "subPoiGalleryPhotos__meta" to metaList
        )

        // ------------------------------------------------
        // 4️⃣ Add to addSubPois list
        // ------------------------------------------------
        val list =
            (v["addSubPois"] as? MutableList<Map<String, Any?>>)
                ?: mutableListOf()

        list.add(subPoi)

        updateValue("addSubPois", list)
        notifyFieldChanged("addSubPois")

        // ------------------------------------------------
        // 5️⃣ Clear input fields
        // ------------------------------------------------
        listOf(
            "subPoiName",
            "description",
            "subPoiLatitude",
            "subPoiLongitude",
            "subPoiPhysicalAddress",
            "subPoiLocalGuruRemarks"
        ).forEach { updateValue(it, "") }

        // ------------------------------------------------
        // 6️⃣ CLEAR WORKING PHOTO BUFFERS (CRITICAL)
        // ------------------------------------------------
        photoUris.remove("subPoiGalleryPhotos")
        photoUrls.remove("subPoiGalleryPhotos")
        photoMetaMap.remove("subPoiGalleryPhotos")

        updateValue("subPoiGalleryPhotos", emptyList<String>())
        notifyFieldChanged("subPoiGalleryPhotos")
        calculateProgress()
        return emptyMap()
    }




    fun removeSubPoi(index: Int) {
        val v = valuesLive.value ?: return
        val list = (v["addSubPois"] as? MutableList<*>) ?: return
        if (index !in list.indices) return

        list.removeAt(index)
        updateValue("addSubPois", list)
        notifyFieldChanged("addSubPois")
        calculateProgress()
    }


    fun hasAtLeastOneSubPoi(): Boolean {
        val list = valuesLive.value?.get("addSubPois") as? List<*>
        return !list.isNullOrEmpty()
    }


    //Opreational Hours
    // ---------------- OPERATIONAL HOURS ----------------
    enum class WeekDay { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY }

    data class TimeSlot(
        var open: Int,   // minutes from 00:00
        var close: Int
    )

    data class OperationalDay(
        val day: WeekDay,
        var isClosed: Boolean = true,
        var isOpen24Hours: Boolean = false,
        val slots: MutableList<TimeSlot> = mutableListOf()
    )
    fun ensureOperationalHours() {
        val map = valuesLive.value ?: mutableMapOf()

        val current = map["operationalHours"] as? MutableList<OperationalDay>

        if (current == null || current.isEmpty()) {
            val initialized = WeekDay.values().map {
                OperationalDay(day = it)
            }.toMutableList()

            map["operationalHours"] = initialized
            valuesLive.postValue(map)
        }
    }



    fun buildOperationalHours(): JSONObject {

        val week = valuesLive.value?.get("operationalHours")
                as? List<FormViewModel.OperationalDay>
            ?: emptyList()

        val weeklyArr = JSONArray()

        week.forEach { day ->

            val obj = JSONObject()
            obj.put("day", day.day.name)
            obj.put("isClosed", day.isClosed)
            obj.put("isOpen24Hours", day.isOpen24Hours)

            val slotsArr = JSONArray()
            if (!day.isClosed && !day.isOpen24Hours) {
                day.slots.forEach {
                    slotsArr.put(
                        JSONObject()
                            .put("open", minutesToHHMM(it.open))
                            .put("close", minutesToHHMM(it.close))
                    )
                }
            }

            obj.put("slots", slotsArr)
            weeklyArr.put(obj)
        }

        // ================================================================
        // 2️⃣ HOLIDAY EXCEPTIONS
        // ================================================================
        val holidays =
            valuesLive.value?.get("holidayExceptions")
                    as? List<FormViewModel.HolidayException>
                ?: emptyList()

        val holidayArr = JSONArray()

        holidays.forEach { h ->

            val obj = JSONObject()
            obj.put("name", h.name)              // ✅ free text
            obj.put("date", h.date)              // yyyy-MM-dd
            obj.put("isClosed", h.isClosed)
            obj.put("isOpen24Hours", h.isOpen24Hours)

            val slotsArr = JSONArray()
            if (!h.isClosed && !h.isOpen24Hours) {
                h.slots.forEach { slot ->
                    slotsArr.put(
                        JSONObject()
                            .put("open", minutesToHHMM(slot.open))
                            .put("close", minutesToHHMM(slot.close))
                    )
                }
            }

            obj.put("slots", slotsArr)
            holidayArr.put(obj)
        }


        // ================================================================
        // 3️⃣ SEASONAL HOURS
        // ================================================================
        val seasons =
            valuesLive.value?.get("seasonalHours")
                    as? List<FormViewModel.SeasonalHours>
                ?: emptyList()

        val seasonalArr = JSONArray()

        seasons.forEach { s ->

            val obj = JSONObject()
            obj.put("name", s.name)              // ✅ free text
            obj.put("startDate", s.startDate)    // yyyy-MM-dd
            obj.put("endDate", s.endDate)        // yyyy-MM-dd
            obj.put("isClosed", s.isClosed)
            obj.put("isOpen24Hours", s.isOpen24Hours)

            val slotsArr = JSONArray()
            if (!s.isClosed && !s.isOpen24Hours) {
                s.slots.forEach { slot ->
                    slotsArr.put(
                        JSONObject()
                            .put("open", minutesToHHMM(slot.open))
                            .put("close", minutesToHHMM(slot.close))
                    )
                }
            }

            obj.put("slots", slotsArr)
            seasonalArr.put(obj)
        }



        return JSONObject().apply {
            put("timezone", TimeZone.getDefault().id)
            put("weekly", weeklyArr)
            put("holidays", holidayArr)
            put("seasonal", seasonalArr)
        }
    }

    private fun minutesToHHMM(min: Int): String {
        val h = min / 60
        val m = min % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    data class HolidayException(
        var name: String,
        var date: String, // yyyy-MM-dd
        var isClosed: Boolean = false,
        var isOpen24Hours: Boolean = false,
        var slots: MutableList<TimeSlot> = mutableListOf()
    )

    data class SeasonalHours(
        var name: String,
        var startDate: String, // yyyy-MM-dd
        var endDate: String,   // yyyy-MM-dd
        var isClosed: Boolean = false,
        var isOpen24Hours: Boolean = false,
        var slots: MutableList<TimeSlot> = mutableListOf()
    )

    fun ensureHolidayAndSeasonal() {
        val map = valuesLive.value ?: mutableMapOf()

        if (map["holidayExceptions"] == null) {
            map["holidayExceptions"] = mutableListOf<HolidayException>()
        }

        if (map["seasonalHours"] == null) {
            map["seasonalHours"] = mutableListOf<SeasonalHours>()
        }

        valuesLive.postValue(map)
    }

    fun addHoliday(h: HolidayException) {
        val map = valuesLive.value ?: return
        val list =
            (map["holidayExceptions"] as? MutableList<HolidayException>)
                ?: mutableListOf()

        list.add(h)
        map["holidayExceptions"] = list
        valuesLive.postValue(map)
        notifyFieldChanged("holidayExceptions")
    }

    fun removeHoliday(index: Int) {
        val map = valuesLive.value ?: return
        val list = map["holidayExceptions"] as? MutableList<HolidayException> ?: return
        if (index in list.indices) {
            list.removeAt(index)
            notifyFieldChanged("holidayExceptions")
        }
    }

    fun addSeasonal(s: SeasonalHours) {
        val map = valuesLive.value ?: return
        val list =
            (map["seasonalHours"] as? MutableList<SeasonalHours>)
                ?: mutableListOf()

        list.add(s)
        map["seasonalHours"] = list
        valuesLive.postValue(map)
        notifyFieldChanged("seasonalHours")
    }

    fun removeSeasonal(index: Int) {
        val map = valuesLive.value ?: return
        val list = map["seasonalHours"] as? MutableList<SeasonalHours> ?: return
        if (index in list.indices) {
            list.removeAt(index)
            notifyFieldChanged("seasonalHours")
        }
    }
//Meta data

    enum class MetadataType {
        TEXT,
        NUMBER,
        DATE,
        TIME
    }

    data class CustomMetadata(
        var label: String = "",
        var type: MetadataType = MetadataType.TEXT,
        var value: String = ""   // stored as string for ALL types (safe for JSON & draft)
    )


    // -----------------------------
    // VALIDATION (called on submit)
    // -----------------------------
    fun validateCustomMetadata(required: Boolean): Map<String, String> {
        if (!required) return emptyMap()

        val list = valuesLive.value?.get("customMetadata")
                as? List<CustomMetadata> ?: return emptyMap()

        if (list.isEmpty()) {
            return mapOf("customMetadata" to "Please add at least one custom metadata field")
        }

        list.forEach {
            if (it.label.isBlank()) {
                return mapOf("customMetadata" to "Field label is required")
            }
            if (it.value.isBlank()) {
                return mapOf("customMetadata" to "Field value is required")
            }
            if (it.type == MetadataType.NUMBER && it.value.any { ch -> !ch.isDigit() }) {
                return mapOf("customMetadata" to "Number field must contain only digits")
            }
        }
        return emptyMap()
    }


    suspend fun buildBulkSubPoiMultipartPayload():
            Pair<RequestBody, List<MultipartBody.Part>> =
        withContext(Dispatchers.IO) {

            val values = valuesLive.value ?: emptyMap()
            val listAll = values["addSubPois"] as? List<Map<String, Any?>>
                ?: emptyList()

            if (listAll.isEmpty()) {
                val emptyBody = "[]"
                    .toRequestBody("application/json".toMediaTypeOrNull())
                return@withContext Pair(emptyBody, emptyList())
            }

          //  Index safety check (VERY IMPORTANT)
            if (currentSubPoiIndex !in listAll.indices) {
                val emptyBody = "{}"
                    .toRequestBody("application/json".toMediaTypeOrNull())
                return@withContext Pair(emptyBody, emptyList())
            }


//  Correct Sub-POI for this call
            val list: List<Map<String, Any?>> =
                listOf(listAll[currentSubPoiIndex])
            val payloadArray = JSONArray()
            val fileParts = mutableListOf<MultipartBody.Part>()

            list.forEachIndexed { subPoiIndex, subPoi ->

                // --------------------------------------------------
                // 🔑 GENERATED VALUES
                // --------------------------------------------------
                val uid = randomDigits(8) + subPoiIndex        // uid
                val subPoiServerId = randomDigits(3) + subPoiIndex

                val subPoiName = subPoi["subPoiName"]?.toString().orEmpty()
                val latitude = subPoi["latitude"] ?: 0.0
                val longitude = subPoi["longitude"] ?: 0.0


                // --------------------------------------------------
                // 1️⃣ Payload JSON (STRING)
                // --------------------------------------------------
                val payloadJson = JSONObject().apply {
                    put("mainPOI", selectedPOIData.poiName)
                    put("subPoiName", subPoiName)
                    put("description", subPoi["description"])
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("physicalAddress", subPoi["physicalAddress"])
                    put("localGuruRemarks", subPoi["localGuruRemarks"])
                    put("localityTown", subPoi["localityTown"])
                    put("regionState", subPoi["regionState"])
                    put("country", subPoi["country"])
                    put("pinVerifiedViaGps", "Y")
                    put("categoryId", selectedPOIData.categoryId)
                    put("poiName", selectedPOIData.poiName)
                    put("PoiId", selectedPOIData.poiId.toString())
                    put("AgentId", selectedPOIData.agentId)
                }


                // --------------------------------------------------
// 2️⃣ IMAGE METADATA — READ FROM SUB-POI (FIXED)
// --------------------------------------------------
                val imageDescArr = JSONArray()

                val metaList =
                    (subPoi["subPoiGalleryPhotos__meta"] as? List<*>)?.mapNotNull { m ->
                        when (m) {
                            is PhotoWithMeta -> m

                            is Map<*, *> -> PhotoWithMeta(
                                uri = m["uri"]?.toString().orEmpty(),
                                label = m["label"]?.toString().orEmpty(),
                                description = m["description"]?.toString().orEmpty(),
                                latitude = (m["latitude"] as? Number)?.toDouble() ?: 0.0,
                                longitude = (m["longitude"] as? Number)?.toDouble() ?: 0.0
                            )

                            else -> null
                        }
                    } ?: emptyList()



                metaList.forEach { p ->
                    imageDescArr.put(
                        JSONObject()
                            .put("label", p.label)
                            .put("description", p.description)
                            .put("latitude", p.latitude?.toString() ?: "")
                            .put("longitude", p.longitude?.toString() ?: "")
                    )
                }

                // --------------------------------------------------
                // 3️⃣ Final item object
                // --------------------------------------------------
                val itemObj = JSONObject().apply {
                    put("Payload", payloadJson.toString())           // STRING
                    put("uid", uid)
                    put("PoiId", selectedPOIData.poiId.toString())                     // GENERATED
                    put("AgentId", selectedPOIData.agentId)
                    put("image_descriptions", imageDescArr.toString()) // STRING
                }

                payloadArray.put(itemObj)



                // --------------------------------------------------
// 4️⃣ Multipart images (SAME ORDER AS META) — FIXED
// --------------------------------------------------
                metaList.forEachIndexed { index, p ->
                    val uri = Uri.parse(p.uri)
                    val file = File(uri.path ?: "")
                    if (!file.exists()) return@forEachIndexed

                    val partName = "galleryphotos[${uid}_${index}]"
                    val part = createMultipart(partName, uri)
                    fileParts.add(part)
                }


            }

            val requestBody =
                payloadArray.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

            return@withContext Pair(requestBody, fileParts)
        }


    fun submitBulkSubPOIAfterMainPOI() {

        viewModelScope.launch {
            bulkSubPOIResponse.value = ResponseHandler.Loading
            try {

                val (jsonPart, fileParts) = buildBulkSubPoiMultipartPayload()
                val response = repository.addBulkSubPOIData(jsonPart, fileParts)
                bulkSubPOIResponse.value = response


            } catch (e: Exception) {
                bulkSubPOIResponse.value = ResponseHandler.OnFailed(
                    500, e.message ?: "Something went wrong", "500"
                )
            }
        }
    }
    private fun randomDigits(length: Int): String =
        (1..length)
            .map { ('0'..'9').random() }
            .joinToString("")


    //  photo metadata per fieldId
    // fieldId -> list of photo meta
    private val photoMetaMap =
        mutableMapOf<String, MutableList<PhotoWithMeta>>()

//    fun savePhotoMetadata(fieldId: String, list: List<PhotoWithMeta>) {
//        photoMetaMap[fieldId] = list.toMutableList()
//    }

    fun savePhotoMetadata(fieldId: String, list: List<PhotoWithMeta>) {

        // 1️⃣ Save metadata
        photoMetaMap[fieldId] = list.toMutableList()

        // 2️⃣ 🔥 ALSO save upload URIs (THIS FIXES VALIDATION)
        val localUris = mutableListOf<Uri>()

        list.forEach { meta ->
            val uriStr = meta.uri
            if (uriStr.startsWith("file://")) {
                val file = File(uriStr.removePrefix("file://"))
                if (file.exists()) {
                    localUris.add(Uri.fromFile(file))
                }
            }
        }

        photoUris[fieldId] = localUris
    }


    fun getPhotoMetadata(fieldId: String): List<PhotoWithMeta> =
        photoMetaMap[fieldId] ?: emptyList()


    fun removePhoto(fieldId: String, uri: String) {

    // 1️⃣ Remove metadata
    photoMetaMap[fieldId]?.removeAll { it.uri == uri }

    // 2️⃣ Remove URI (SOURCE OF TRUTH)
    photoUris[fieldId]?.removeAll { it.toString() == uri }

    val current = valuesLive.value?.toMutableMap() ?: mutableMapOf()

    // 3️⃣ Rebuild strictly from photoUris
    val updatedUris = photoUris[fieldId]?.map { it.toString() } ?: emptyList()

    current[fieldId] = updatedUris
    valuesLive.postValue(current)

    notifyFieldChanged(fieldId)
        calculateProgress()
}




    fun calculateProgress() {

        val schema = schemaLive.value ?: return
        val values = valuesLive.value ?: emptyMap()

        var totalFields = 0
        var filledFields = 0

        schema.tabs.forEach { tab ->
            tab.fields.forEach { field ->

                //  Skip non-input fields
                if (field.type in listOf("label", "divider", "section_header")) {
                    return@forEach
                }

                totalFields++

                if (isFieldFilled(field, values)) {
                    filledFields++
                }
            }
        }

        val percentage =
            if (totalFields == 0) 0
            else ((filledFields * 100f) / totalFields).toInt()

        _progressPercent.postValue(percentage)
    }
    private fun isFieldFilled(
        field: FieldSchema,
        values: Map<String, Any?>
    ): Boolean {

        val value = values[field.id]

        return when (field.type) {

            "text", "textarea", "select" ->
                value is String && value.trim().isNotEmpty()

            "number" ->
                value != null

            "checkbox_group" ->
                value is List<*> && value.isNotEmpty()

            "photo_list" ->
                (photoUris[field.id]?.size ?: 0) > 0

            "text_list" ->
                value is List<*> && value.any {
                    it?.toString()?.trim()?.isNotEmpty() == true
                }

            "sub_poi_list" ->
                value is List<*> && value.isNotEmpty()

            "operational_hours" ->
                value is List<*> && value.isNotEmpty()

            else -> false
        }
    }

    fun fetchAccurateLocation() {



        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .build()

        try {

            fusedClient.getCurrentLocation(request, object : CancellationToken() {
                override fun onCanceledRequested(p0: OnTokenCanceledListener): CancellationToken = this
                override fun isCancellationRequested() = false
            }).addOnSuccessListener { loc ->

                if (loc != null) {

                    // format to required 6 decimal precision
                    val latStr = String.format(Locale.US, "%.6f", loc.latitude)
                    val lngStr = String.format(Locale.US, "%.6f", loc.longitude)
                     Log.d("LAT Rohit :",latStr)
                    // update values (as String)
                    updateValue("backgroundLatitude", latStr)
                    updateValue("backgroundLongitude", lngStr)




                } else {

                }

            }.addOnFailureListener {

            }

        } catch (e: SecurityException) {

        }
    }

//Add Events

    data class Event(
        val name: String,
        val date: String,                // yyyy-MM-dd
        val eventDescription: String,                // yyyy-MM-dd
        val slots: MutableList<TimeSlot> = mutableListOf()
    )
    fun addEvent(e: Event) {
        val current =
            (valuesLive.value?.get("events") as? MutableList<Event>)
                ?: mutableListOf()

        current.add(e)
        updateValue("events", current)
        notifyFieldChanged("events")
    }

//    fun removeEvent(e: Event) {
//        val current =
//            (valuesLive.value?.get("events") as? MutableList<Event>)
//                ?: return
//
//        current.remove(e)
//        updateValue("events", current)
//        notifyFieldChanged("events")
//    }

    fun removeEvent(event: Event) {

        val currentMap = valuesLive.value?.toMutableMap() ?: return

        val oldList = currentMap["events"] as? List<Event> ?: return

        //  ALWAYS create new list
        val newList = oldList
            .filterNot {
                it.name == event.name &&
                        it.date == event.date
            }
            .toMutableList()

        currentMap["events"] = newList

        //  update VM state
        valuesLive.postValue(currentMap)

        //  REQUIRED in your architecture
        notifyFieldChanged("events")
    }

//Event end

//Add Facilities like parking, washroom, drinking water etc


    data class FacilityPoint(
        val label: String,
        val description: String,
        val latitude: String,
        val longitude: String,
        val landmark: String
    )




    fun addFacility(fieldId: String, facility: FacilityPoint) {

        val current =
            valuesLive.value?.get(fieldId) as? MutableList<FacilityPoint>
                ?: mutableListOf()

        current.add(facility)

        updateValue(fieldId, current)
        notifyFieldChanged(fieldId)
    }


    fun removeFacility(fieldId: String, item: FacilityPoint) {

        val current = valuesLive.value?.toMutableMap() ?: return

        val oldList = current[fieldId] as? List<FacilityPoint> ?: return

        //  Create NEW mutable list
        val newList = oldList.toMutableList()

        //  Remove by stable identity (NOT object reference)
        newList.removeAll { fp ->
            fp.label == item.label &&
                    fp.latitude == item.latitude &&
                    fp.longitude == item.longitude
        }

        current[fieldId] = newList

        //  Publish NEW map instance
        valuesLive.postValue(current)
        notifyFieldChanged(fieldId)
    }
//Secondary Address

    data class SecondaryAddress(
        val address: String,
        val latitude: String,
        val longitude: String,
        val landmark: String
    )

    fun addAddress(fieldId: String, facility: SecondaryAddress) {

        val current =
            valuesLive.value?.get(fieldId) as? MutableList<SecondaryAddress>
                ?: mutableListOf()

        current.add(facility)

        updateValue(fieldId, current)
        notifyFieldChanged(fieldId)
    }


    fun removeAddress(fieldId: String, item: SecondaryAddress) {

        val current = valuesLive.value?.toMutableMap() ?: return

        val oldList = current[fieldId] as? List<SecondaryAddress> ?: return

        //  Create NEW mutable list
        val newList = oldList.toMutableList()

        //  Remove by stable identity (NOT object reference)
        newList.removeAll { fp ->
            fp.address == item.address &&
                    fp.latitude == item.latitude &&
                    fp.longitude == item.longitude
        }

        current[fieldId] = newList

        //  Publish NEW map instance
        valuesLive.postValue(current)
        notifyFieldChanged(fieldId)
    }
//Source Verification

    data class SourceVerification(
        val source: String,
        var comment: String = ""
    )


}
