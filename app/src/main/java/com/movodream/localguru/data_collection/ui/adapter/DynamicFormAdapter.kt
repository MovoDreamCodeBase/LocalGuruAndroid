package com.movodream.localguru.data_collection.ui.adapter


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.core.utils.SimpleTextWatcher
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.FieldSchema
import com.movodream.localguru.data_collection.presentation.FormViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.text.contains
import kotlin.text.substringBefore
import androidx.core.widget.doAfterTextChanged
import org.w3c.dom.Text

class DynamicFormAdapter(
    private val onTakePhoto: (fieldId: String) -> Unit,
    private val onPickImages: (fieldId: String) -> Unit,
    private val onRequestLocation: (latFieldId: String, lngFieldId: String) -> Unit,
    private val onFieldChanged: (fieldId: String, value: Any?) -> Unit,
    private val onRemovePhoto: (fieldId: String, uri: String) -> Unit,
    private val onAddNotification: () -> Unit,
    private val onRemoveNotification: (index: Int) -> Unit,
    private val onPreviewPhotos: (fieldId: String, uris: MutableList<Uri>) -> Unit,
    private val onAddSubPoi: () -> Unit,
    private val onRemoveSubPoi: (index: Int) -> Unit,
    private val onOperationalHoursChanged: (
        List<FormViewModel.OperationalDay>,
        FormViewModel.WeekDay
    ) -> Unit,
    private val onAddHoliday: () -> Unit,
    private val onAddSeasonal: () -> Unit,
    private val onRemoveHoliday: (index: Int) -> Unit,
    private val onRemoveSeason: (index: Int) -> Unit,
    private val onRequestRebind: (String) -> Unit



) : RecyclerView.Adapter<DynamicFormAdapter.FieldVH>() {

    private var fields: List<FieldSchema> = emptyList()
    private var values: Map<String, Any?> = emptyMap()
    private var errors: Map<String, String> = emptyMap()

    // keep stable ids to reduce rebinding jumps
    override fun getItemId(position: Int): Long = fields[position].id.hashCode().toLong()
    override fun setHasStableIds(hasStableIds: Boolean) { super.setHasStableIds(true) }

    fun submitFields(fields: List<FieldSchema>, values: Map<String, Any?>) {
        this.fields = fields
        this.values = values
        notifyDataSetChanged()
    }

    fun setErrors(errors: Map<String, String>) {
        this.errors = errors
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_field_container, parent, false)
        return FieldVH(v, onTakePhoto, onPickImages, onRequestLocation, onFieldChanged,onRemovePhoto,onAddNotification,onRemoveNotification,onPreviewPhotos,onAddSubPoi,onRemoveSubPoi,onOperationalHoursChanged,onAddHoliday,onAddSeasonal,onRemoveHoliday,onRemoveSeason,onRequestRebind)
    }

    override fun onBindViewHolder(holder: FieldVH, position: Int) {
        holder.allValues = values
        holder.bind(fields[position], values[fields[position].id], errors[fields[position].id])
    }

    override fun getItemCount(): Int = fields.size

    class FieldVH(
        itemView: View,
        private val onTakePhoto: (fieldId: String) -> Unit,
        private val onPickImages: (fieldId: String) -> Unit,
        private val onRequestLocation: (latFieldId: String, lngFieldId: String) -> Unit,
        private val onFieldChanged: (fieldId: String, value: Any?) -> Unit,
        private val onRemovePhoto: (fieldId: String, uri: String) -> Unit,
        private val onAddNotification: () -> Unit,
        private val onRemoveNotification: (index: Int) -> Unit,
        private val onPreviewPhotos: (fieldId: String, uris: MutableList<Uri>) -> Unit,
        private val onAddSubPoi: () -> Unit,
        private val onRemoveSubPoi: (index: Int) -> Unit,
        private val onOperationalHoursChanged: (
            List<FormViewModel.OperationalDay>,
            FormViewModel.WeekDay
        ) -> Unit,
        private val onAddHoliday: () -> Unit,
        private val onAddSeasonal: () -> Unit,
        private val onRemoveHoliday: (index: Int) -> Unit,
        private val onRemoveSeason: (index: Int) -> Unit,
        private val onRequestRebind: (String) -> Unit




    ) : RecyclerView.ViewHolder(itemView) {
        var allValues: Map<String, Any?> = emptyMap()

        private val label = itemView.findViewById<TextView>(R.id.label)
        private val host = itemView.findViewById<FrameLayout>(R.id.control_host)
        private val instructions = itemView.findViewById<TextView>(R.id.instructions)
        private val errorTv = itemView.findViewById<TextView>(R.id.error)

        // store active watcher to remove before re-adding
        private var activeWatcher: SimpleTextWatcher? = null

        private fun makeReadOnly(view: EditText) {
            view.isEnabled = false
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            //  view.setTextColor(Color.GRAY)
        }
        private fun formatSlots(slots: List<FormViewModel.TimeSlot>): String {
            return slots.joinToString(separator = "\n") { slot ->
                "${fmt(slot.open)} – ${fmt(slot.close)}"
            }
        }
        private fun fmt(min: Int): String {
            val h = min / 60
            val m = min % 60
            val ampm = if (h < 12) "am" else "pm"
            val hh = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            return "$hh:${m.toString().padStart(2,'0')} $ampm"
        }

        private fun showDatePicker(ctx: Context, cb: (String) -> Unit) {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                ctx,
                { _, y, m, d -> cb("%04d-%02d-%02d".format(y, m + 1, d)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        private fun showTimePicker(ctx: Context, cb: (String) -> Unit) {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .build()

            picker.addOnPositiveButtonClickListener {
                cb(String.format("%02d:%02d", picker.hour, picker.minute))
            }

            picker.show((ctx as AppCompatActivity).supportFragmentManager, "TIME_PICKER")
        }

        private fun renderCustomMetadata(
            ctx: Context,
            host: FrameLayout,
            value: Any?
        ) {
            host.removeAllViews()

            val list = (value as? MutableList<FormViewModel.CustomMetadata>)
                ?: mutableListOf<FormViewModel.CustomMetadata>().also {
                    onFieldChanged("customMetadata", it)
                }

            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            list.forEachIndexed { index, item ->
                container.addView(
                    createMetadataCard(
                        ctx = ctx,
                        metadata = item,
                        onDelete = {
                            list.removeAt(index)
                            onFieldChanged("customMetadata", list)
                            onRequestRebind("customMetadata") // ✅ re-render after delete
                        },
                        onChanged = {
                            list[index] = it
                            onFieldChanged("customMetadata", list)
                        }
                    )
                )
            }

            //  ADD BUTTON
            val addBtn = TextView(ctx).apply {
                text = "+ Add Custom Metadata Field"
                textSize = 13f
                setPadding(24, 12, 24, 12)
                background = ContextCompat.getDrawable(ctx, com.core.R.drawable.bg_button_background)
                setTextColor(Color.WHITE)
                typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 12.dpToPx(ctx)
                layoutParams = params

                setOnClickListener {
                    list.add(FormViewModel.CustomMetadata())
                    onFieldChanged("customMetadata", list)
                    onRequestRebind("customMetadata") // ✅ required
                }
            }

            container.addView(addBtn)
            host.addView(container)
        }
        private fun createMetadataCard(
            ctx: Context,
            metadata: FormViewModel.CustomMetadata,
            onDelete: () -> Unit,
            onChanged: (FormViewModel.CustomMetadata) -> Unit
        ): View {

            val root = LayoutInflater.from(ctx)
                .inflate(R.layout.item_custom_metadata, null, false)

            val etLabel = root.findViewById<EditText>(R.id.etLabel)
            val tvTitle = root.findViewById<TextView>(R.id.tvTitle)
            val spType = root.findViewById<Spinner>(R.id.spType)
            val valueContainer = root.findViewById<FrameLayout>(R.id.valueContainer)
            val btnDelete = root.findViewById<ImageView>(R.id.btnDelete)

            etLabel.setText(metadata.label)
            tvTitle.text = metadata.label
                ?.takeIf { it.isNotBlank() }
                ?: "New Field"

            etLabel.doAfterTextChanged {
                metadata.label = it?.toString().orEmpty()
                onChanged(metadata)
            }

            btnDelete.setOnClickListener { onDelete() }

            // Spinner
            val types = FormViewModel.MetadataType.values()
                .map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }

            spType.adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_dropdown_item,
                types
            )

            spType.setSelection(metadata.type.ordinal)

            fun renderValueField() {
                valueContainer.removeAllViews()

                val view = when (metadata.type) {

                    FormViewModel.MetadataType.TEXT ->
                        EditText(ctx).apply {
                            background = context.getDrawable(R.drawable.bg_input_bordered)
                            setPadding(24, 36, 24, 36)
                            hint = "Enter field value"
                            setText(metadata.value)
                            doAfterTextChanged {
                                metadata.value = it?.toString().orEmpty()

                                onChanged(metadata)
                            }
                        }

                    FormViewModel.MetadataType.NUMBER ->
                        EditText(ctx).apply {
                            hint = "Enter field value"
                            inputType = InputType.TYPE_CLASS_NUMBER
                            background = context.getDrawable(R.drawable.bg_input_bordered)
                            setPadding(24, 36, 24, 36)
                            setText(metadata.value)
                            doAfterTextChanged {
                                metadata.value =
                                    it?.toString()?.filter(Char::isDigit).orEmpty()
                                onChanged(metadata)
                            }
                        }

                    FormViewModel.MetadataType.DATE ->
                        TextView(ctx).apply {
                            text =
                                if (metadata.value.isBlank()) "Select date" else metadata.value

                            setPadding(24, 48, 24, 48)
                            background =
                                ContextCompat.getDrawable(ctx, R.drawable.bg_input_bordered)

                            setOnClickListener {
                                showDatePicker(ctx) {
                                    metadata.value = it
                                    text = it
                                    onChanged(metadata)
                                }
                            }
                        }

                    FormViewModel.MetadataType.TIME ->
                        TextView(ctx).apply {
                            text =
                                if (metadata.value.isBlank()) "Select time" else metadata.value
                            setPadding(24, 48, 24, 48)
                            background =
                                ContextCompat.getDrawable(ctx, R.drawable.bg_input_bordered)

                            setOnClickListener {
                                showTimePicker(ctx) {
                                    metadata.value = it
                                    text = it
                                    onChanged(metadata)
                                }
                            }
                        }
                }

                valueContainer.addView(view)
            }

            renderValueField()

            spType.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val newType = FormViewModel.MetadataType.values()[position]
                        if (metadata.type != newType) {
                            metadata.type = newType
                            metadata.value = ""
                            onChanged(metadata)
                            renderValueField()
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }

            return root
        }


        fun bind(field: FieldSchema, value: Any?, error: String?) {
            if (field.required) {
                val full = "${field.label} *"
                val spannable = SpannableString(full)
                spannable.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            itemView.context,
                            com.core.R.color.colorRed
                        )
                    ),
                    field.label.length, full.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                label.text = spannable
            } else {
                label.text = field.label
            }
            label.typeface = ResourcesCompat.getFont(itemView.context, com.core.R.font.dm_sans_bold)
            instructions.visibility = if (!field.instructions.isNullOrEmpty()) { instructions.text = field.instructions; View.GONE } else View.GONE
            errorTv.visibility = if (!error.isNullOrEmpty()) { errorTv.text = error; View.VISIBLE } else View.GONE
            val nonEditableLocationFields = setOf(
                "latitude", "longitude", "localityTown", "regionState", "country"
            )

            val lat = allValues["latitude"]?.toString()?.toDoubleOrNull() ?: 0.0
            val lng = allValues["longitude"]?.toString()?.toDoubleOrNull() ?: 0.0

            val shouldLockLocation = lat != 0.0 && lng != 0.0
            host.removeAllViews()
            activeWatcher = null

            when (field.type) {
                "text" -> {
                    var et = EditText(itemView.context).apply {
                        background = context.getDrawable(R.drawable.bg_input_bordered)
                        setPadding(24, 36, 24, 36)
                        textSize = 15f
                        hint = field.placeholder ?: field.label
                        setText(value as? String ?: "")
                        inputType = InputType.TYPE_CLASS_TEXT
                        typeface = ResourcesCompat.getFont(itemView.context, com.core.R.font.dm_sans_medium)
                        setTextColor(context.getColor(android.R.color.black))
                        setHintTextColor(Color.parseColor("#9E9E9E"))


                    }
                    // 🔒 Make read-only only when data exists
                    if (field.id == "localityTown" ||
                        field.id == "regionState" ||
                        field.id == "country"||field.id == "siteName"||field.id == "collectorId") {

                        val currentVal = (value as? String)?.trim().orEmpty()

                        if (currentVal.isNotEmpty()) {
                            makeReadOnly(et)
                        }
                    }



                    et.addTextChangedListener(SimpleTextWatcher { onFieldChanged(field.id, it) })
                    activeWatcher = null

                    // Keep focus behavior smooth
                    et.setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            v.post {
                                (itemView.parent as? RecyclerView)?.let { rv ->
                                    val pos = bindingAdapterPosition
                                    if (pos != RecyclerView.NO_POSITION) rv.smoothScrollToPosition(pos)
                                }
                            }
                        }
                    }

                    host.addView(et)
                }

                "textarea" -> {
                    val et = EditText(itemView.context).apply {
                        background = context.getDrawable(R.drawable.bg_input_bordered)
                        setPadding(24, 36, 24, 36)
                        textSize = 15f
                        hint = field.placeholder ?: field.label
                        setText(value as? String ?: "")
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        minLines = 3
                        gravity = Gravity.TOP
                        typeface = ResourcesCompat.getFont(itemView.context, com.core.R.font.dm_sans_medium)
                        setTextColor(context.getColor(android.R.color.black))
                        setHintTextColor(Color.parseColor("#9E9E9E"))
                    }

                    et.addTextChangedListener(SimpleTextWatcher { onFieldChanged(field.id, it) })
                    activeWatcher = null
                    host.addView(et)
                }

                "number" -> {
                    val ctx = itemView.context

                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // -------- Number Input Field --------
                    val et = EditText(ctx).apply {
                        background = ctx.getDrawable(R.drawable.bg_input_bordered)
                        setPadding(24, 36, 24, 36)
                        textSize = 15f
                        setText(value?.toString() ?: "")
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                        setTextColor(ctx.getColor(android.R.color.black))
                        setHintTextColor(Color.parseColor("#9E9E9E"))
                    }

                    et.addTextChangedListener(SimpleTextWatcher { s ->
                        onFieldChanged(field.id, s.toDoubleOrNull())
                    })

                    container.addView(et)

                    // -------- Show ONE button only after longitude --------
                    if (field.id == "longitude" || field.id == "subPoiLongitude") {

                        val latFieldId = if (field.id == "longitude") {
                            "latitude"
                        } else {
                            "subPoiLatitude"
                        }

                        val lngFieldId = field.id

                        val btn = TextView(ctx).apply {
                            text = "Fetch Location"
                            textSize = 13f
                            setPadding(24, 12, 24, 12)
                            background = ContextCompat.getDrawable(ctx, com.core.R.drawable.bg_button_background)
                            setTextColor(Color.WHITE)
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)

                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            params.topMargin = 12.dpToPx(ctx)
                            layoutParams = params
                        }

                        btn.setOnClickListener {
                            onRequestLocation(latFieldId, lngFieldId)
                        }

                        container.addView(btn)
                    }
