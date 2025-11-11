package com.core.customviews


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ScaleDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.core.R

import kotlin.collections.forEach
import kotlin.collections.indices
import kotlin.jvm.java
import kotlin.ranges.until

class CustomBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val items = mutableListOf<NavItem>()
    private var selectedItem: NavItem? = null

    init {
        orientation = HORIZONTAL
    }

    @SuppressLint("MissingInflatedId")
    fun addItem(iconRes: Int, labelText: String, onClick: () -> Unit) {
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.item_bottom_nav, this, false)

        val iconView = itemView.findViewById<AppCompatImageView>(R.id.iv_nav_icon)
        val textView = itemView.findViewById<AppCompatTextView>(R.id.tv_nav_label)

        iconView.setImageResource(iconRes)
        textView.text = labelText
        textView.isVisible = true // ✅ Always show label

        val item = NavItem(iconView, textView, onClick)
        items.add(item)

        itemView.setOnClickListener { selectItem(item) }
        addView(itemView)
    }

    /**
     * Public API to select an item by index (0-based)
     */
    fun selectItem(index: Int) {
        if (index !in items.indices) return
        val item = items[index]
        selectItem(item)
    }

    /**
     * Handles highlight/animation for selected item
     */
    private fun selectItem(selectedItem: NavItem) {
        if (this.selectedItem == selectedItem) return
        this.selectedItem = selectedItem

        items.forEach { item ->
            val isSelected = item == selectedItem

            // ✅ Update selected state (for tint/textColor selectors)
            item.iconView.isSelected = isSelected
            item.textView.isSelected = isSelected

            // ✅ Scale animation for selected item
            val scale = if (isSelected) 1.1f else 1f
            item.iconView.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(300)
                .start()

            if (isSelected) {
                startAnimatableRecursive(item.iconView.drawable)
                item.onClick()
            }
        }
    }

    /**
     * Recursively starts any AVD or Animatable
     */
    private fun startAnimatableRecursive(drawable: Drawable?) {
        if (drawable == null) return
        when {
            drawable is AnimatedVectorDrawableCompat -> drawable.start()
            drawable is Animatable -> drawable.start()
            drawable is LayerDrawable -> (0 until drawable.numberOfLayers).forEach {
                startAnimatableRecursive(drawable.getDrawable(it))
            }
            drawable is InsetDrawable -> startAnimatableRecursive(drawable.drawable)
            drawable is ScaleDrawable -> startAnimatableRecursive(drawable.drawable)
            else -> try {
                val field = drawable::class.java.getDeclaredField("mDrawable")
                field.isAccessible = true
                startAnimatableRecursive(field.get(drawable) as? Drawable)
            } catch (_: Exception) {
            }
        }
    }

    private data class NavItem(
        val iconView: AppCompatImageView,
        val textView: AppCompatTextView,
        val onClick: () -> Unit
    )
}
