package com.movodream.localguru.data_collection.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.core.constants.AppConstants
import com.core.preferences.MyPreference
import com.core.preferences.PrefKey
import com.core.utils.SingleLiveEvent
import com.core.utils.Utils
import com.data.local.AppDatabase
import com.data.local.entity.DraftEntity
import com.data.remote.model.AgentTaskResponse
import com.data.remote.model.RevisionDataResponse
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.SummaryItem
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.repository.CategoryResult
import com.movodream.localguru.data_collection.repository.DashboardRepository
import com.movodream.localguru.data_collection.repository.DraftRepository
import com.network.client.ResponseHandler
import com.network.model.ResponseData
import com.network.model.ResponseListData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DashboardRepository()

    private val _tasks = MutableLiveData<List<TaskItem>>()
    val tasks: LiveData<List<TaskItem>> = _tasks

    private val _dashboardTasks = MutableLiveData<List<TaskItem>>()
    val dashboardTasks: LiveData<List<TaskItem>> = _dashboardTasks

    private val _summaryItems = MutableLiveData<List<SummaryItem>>()
    val summaryItems: LiveData<List<SummaryItem>> = _summaryItems

    private val _filteredTasks = MutableLiveData<List<TaskItem>>()
    val filteredTasks: LiveData<List<TaskItem>> = _filteredTasks

    private val _selectedTab = MutableLiveData<Int>()
    val selectedTab: LiveData<Int> = _selectedTab

    // API response holder
    var dashboardResponse =
        MutableLiveData<ResponseHandler<ResponseData<AgentTaskResponse>?>>()

    var revisionDataResponse =
        MutableLiveData<ResponseHandler<ResponseListData<RevisionDataResponse>?>>()
    var poiDataResponse =
        MutableLiveData<ResponseHandler<ResponseListData<RevisionDataResponse>?>>()
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(getApplication())
    }
    private val draftRepository by lazy {
        DraftRepository(database.draftDao())
    }
    init {
        _tasks.value = emptyList()
        _dashboardTasks.value = emptyList()
        _filteredTasks.value = emptyList()
        _summaryItems.value = emptyList()
    }




    // --------------------------------------------------------
    //  Convert AgentTaskResponse → TaskItem + SummaryItem
    // --------------------------------------------------------
    fun mapDashboardData(data: AgentTaskResponse) {
        // Summary mapping

        _summaryItems.value = data.taskSummary.map { item ->

            when (item.taskName.lowercase()) {

                "pending" -> SummaryItem(
                    iconRes = R.drawable.ic_task,
                    bgRes = com.core.R.drawable.bg_pending_tasks,
                    count = item.count,
                    label = "Pending Tasks"
                )

                "completed" -> SummaryItem(
                    iconRes = R.drawable.ic_circle,
                    bgRes = com.core.R.drawable.bg_completed_today,
                    count = item.count,
                    label = "Completed Today"
                )

                "revision", "need revision", "revision required" -> SummaryItem(
                    iconRes = com.core.R.drawable.ic_revision,
                    bgRes = com.core.R.drawable.bg_need_revision,
                    count = item.count,
                    label = "Need Revision"
                )

                "verified", "verified data" -> SummaryItem(
                    iconRes = R.drawable.ic_reward,
                    bgRes = com.core.R.drawable.bg_verified_data,
                    count = item.count,
                    label = "Verified Data"
                )

                else -> SummaryItem(
                    iconRes = R.drawable.ic_task,
                    bgRes = com.core.R.drawable.bg_pending_tasks,
                    count = item.count,
                    label = item.taskName
                )
            }
        }


        val mappedTasks = data.assignedPOIs.map { poi ->


            // 🔥 Determine Priority Based on Rules
            val computedPriority = when {
                poi.revisionRequired -> AppConstants.STATUS_REVISION_NEEDED
                poi.taskStatus == "Pending" -> AppConstants.STATUS_NOT_STARTED
                poi.taskStatus == "Completed" -> AppConstants.STATUS_COMPLETED
                poi.taskStatus == "In Progress" -> AppConstants.STATUS_HIGH_PRIORITY
                else -> AppConstants.STATUS_NOT_STARTED
            }

            TaskItem(
                poiId = poi.poiId,
                poiName = poi.poiName,
                category = poi.category,
                categoryId = poi.categoryId.toString(),
               // categoryId = "RELIGIOUS_SITES",

                // 🔥 Replacing API value with computed priority
                taskPriority = computedPriority,

                taskStatus = poi.taskStatus,
                progress = poi.progress,
                revisionRequired = poi.revisionRequired,
                revisionMessage = poi.revisionMessage ?: "",
                contactNo = poi.contactNo,
                latitude = poi.latitude,
                longitude = poi.longitude,
                agentName = data.agentName,
                agentId = data.agentId,
                agentLocation = data.dataCollectionLocation,

                subTasks = poi.subTasks.mapIndexed { index, st ->
                    TaskItem.SubTask(
                        id = index + 1,
                        iconRes = com.movodream.localguru.R.drawable.ic_circle,
                        text = st.text
                    )
                }
            )
        }

        _tasks.value = mappedTasks
        _filteredTasks.value = mappedTasks
        filterDashboardTasks()
    }

    // --------------------------
    // 🔍 Filters
    // --------------------------
    fun filterTasks(tab: String) {
        val all = _tasks.value ?: return
        _filteredTasks.value = when (tab) {
            AppConstants.TAB_PENDING -> all.filter { it.taskStatus == AppConstants.TAB_PENDING }
            AppConstants.TAB_COMPLETED -> all.filter { it.taskStatus == AppConstants.TAB_COMPLETED }
            AppConstants.TAB_REVISION -> all.filter { it.taskStatus == AppConstants.TAB_REVISION }
            AppConstants.TAB_IN_PROGRESS -> all.filter { it.taskStatus == AppConstants.TAB_IN_PROGRESS }
            else -> all
        }
    }

    fun filterDashboardTasks() {
        val all = _tasks.value ?: return
        _dashboardTasks.value = all.filter { it.taskStatus != AppConstants.TAB_COMPLETED }
    }

    fun onClickViewAll() {
        _selectedTab.value = 1
    }

    // --------------------------
    // Category Actions
    // --------------------------
    private val _categoryState = MutableLiveData<CategoryResult?>()
    val categoryState: LiveData<CategoryResult?> get() = _categoryState

    private val _categoryCaller = MutableLiveData<String?>()
    val categoryCaller: LiveData<String?> = _categoryCaller

    fun setCaller(tag: String) = run { _categoryCaller.value = tag }
    fun clearCaller() = run { _categoryCaller.value = null }

    fun loadCategory(categoryId: String) {
        if (categoryId.isBlank()) {
            _categoryState.value = CategoryResult.Error("Invalid category id")
            return
        }

        _categoryState.value = CategoryResult.Loading

        viewModelScope.launch {
            val result = repository.getCategoryDetails(categoryId)
            _categoryState.postValue(result)
        }
    }

    fun resetCategoryState() {
        _categoryState.value = null
    }

    fun callAgentDashboardAPI(agentId : String) {
        viewModelScope.launch(Utils.coroutineContext) {
            dashboardResponse.value = ResponseHandler.Loading
            dashboardResponse.value = repository.merchantDashboard(agentId)
        }
    }

    fun callRevisionDataAPI(agentId : String,poiId : String) {
        viewModelScope.launch(Utils.coroutineContext) {
            revisionDataResponse.value = ResponseHandler.Loading
            revisionDataResponse.value = repository.revisionData(agentId,poiId)
        }
    }

    fun callPOIDetails(agentId : String,poiId : String) {
        viewModelScope.launch(Utils.coroutineContext) {
            poiDataResponse.value = ResponseHandler.Loading
            poiDataResponse.value = repository.revisionData(agentId,poiId)
        }
    }

    fun fetchAccurateLocation() {




        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .build()

        try {
            fusedClient.getCurrentLocation(request, null)
                .addOnSuccessListener { loc ->
                    if (loc != null && loc.longitude!=null) {
                        MyPreference.setValueString(PrefKey.LATITUDE,""+loc.latitude)
                        MyPreference.setValueString(PrefKey.LONGITUDE,""+loc.longitude)
                    } else {

                    }
                }
                .addOnFailureListener {

                }
        } catch (e: SecurityException) {
            // permission not available

        }
    }

    fun saveServerPoiDetailsAsDraft(
        poiId: String,
        poiDetailsString: String,galleryPhotos : String,   // <-- now STRING
        onSaved: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {

            try {
                // 1️⃣ Parse server string into JSON object
                val poiDetails = JSONObject(poiDetailsString)

                // 2️⃣ Build draft root object
                val root = JSONObject()
                val valuesObj = JSONObject()

                poiDetails.keys().forEach { key ->
                    valuesObj.put(key, poiDetails.get(key))
                }
                // 3️⃣ Special rename → backend sends "GalleryPhotos", dynamic form uses "galleryPhotos"
                if (!galleryPhotos.isNullOrEmpty() && galleryPhotos.startsWith("[")) {

                    val parsed = JSONArray(galleryPhotos)   // Convert string → JSONArray

                    val pipeArray = JSONArray()

                    for (i in 0 until parsed.length()) {
                        val obj = parsed.getJSONObject(i)

                        val id = obj.optString("img_id")           // safe extraction
                        val url = obj.optString("img_url")

                        if (url.isNotEmpty()) pipeArray.put("$url|$id")
                    }

                    valuesObj.put("galleryPhotos", pipeArray)      // 🔥 correct dynamic field key
                }


                root.put("values", valuesObj)

                val entity = DraftEntity(
                    poiId = poiId,
                    formId = "",
                    draftJson = root.toString(),
                    updatedAt = 0
                )

                draftRepository.saveDraft(entity)

                withContext(Dispatchers.Main) { onSaved() }

            } catch (ex: Exception) {
                ex.printStackTrace()
                withContext(Dispatchers.Main) { onSaved() }
            }
        }
    }

    fun parseGalleryPhotoString(raw: Any?): MutableList<String> {
        if (raw !is String || raw.isBlank()) return mutableListOf()

        return raw
            .trim()                       // remove leading/trailing spaces
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")                    // split into URLs
            .mapNotNull { it.trim() }
            .filter { it.startsWith("http", ignoreCase = true) }
            .toMutableList()
    }

    fun isPoiDraftAvailable(poiId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val exists = draftRepository.hasDraft(poiId)
            withContext(Dispatchers.Main) {
                callback(exists)
            }
        }
    }



    private val _shareText = SingleLiveEvent<String>()
    val shareText: LiveData<String> get() = _shareText

    fun prepareShareText(poiRaw: String) {
        viewModelScope.launch(Dispatchers.Default) {

            val json = parseJsonSafely(poiRaw)
            val builder = StringBuilder()

            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key)

                // Skip unwanted backend fields
                if (key.equals("PoiId", true) ||
                    key.equals("AgentId", true) ||
                    key.equals("movodreamId", true) ||
                    key.equals("galleryPhotos", true)) continue

                when (value) {

                    is String -> if (value.isNotBlank()) {
                        builder.append("${keyToLabel(key)} : ${formatValue(value)}\n\n")
                    }

                    is Number, is Boolean -> {
                        builder.append("${keyToLabel(key)} : $value\n\n")
                    }

                    is JSONArray -> if (value.length() > 0) {
                        val items = (0 until value.length()).map { idx ->
                            formatValue(value.optString(idx))
                        }
                        builder.append("${keyToLabel(key)} :\n${items.joinToString("\n") { "• $it" }}\n\n")
                    }
                }
            }

            _shareText.postValue(builder.toString()) // no trim — preserves last lines
        }
    }

    // ---------------------------------------------------------------------
    // SAFE JSON PARSER
    // ---------------------------------------------------------------------
    private fun parseJsonSafely(raw: String): JSONObject {
        return try { JSONObject(raw) }
        catch (_: Exception) { JSONObject(raw.replace("\\", "")) }
    }

    // ---------------------------------------------------------------------
    // KEY TO LABEL (CamelCase / snake_case -> Normal Title Case)
    // ---------------------------------------------------------------------
    private fun keyToLabel(key: String): String {
        return key.replace("_", " ")
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") // aaaBbb -> aaa Bbb
            .split(" ", "-")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.titlecase() } }
    }

    // ---------------------------------------------------------------------
    // VALUE FORMATTER + ENCODING NORMALIZATION
    // ---------------------------------------------------------------------
    private fun formatValue(raw: String): String {
        if (raw.isBlank()) return raw

        // 1) FIX ENCODING BEFORE ANYTHING ELSE
        val fixed = normalizeEncoding(raw)

        // 2) URLs & Email remain unchanged
        if (fixed.contains("://") ||
            fixed.contains("@") ||
            fixed.startsWith("www.", true)) return fixed

        val abbreviations = setOf("qr", "upi", "nfc", "gps", "atm")
        val text = fixed.replace("_", " ").lowercase()

        return text.split(" ").mapIndexed { index, word ->
            when {
                abbreviations.contains(word) -> word.uppercase()
                index == 0 -> word.replaceFirstChar { it.titlecase() }
                else -> word
            }
        }.joinToString(" ")
    }

    // ---------------------------------------------------------------------
    // THE TRUE FIX – DOUBLE ENCODING NORMALIZER
    // ---------------------------------------------------------------------
    private fun normalizeEncoding(text: String): String {
        var value = text
        repeat(2) {
            value = try {
                String(value.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
            } catch (_: Exception) {
                return cleanUnknown(text)
            }
        }
        return cleanUnknown(value)
    }

    private fun cleanUnknown(text: String): String {
        return text
            .replace("\uFFFD", "") // remove replacement char �
            .replace("�", "")      // visual form
            .replace(Regex("[\\p{C}]"), "") // remove all non-printable control chars
    }

}