// ---- Make LAT/LNG read-only when map returns values ----
                    if (shouldLockLocation && field.id in nonEditableLocationFields) {
                        et.isEnabled = false
                        et.isFocusable = false
                        et.isFocusableInTouchMode = false
                        et.isClickable = false
                        // et.alpha = 0.6f   // Optional: visually show disabled state
                    }
                    host.addView(container)
                }




                "select" -> {
                    val ctx = itemView.context
                    val wrapper = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // ---------- Spinner Container ----------
                    val spinnerWrapper = RelativeLayout(ctx)

                    val spinner = Spinner(ctx, Spinner.MODE_DROPDOWN).apply {
                        id = View.generateViewId()
                        background = ctx.getDrawable(R.drawable.bg_input_bordered)
                        setPadding(24, 6, 56, 6)
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        layoutParams = RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val arrow = ImageView(ctx).apply {
                        setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_arrow_down))
                        layoutParams = RelativeLayout.LayoutParams(
                            24.dpToPx(ctx),
                            24.dpToPx(ctx)
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_END)
                            addRule(RelativeLayout.CENTER_VERTICAL)
                            marginEnd = 8.dpToPx(ctx)
                        }
                        isClickable = false
                        isFocusable = false
                    }

                    spinnerWrapper.addView(spinner)
                    spinnerWrapper.addView(arrow)
                    wrapper.addView(spinnerWrapper)

                    // ---------- Options ----------
                    val labels = mutableListOf("Select")
                    val values = mutableListOf("")

                    field.options.forEach {
                        labels.add(it.label)
                        values.add(it.value)
                    }

                    val hasOtherOption = values.any { it.equals("Other", true) }
                    val otherFieldId = "${field.id}__other"

                    val spinnerAdapter = object : ArrayAdapter<String>(
                        ctx,
                        R.layout.item_spinner_text,
                        labels
                    ) {
                        override fun isEnabled(position: Int): Boolean {
                            return !(field.required && position == 0)
                        }

                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = convertView ?: LayoutInflater.from(ctx)
                                .inflate(R.layout.item_spinner_dropdown, parent, false)
                            val tv = v.findViewById<TextView>(R.id.spinner_dropdown_text)
                            tv.text = labels[position]
                            tv.typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                            tv.setTextColor(if (position == 0) Color.GRAY else Color.BLACK)
                            return v
                        }
                    }

                    spinner.adapter = spinnerAdapter

                    // ---------- Other Text Field ----------
                    val otherEditText = EditText(ctx).apply {
                        tag = otherFieldId
                        hint = "Enter ${field.label}"
                        background = ctx.getDrawable(R.drawable.bg_input_bordered)
                        setPadding(24, 36, 24, 36)
                        visibility = View.GONE
                        inputType = InputType.TYPE_CLASS_TEXT
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                        setText(allValues[otherFieldId] as? String ?: "")

                        addTextChangedListener(SimpleTextWatcher {
                            onFieldChanged(otherFieldId, it)
                        })
                    }

                    wrapper.addView(otherEditText)

                    // ---------- Preselect ----------
                    val currentValue = (value as? String)?.takeIf { it.isNotBlank() }
                    val selectedIndex = if (currentValue != null) values.indexOf(currentValue) else 0
                    spinner.setSelection(if (selectedIndex >= 0) selectedIndex else 0)

                    if (hasOtherOption && currentValue.equals("Other", true)) {
                        otherEditText.visibility = View.VISIBLE
                    }

                    // ---------- Selection Listener ----------
                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            val selectedValue = if (position == 0) "" else values[position]

                            onFieldChanged(field.id, selectedValue)

                            if (!hasOtherOption) return

                            if (selectedValue.equals("Other", true)) {
                                otherEditText.visibility = View.VISIBLE
                            } else {
                                // 🔥 Hide + clear Other value
                                otherEditText.visibility = View.GONE
                                onFieldChanged(otherFieldId, "")
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }

                    host.addView(wrapper)
                }



                "checkbox_group" -> {
                    val ctx = itemView.context

                    // Create a GridLayout for 2-column checkboxes
                    val grid = GridLayout(ctx).apply {
                        columnCount = 2
                        orientation = GridLayout.HORIZONTAL
                        useDefaultMargins = true
                    }

                    val selected = (value as? List<*>)?.map { it.toString() }?.toMutableSet() ?: mutableSetOf()

                    field.options.forEachIndexed { index, opt ->
                        val cb = CheckBox(ctx).apply {
                            text = opt.label
                            isChecked = selected.contains(opt.value)
                            textSize = 13f
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                            setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
                            buttonTintList = ContextCompat.getColorStateList(ctx, R.color.checkbox_tint)
                            setPadding(8, 8, 8, 8)
                        }

                        val params = GridLayout.LayoutParams(
                            GridLayout.spec(index / 2, 1f),
                            GridLayout.spec(index % 2, 1f)
                        ).apply {
                            width = 0
                            setMargins(8, 4, 8, 4)
                        }

                        cb.layoutParams = params

                        cb.setOnCheckedChangeListener { _, checked ->
                            if (checked) selected.add(opt.value) else selected.remove(opt.value)
                            onFieldChanged(field.id, selected.toList())
                        }

                        grid.addView(cb)
                    }

                    host.addView(grid)
                }

                "photo_list" -> {
                    val ctx = itemView.context
                    val inflater = LayoutInflater.from(ctx)


                    // Root container
                    val vertical = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // Upload dashed box (clickable)
                    val uploadBox = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        background = ContextCompat.getDrawable(ctx, R.drawable.bg_upload_dashed)
                        setPadding(24, 36, 24, 36)
                        isClickable = true
                        isFocusable = true
                    }

                    val icon = ImageView(ctx).apply {
                        setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_cloud_upload))
                        layoutParams = LinearLayout.LayoutParams(70, 70)
                    }

                    val title = TextView(ctx).apply {
                        text = "Click to upload photos or drag\nand drop"
                        setTextColor(ContextCompat.getColor(ctx, com.core.R.color.colorDarkGrey))
                        textSize = 14f
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                        gravity = Gravity.CENTER
                    }

                    val subtitle = TextView(ctx).apply {
                        if (!field.instructions.isNullOrEmpty())
                        { text= field.instructions  }else {
                            text = "Minimum ${field.minItems} photos required"
                        }
                        setTextColor(Color.parseColor("#9E9E9E"))
                        textSize = 12f
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_regular)
                        gravity = Gravity.CENTER
                        setPadding(0, 8, 0, 0)
                    }


                    uploadBox.addView(icon)
                    uploadBox.addView(title)
                    uploadBox.addView(subtitle)
                    vertical.addView(uploadBox)

                    // List of uploaded photos
                    val photoList = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8, 16, 8, 8)
                    }

                    vertical.addView(photoList)

                    // Load existing photo URIs
                    // Load existing values (could be server URLs or saved draft strings)
                    val serverList = (value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

// Convert to Uri for UI
                    val uris = serverList.map { Uri.parse(it) }.toMutableList()


                    fun refreshPhotoList() {
                        photoList.removeAllViews()
                        uris.forEachIndexed { index, uri ->
                            val row = LinearLayout(ctx).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(8, 12, 8, 12)
                            }

                            val fileIcon = ImageView(ctx).apply {
                                setImageResource(android.R.drawable.ic_menu_camera)
                                setColorFilter(ContextCompat.getColor(ctx, com.core.R.color.colorSkyBlue))
                                layoutParams = LinearLayout.LayoutParams(40, 40)
                            }

                            val raw = uri.toString()
                            val url = if (raw.contains("|")) raw.substringBefore("|") else raw
                            val fileN = url.substringAfterLast('/').substringBefore('?')
                            val fileName = TextView(ctx).apply {
                                text = fileN
                                setTextColor(Color.BLACK)
                                textSize = 14f
                                typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                setPadding(12, 0, 0, 0)
                            }

                            val deleteBtn = ImageView(ctx).apply {
                                setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_delete))
                                layoutParams = LinearLayout.LayoutParams(40, 40)

                                setOnClickListener {
                                    val uriStr = uri.toString()

                                    // Delegate removal ONLY
                                    onRemovePhoto(field.id, uriStr)


                                }
                            }



                            row.addView(fileIcon)
                            row.addView(fileName)
                            row.addView(deleteBtn)
                            photoList.addView(row)
                        }
                    }

                    refreshPhotoList()

                    uploadBox.setOnClickListener {
                        // Trigger the gallery photo picker
                        onPickImages(field.id)
                    }

                    //  ADDED — "SHOW PHOTOS" BUTTON
                    val showBtn = TextView(ctx).apply {
                        text = "Show Photos"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        background = ContextCompat.getDrawable(ctx, com.core.R.drawable.bg_button_background)
                        setPadding(24, 12, 24, 12)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                        visibility = if (uris.isEmpty()) View.GONE else View.VISIBLE
                        setOnClickListener {
                            // ViewModel holds uris, so just show dialog
                            onPreviewPhotos(field.id, uris)  // ✅ pass uris
                        }
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.topMargin = 12.dpToPx(ctx)
                        layoutParams = params
                    }

                    vertical.addView(showBtn)

                    host.addView(vertical)
                }


                "rating" -> {
                    val ctx = itemView.context
                    val maxStars = field.max?.toInt() ?: 5

                    var currentRating = (value as? Number)?.toInt() ?: 0

                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(16.dpToPx(ctx), 1.dpToPx(ctx), 16.dpToPx(ctx), 1.dpToPx(ctx))
                    }

                    val starsLayout = com.google.android.flexbox.FlexboxLayout(ctx).apply {
                        flexWrap = com.google.android.flexbox.FlexWrap.WRAP
                        flexDirection = com.google.android.flexbox.FlexDirection.ROW
                        justifyContent = com.google.android.flexbox.JustifyContent.FLEX_START
                    }

                    val starSize = if (maxStars > 5) 24.dpToPx(ctx) else 32.dpToPx(ctx)
                    val starMargin = if (maxStars > 5) 4.dpToPx(ctx) else 6.dpToPx(ctx)

                    val ratingText = TextView(ctx).apply {
                        text = "$currentRating / $maxStars"
                        setPadding(8.dpToPx(ctx), 0, 0, 0)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                        setTextColor(Color.BLACK)
                        textSize = 14f
                    }

                    fun refreshStars() {
                        for (i in 0 until maxStars) {
                            val starView = starsLayout.getChildAt(i) as ImageView
                            val drawableId =
                                if (i < currentRating) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                            starView.setImageDrawable(ContextCompat.getDrawable(ctx, drawableId))
                        }
                        ratingText.text = "$currentRating / $maxStars"
                    }

                    repeat(maxStars) { index ->
                        val star = ImageView(ctx).apply {
                            layoutParams = LinearLayout.LayoutParams(starSize, starSize).apply {
                                marginEnd = starMargin
                                bottomMargin = starMargin
                            }
                            setImageDrawable(
                                ContextCompat.getDrawable(
                                    ctx,
                                    if (index < currentRating) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                                )
                            )
                            setOnClickListener {
                                currentRating = when {
                                    // Allow reset ONLY when tapping first star again
                                    index == 0 && currentRating == 1 -> 0

                                    // Otherwise normal selection
                                    else -> index + 1
                                }
                                onFieldChanged(field.id, currentRating)
                                refreshStars()
                            }
                        }
                        starsLayout.addView(star)
                    }

                    refreshStars()

                    container.addView(starsLayout)
                    container.addView(ratingText)
                    host.addView(container)
                }

                "notification_list" -> {
                    val ctx = itemView.context
                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8, 8, 8, 8)
                    }

                    val list = (value as? List<Map<String, Any?>>) ?: emptyList()

                    // ----------------------------
                    // Render Notification Items
                    // ----------------------------
                    list.forEachIndexed { index, item ->

                        val card = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            background = ContextCompat.getDrawable(ctx, R.drawable.bg_input_bordered)
                            setPadding(20, 20, 20, 20)
                        }

                        val title = TextView(ctx).apply {
                            text = item["notificationCategories"]?.toString() ?: ""
                            setTextColor(Color.BLACK)
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_bold)
                            textSize = 16f
                        }

                        val desc = TextView(ctx).apply {
                            text = item["notificationLanguageAvailability"]?.toString() ?: ""
                            setTextColor(Color.BLACK)
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_regular)
                            textSize = 12f
                        }

