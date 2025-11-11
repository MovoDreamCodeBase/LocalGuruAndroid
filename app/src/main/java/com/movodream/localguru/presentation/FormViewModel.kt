package com.movodream.localguru.presentation



import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.movodream.localguru.model.FormSchema
import com.movodream.localguru.model.Parser
import com.movodream.localguru.ui.activities.DynamicFormActivity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class FormViewModel(app: Application) : AndroidViewModel(app) {

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

    fun loadSchemaFromString(jsonStr: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val j = JSONObject(jsonStr)
            val parsed = Parser.parseFormSchema(j)
            schemaLive.postValue(parsed)
            val map = valuesLive.value ?: mutableMapOf()
            parsed.tabs.flatMap { it.fields }.forEach { if (!map.containsKey(it.id)) map[it.id] = it.default }
            valuesLive.postValue(map)
        }
    }

    fun updateValue(fieldId: String, value: Any?) {
        val m = valuesLive.value ?: mutableMapOf()
        m[fieldId] = value
        valuesLive.postValue(m)
    }

    fun addPhotoUri(fieldId: String, uri: Uri) {
        val m = valuesLive.value ?: mutableMapOf()
        val cur = (m[fieldId] as? MutableList<Uri>) ?: mutableListOf()
        cur.add(uri)
        m[fieldId] = cur
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
                if (f.minItems != null && (col == null || col.size < f.minItems!!))
                    errors[f.id] = "Requires at least ${f.minItems} photos"
            }
            f.minLength?.let { if (v is String && v.length < it) errors[f.id] = "${f.label} must be at least $it chars" }
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

    // Build full payload merging draftPerTab + current values
    fun buildPayload(): JSONObject {
        val schema = schemaLive.value ?: throw IllegalStateException("schema missing")
        val payload = JSONObject()
        payload.put("form_id", schema.formId)
        payload.put("submitted_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        val all = org.json.JSONObject()
        // merge drafts first then current values to ensure any unsaved fields included
        draftPerTab.value?.forEach { (_, tabMap) ->
            tabMap.forEach { (k, v) -> all.put(k, v.toString()) }
        }
        val cur = valuesLive.value ?: mapOf()
        cur.forEach { (k, v) ->
            when (v) {
                is Uri -> all.put(k, v.toString())
                is Collection<*> -> {
                    val arr = org.json.JSONArray()
                    v.forEach { arr.put(it.toString()) }
                    all.put(k, arr)
                }
                else -> all.put(k, v ?: org.json.JSONObject.NULL)
            }
        }
        payload.put("values", all)
        return payload
    }

    fun submit(payload: JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            // replace with actual network call; for now simulate
            Thread.sleep(400)
            formState.postValue(FormState.SubmitResult(true, "Submitted (mock)"))
        }
    }

    fun fetchLocation(activity: DynamicFormActivity, fieldId: String) {

    }

    // ✅ Add this for handling multiple gallery image selection
    fun addPhotoUris(fieldId: String, uris: List<Uri>) {
        val list = photoUris.getOrPut(fieldId) { mutableListOf() }
        list.addAll(uris)
        updateValue(fieldId, list.map { it.toString() })
        notifyFieldChanged(fieldId)
    }

    // ✅ (Optional) Allow user to remove a photo from the list
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
}
