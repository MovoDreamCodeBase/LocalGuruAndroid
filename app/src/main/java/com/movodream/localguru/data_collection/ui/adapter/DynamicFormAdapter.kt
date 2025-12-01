package com.movodream.localguru.data_collection.ui.adapter


import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.core.utils.SimpleTextWatcher
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.FieldSchema


class DynamicFormAdapter(
    private val onTakePhoto: (fieldId: String) -> Unit,
    private val onPickImages: (fieldId: String) -> Unit,
    private val onRequestLocation: (latFieldId: String, lngFieldId: String) -> Unit,
    private val onFieldChanged: (fieldId: String, value: Any?) -> Unit,
    private val onRemovePhoto: (fieldId: String, uri: Uri) -> Unit,
    private val onAddNotification: () -> Unit,
    private val onRemoveNotification: (index: Int) -> Unit
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
        return FieldVH(v, onTakePhoto, onPickImages, onRequestLocation, onFieldChanged,onRemovePhoto,onAddNotification,onRemoveNotification)
    }

    override fun onBindViewHolder(holder: FieldVH, position: Int) {
        holder.bind(fields[position], values[fields[position].id], errors[fields[position].id])
    }

    override fun getItemCount(): Int = fields.size

    class FieldVH(
        itemView: View,
        private val onTakePhoto: (fieldId: String) -> Unit,
        private val onPickImages: (fieldId: String) -> Unit,
        private val onRequestLocation: (latFieldId: String, lngFieldId: String) -> Unit,
        private val onFieldChanged: (fieldId: String, value: Any?) -> Unit,
        private val onRemovePhoto: (fieldId: String, uri: Uri) -> Unit,
        private val onAddNotification: () -> Unit,
        private val onRemoveNotification: (index: Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val label = itemView.findViewById<TextView>(R.id.label)
        private val host = itemView.findViewById<FrameLayout>(R.id.control_host)
        private val instructions = itemView.findViewById<TextView>(R.id.instructions)
        private val errorTv = itemView.findViewById<TextView>(R.id.error)

        // store active watcher to remove before re-adding
        private var activeWatcher: SimpleTextWatcher? = null

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

            host.removeAllViews()
            activeWatcher = null

            when (field.type) {
                "text" -> {
                    val et = EditText(itemView.context).apply {
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
                    if (field.id == "longitude") {

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
                            onRequestLocation("latitude", "longitude")
                        }

                        container.addView(btn)
                    }

                    host.addView(container)
                }




                "select" -> {
                    val ctx = itemView.context
                    val wrapper = RelativeLayout(ctx)

                    // Spinner setup
                    val spinner = Spinner(ctx, Spinner.MODE_DROPDOWN).apply {
                        id = View.generateViewId()
                        background = ctx.getDrawable(R.drawable.bg_input_bordered)
                        setPadding(24, 6, 56, 6) // extra right padding for arrow
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        layoutParams = RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // Dropdown arrow icon
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

                    wrapper.addView(spinner)
                    wrapper.addView(arrow)

                    // Options list with placeholder
                    val labels = mutableListOf("Select")
                    val values = mutableListOf("") // empty string for default

                    field.options.forEach {
                        labels.add(it.label)
                        values.add(it.value)
                    }

                    val spinnerAdapter = object : ArrayAdapter<String>(
                        ctx,
                        R.layout.item_spinner_text,
                        labels
                    ) {
                        override fun isEnabled(position: Int): Boolean {
                            // Disable only for required fields
                            return !(field.required && position == 0)
                        }

                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = convertView ?: LayoutInflater.from(ctx)
                                .inflate(R.layout.item_spinner_dropdown, parent, false)
                            val tv = v.findViewById<TextView>(R.id.spinner_dropdown_text)
                            tv.text = labels[position]
                            tv.typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)

                            // Grey placeholder text
                            if (position == 0) tv.setTextColor(Color.GRAY)
                            else tv.setTextColor(Color.BLACK)
                            return v
                        }

                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val tv = super.getView(position, convertView, parent) as TextView
                            tv.typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                            tv.setTextColor(
                                if (position == 0) Color.GRAY
                                else ContextCompat.getColor(ctx, android.R.color.black)
                            )
                            return tv
                        }
                    }

                    spinner.adapter = spinnerAdapter

                    // Preselect if value already exists
                    val currentValue = (value as? String)?.takeIf { it.isNotBlank() }
                    val selectedIndex = if (currentValue != null) values.indexOf(currentValue) else 0
                    spinner.setSelection(if (selectedIndex >= 0) selectedIndex else 0)

                    // Selection listener
                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            val selectedValue = if (position == 0) "" else values[position] ?: ""
                            onFieldChanged(field.id, selectedValue)
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
                    val uris = (value as? List<*>)?.mapNotNull {
                        when (it) {
                            is Uri -> it
                            is String -> Uri.parse(it)
                            else -> null
                        }
                    }?.toMutableList() ?: mutableListOf()

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

                            val fileName = TextView(ctx).apply {
                                text = "photo${index + 1}.jpg"
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
                                    // call ViewModel via adapter callback so the source-of-truth is updated
                                    onRemovePhoto(field.id, uri)

                                    // update the local UI list and values so UI is immediately consistent
                                    uris.remove(uri)
                                    onFieldChanged(field.id, uris.map { it.toString() })
                                    refreshPhotoList()
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

                    host.addView(vertical)
                }

                "rating" -> {
                    val ctx = itemView.context
                    val maxStars = field.max?.toInt() ?: 5
                    var currentRating = (value as? Number)?.toInt() ?: 0

                    // container
                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(16.dpToPx(ctx), 8.dpToPx(ctx), 16.dpToPx(ctx), 8.dpToPx(ctx))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // stars container
                    val starsLayout = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    // rating text
                    val ratingText = TextView(ctx).apply {
                        text = "$currentRating / $maxStars"
                        setPadding(12.dpToPx(ctx), 0, 0, 0)
                        typeface = ResourcesCompat.getFont(ctx, com.core.R.font.dm_sans_medium)
                        setTextColor(Color.BLACK)
                        textSize = 14f
                    }

                    // function to update stars UI
                    fun refreshStars() {
                        for (i in 0 until maxStars) {
                            val starView = starsLayout.getChildAt(i) as ImageView
                            val drawableId =
                                if (i < currentRating) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                            starView.setImageDrawable(ContextCompat.getDrawable(ctx, drawableId))
                        }
                        ratingText.text = "$currentRating / $maxStars"
                    }

                    // create stars
                    repeat(maxStars) { index ->
                        val star = ImageView(ctx).apply {
                            val size = 32.dpToPx(ctx)
                            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                marginEnd = 6.dpToPx(ctx)
                            }
                            setImageDrawable(
                                ContextCompat.getDrawable(
                                    ctx,
                                    if (index < currentRating) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                                )
                            )
                            setOnClickListener {
                                currentRating = index + 1
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

