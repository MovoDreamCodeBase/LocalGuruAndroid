package com.movodream.localguru.model



import org.json.JSONArray
import org.json.JSONObject

object Parser {
    fun parseFormSchema(json: JSONObject): FormSchema {
        val formId = json.optString("form_id", "")
        val title = json.optString("title", "")
        val progress = if (json.has("progress")) json.optInt("progress") else null
        val tags = mutableListOf<String>().apply {
            val arr = json.optJSONArray("tags")
            if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i))
        }
        val tabs = mutableListOf<TabSchema>()
        val tabsArray = json.optJSONArray("tabs") ?: JSONArray()
        for (i in 0 until tabsArray.length()) {
            val tObj = tabsArray.optJSONObject(i) ?: continue
            tabs.add(parseTab(tObj))
        }
        val sb = json.optJSONObject("submit_button") ?: JSONObject()
        val submit = SubmitInfo(
            label = sb.optString("label", "Submit"),
            draftLabel = sb.optString("draft_label", "Save Draft"),
            endpoint = sb.optString("endpoint", ""),
            method = sb.optString("method", "POST")
        )
        return FormSchema(formId, title, progress, tags, tabs, submit)
    }

    private fun parseTab(obj: JSONObject): TabSchema {
        val id = obj.optString("id", "")
        val title = obj.optString("title", id)
        val order = obj.optInt("order", 0)
        val fields = mutableListOf<FieldSchema>()
        val arr = obj.optJSONArray("fields") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            fields.add(parseField(f))
        }
        return TabSchema(id, title, order, fields)
    }

    private fun parseField(f: JSONObject): FieldSchema {
        val opts = mutableListOf<Option>()
        val optArr = f.optJSONArray("options")
        if (optArr != null) {
            for (i in 0 until optArr.length()) {
                val o = optArr.optJSONObject(i) ?: continue
                opts.add(Option(o.optString("value"), o.optString("label")))
            }
        }
        return FieldSchema(
            id = f.optString("id"),
            type = f.optString("type"),
            label = f.optString("label", f.optString("id")),
            placeholder = f.optString("placeholder", null),
            required = f.optBoolean("required", false),
            minLength = f.optIntOrNull("minLength"),
            maxLength = f.optIntOrNull("maxLength"),
            pattern = f.optString("pattern", null),
            min = f.optDoubleOrNull("min"),
            max = f.optDoubleOrNull("max"),
            precision = f.optIntOrNull("precision"),
            options = opts,
            default = if (f.has("default")) f.get("default") else null,
            minItems = f.optIntOrNull("minItems"),
            maxItems = f.optIntOrNull("maxItems"),
            minSelected = f.optIntOrNull("minSelected"),
            captureRequired = f.optBooleanOrNull("capture_required"),
            instructions = f.optString("instructions", null)
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) optInt(key) else null
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key)) optDouble(key) else null
    private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (has(key)) optBoolean(key) else null
}