//        val meta = TextView(ctx).apply {
//            val cat = item["category"] ?: ""
//            val active = item["active"] ?: ""
//            text = "Type: $cat | Active: $active"
//            setTextColor(Color.GRAY)
//            textSize = 12f
//        }

                        val deleteBtn = ImageView(ctx).apply {
                            setImageResource(R.drawable.ic_delete)
                            layoutParams = LinearLayout.LayoutParams(45, 45).apply {
                                topMargin = 10
                                gravity = Gravity.END
                            }
                            setOnClickListener { onRemoveNotification(index) }
                        }

                        card.addView(title)
                        card.addView(desc)
                        // card.addView(meta)
                        card.addView(deleteBtn)

                        val wrapper = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(0, 10, 0, 10)
                        }

                        wrapper.addView(card)
                        container.addView(wrapper)
                    }

                    // ------------------------------------------------------------
                    // 2) Add Notification Button
                    // ------------------------------------------------------------
                    val addBtn = TextView(ctx).apply {
                        text = "Add Notification"
                        textSize = 13f
                        setPadding(24, 12, 24, 12)
                        background = ContextCompat.getDrawable(ctx, com.core.R.drawable.bg_button_background)
                        setTextColor(Color.WHITE)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)

                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.topMargin = 12.dpToPx(ctx)
                        layoutParams = params
                    }

                    addBtn.setOnClickListener { onAddNotification() }

                    container.addView(addBtn)
                    host.addView(container)
                }

                "sub_poi_list" -> {
                    val ctx = itemView.context
                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8, 8, 8, 8)
                    }

                    val list = (value as? List<Map<String, Any?>>) ?: emptyList()

                    // Render Sub POIs
                    list.forEachIndexed { index, item ->

                        val card = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            background = ContextCompat.getDrawable(ctx, R.drawable.bg_input_bordered)
                            setPadding(20, 20, 20, 20)
                        }

                        val title = TextView(ctx).apply {
                            text = item["subPoiName"]?.toString().orEmpty()
                            setTextColor(Color.BLACK)
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_bold)
                            textSize = 16f
                        }

                        val deleteBtn = ImageView(ctx).apply {
                            setImageResource(R.drawable.ic_delete)
                            setOnClickListener { onRemoveSubPoi(index) }
                        }

                        card.addView(title)
                        card.addView(deleteBtn)

                        container.addView(card)
                    }

                    // Add Sub POI Button
                    val addBtn = TextView(ctx).apply {
                        text = "Add Sub POI"
                        textSize = 13f
                        setPadding(24, 12, 24, 12)
                        background = ContextCompat.getDrawable(ctx, com.core.R.drawable.bg_button_background)
                        setTextColor(Color.WHITE)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)

                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.topMargin = 12.dpToPx(ctx)
                        layoutParams = params

                    }
                    addBtn.setOnClickListener {
                        onAddSubPoi()
                    }


                    container.addView(addBtn)
                    host.addView(container)
                }
                "operational_hours" -> {
                    val ctx = itemView.context

                    val days = value as? List<FormViewModel.OperationalDay>
                        ?: return

                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8.dpToPx(ctx), 8.dpToPx(ctx), 8.dpToPx(ctx), 8.dpToPx(ctx))
                    }

                    days.forEach { day ->

                        val row = LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(8.dpToPx(ctx), 8.dpToPx(ctx), 8.dpToPx(ctx), 8.dpToPx(ctx))
                        }

                        // Day label
                        val dayTv = TextView(ctx).apply {
                            text = day.day.name.lowercase()
                                .replaceFirstChar { it.uppercase() }
                            textSize = 15f
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                            setTextColor(Color.BLACK)
                            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        }

                        // Status text
                        val statusTv = TextView(ctx).apply {
                            text = when {
                                day.isClosed -> "Closed"
                                day.isOpen24Hours -> "Open 24 hours"
                                day.slots.isNotEmpty() -> formatSlots(day.slots)
                                else -> "Closed"
                            }
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_regular)
                            textSize = 13f
                            setTextColor(Color.DKGRAY)
                            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        }

                        // Edit icon
                        val edit = ImageView(ctx).apply {
                            setImageResource(R.drawable.ic_edit_hours)
                            setOnClickListener {
                                // ✅ PASS CLICKED DAY
                                onOperationalHoursChanged(days, day.day)
                            }
                        }

                        row.addView(dayTv)
                        row.addView(statusTv)
                        row.addView(edit)

                        container.addView(row)
                    }

                    host.addView(container)
                }

                "holiday_list" -> {
                    val ctx = itemView.context
                    val list = (value as? List<FormViewModel.HolidayException>) ?: emptyList()

                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16.dpToPx(ctx), 8.dpToPx(ctx), 16.dpToPx(ctx), 8.dpToPx(ctx))
                    }

                    list.forEachIndexed { index, h ->
                        val row = LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = ContextCompat.getDrawable(ctx, R.drawable.bg_input_bordered)
                            setPadding(8.dpToPx(ctx), 8.dpToPx(ctx), 8.dpToPx(ctx), 8.dpToPx(ctx))
                        }

                        val title = TextView(ctx).apply {
                            text = h.name
                            textSize = 15f
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        }

                        val status = TextView(ctx).apply {
                            text = when {
                                h.isClosed -> "Closed"
                                h.isOpen24Hours -> "Open 24 hours"
                                h.slots.isNotEmpty() -> formatSlots(h.slots)
                                else -> "Closed"
                            }
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                            textSize = 13f
                            setTextColor(Color.DKGRAY)
                            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        }

                        val delete = ImageView(ctx).apply {
                            setImageResource(R.drawable.ic_delete)
                            setOnClickListener { onRemoveHoliday(index) }
                        }

                        row.addView(title)
                        row.addView(status)
                        row.addView(delete)
                        container.addView(row)
                    }

                    val addBtn = TextView(ctx).apply {
                        text = "+ Add Holiday"
                        textSize = 13f
                        setPadding(24, 12, 24, 12)
                        background = ContextCompat.getDrawable(ctx, com.core.R.drawable.bg_button_background)
                        setTextColor(Color.WHITE)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)

                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.topMargin = 12.dpToPx(ctx)
                        layoutParams = params
                        setOnClickListener { onAddHoliday() }

                    }



                    container.addView(addBtn)
                    host.addView(container)
                }

                "seasonal_list" -> {
                    val ctx = itemView.context
                    val list = (value as? List<FormViewModel.SeasonalHours>) ?: emptyList()

                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16.dpToPx(ctx), 8.dpToPx(ctx), 16.dpToPx(ctx), 8.dpToPx(ctx))
                    }

                    list.forEachIndexed { index, s ->
                        val row = LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = ContextCompat.getDrawable(ctx, R.drawable.bg_input_bordered)
                            setPadding(8.dpToPx(ctx), 8.dpToPx(ctx), 8.dpToPx(ctx), 8.dpToPx(ctx))
                        }

                        val title = TextView(ctx).apply {
                            text = s.name   // 🔥 instead of date range
                            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                        }


                        val status = TextView(ctx).apply {
                            text = when {
                                s.isClosed -> "Closed"
                                s.isOpen24Hours -> "Open 24 hours"
                                 s.slots.isNotEmpty() -> formatSlots(s.slots)
                                else -> "Closed"
                            }
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        }

                        val delete = ImageView(ctx).apply {
                            setImageResource(R.drawable.ic_delete)
                            setOnClickListener { onRemoveSeason(index) }
                        }

                        row.addView(title)
                        row.addView(status)
                        row.addView(delete)
                        container.addView(row)
                    }

                    val addBtn = TextView(ctx).apply {
                        text = "+ Add Season"
                        textSize = 13f
                        setPadding(24, 12, 24, 12)
                        background = ContextCompat.getDrawable(ctx, com.core.R.drawable.bg_button_background)
                        setTextColor(Color.WHITE)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)

                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.topMargin = 12.dpToPx(ctx)
                        layoutParams = params
                        setOnClickListener { onAddSeasonal() }
                    }



                    container.addView(addBtn)
                    host.addView(container)
                }



                "custom_metadata_list" -> {
                    val ctx = itemView.context
                    host.removeAllViews()

                    renderCustomMetadata(
                        ctx = ctx,
                        host = host,
                        value = value
                    )
                }

                // ======================================================
