package com.movodream.localguru.data_collection.model



data class FormSchema(
    val formId: String,
    val title: String,
    val progress: Int?,
    val tags: List<String>,
    val tabs: List<TabSchema>,

)

data class SubmitInfo(
    val label: String,
    val draftLabel: String?,
    val endpoint: String,
    val method: String
)

data class TabSchema(
    val id: String,
    val title: String,
    val order: Int,
    val fields: List<FieldSchema>
)

data class FieldSchema(
    val id: String,
    val type: String,
    val label: String,
    val placeholder: String? = null,
    val required: Boolean = false,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val precision: Int? = null,
    val options: List<Option> = emptyList(),
    val default: Any? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val minSelected: Int? = null,
    val captureRequired: Boolean? = null,
    val instructions: String? = null
)

data class Option(val value: String, val label: String)