// TEXT LIST (Dos / Don’ts / Guidelines / Etiquettes)
// ======================================================
                "text_list" -> {

                    val ctx = itemView.context

                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // ---------------------------
// INPUT COLUMN (VERTICAL LAYOUT)
// ---------------------------
                    val inputColumn = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

// ---------------------------
// Text Input
// ---------------------------
                    val etInput = EditText(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 8.dpToPx(ctx)
                            bottomMargin = 8.dpToPx(ctx)
                        }

                        hint = field.placeholder ?: "Enter text"
                        background = ctx.getDrawable(R.drawable.bg_input_bordered)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_regular)
                        setSingleLine(true)
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        textSize = 14f
                        setPadding(24, 36, 24, 36)
                    }

// ---------------------------
// ADD BUTTON (below EditText)
// ---------------------------
                    val btnAdd = TextView(ctx).apply {
                        text = field.addButtonLabel ?: "Add"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(24, 12, 24, 12)
                        background = ContextCompat.getDrawable(ctx, com.core.R.drawable.bg_button_background)
                        setTextColor(Color.WHITE)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)

                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.END      // button aligns right
                            bottomMargin = 12.dpToPx(ctx)
                        }
                    }

// Add both to vertical parent
                    inputColumn.addView(etInput)
                    inputColumn.addView(btnAdd)


                    container.addView(inputColumn)

                    // ---------------------------
                    // Helper Text
                    // ---------------------------
                    if (!field.helperText.isNullOrBlank()) {
                        val helper = TextView(ctx).apply {
                            text = field.helperText
                            textSize = 12f
                            typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_bold)
                            setTextColor(Color.GRAY)
                            setPadding(0, 8, 0, 8)
                        }
                        container.addView(helper)
                    }

                    // ---------------------------
                    // List Container
                    // ---------------------------
                    val listContainer = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    container.addView(listContainer)

                    // ---------------------------
                    // Existing values
                    // ---------------------------
                    val items = (value as? List<*>)?.map { it.toString() }?.toMutableList()
                        ?: mutableListOf()

                    fun refreshList() {
                        listContainer.removeAllViews()

                        items.forEachIndexed { index, text ->

                            val row = LinearLayout(ctx).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(8, 12, 8, 12)
                            }

                            val tv = TextView(ctx).apply {
                                this.text = "• $text"
                                typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_regular)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                textSize = 14f
                            }

                            val delete = ImageView(ctx).apply {
                                setImageResource(R.drawable.ic_delete)
                                setOnClickListener {
                                    items.removeAt(index)
                                    onFieldChanged(field.id, items)
                                    refreshList()
                                }
                            }

                            row.addView(tv)
                            row.addView(delete)
                            listContainer.addView(row)
                        }
                    }

                    refreshList()

                    // ---------------------------
                    // Add logic
                    // ---------------------------
                    fun addItem() {
                        val text = etInput.text.toString().trim()
                        if (text.isEmpty()) return

                        items.add(text)
                        etInput.setText("")
                        onFieldChanged(field.id, items)
                        refreshList()
                    }

                    btnAdd.setOnClickListener { addItem() }

                    etInput.setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            addItem()
                            true
                        } else false
                    }

                    host.addView(container)
                }


                // existing cases...


                else -> {
                    val tv = TextView(itemView.context).apply { text = "Unsupported: ${field.type}" }
                    host.addView(tv)
                }
            }
        }
    }
    fun updateValue(fieldId: String, value: Any?) {
        val index = fields.indexOfFirst { it.id == fieldId }
        if (index != -1) notifyItemChanged(index)
    }
    fun getCurrentFields(): List<FieldSchema> = fields


}
fun Int.dpToPx(ctx: Context): Int =
    (this * ctx.resources.displayMetrics.density).toInt()

