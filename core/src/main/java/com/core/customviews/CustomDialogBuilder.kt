package com.core.customviews





import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.core.R

import kotlin.apply


/**
 * Dark neon themed Material Dialog
 * Matches Travel UI with deep purple + pink accent theme.
 */
class CustomDialogBuilder(private val context: Context) {

    private var title: String? = null
    private var message: String? = null
    private var positiveText: String? = null
    private var negativeText: String? = null
    private var cancelable: Boolean = true
    private var iconRes: Int? = null

    private var onPositiveClick: (() -> Unit)? = null
    private var onNegativeClick: (() -> Unit)? = null
    private var onSingleClick: (() -> Unit)? = null

    fun setTitle(title: String) = apply { this.title = title }
    fun setMessage(message: String) = apply { this.message = message }
    fun setIcon(@DrawableRes res: Int) = apply { this.iconRes = res }
    fun setCancelable(flag: Boolean) = apply { this.cancelable = flag }

    fun setPositiveButton(text: String, callback: () -> Unit) = apply {
        this.positiveText = text
        this.onPositiveClick = callback
    }

    fun setNegativeButton(text: String, callback: () -> Unit) = apply {
        this.negativeText = text
        this.onNegativeClick = callback
    }

    fun setSingleButton(text: String, callback: () -> Unit) = apply {
        this.positiveText = text
        this.onSingleClick = callback
    }

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_common, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(cancelable)
            .create()


        dialog.window?.setBackgroundDrawable(
            ColorDrawable(ContextCompat.getColor(context, android.R.color.transparent))
        )
        // Views
        val titleView = dialogView.findViewById<AppCompatTextView>(R.id.dialogTitle)
        val messageView = dialogView.findViewById<AppCompatTextView>(R.id.dialogMessage)
        val btnPositive = dialogView.findViewById<AppCompatButton>(R.id.btnPositive)
        val btnNegative = dialogView.findViewById<AppCompatButton>(R.id.btnNegative)

        titleView.text = title ?: ""
        messageView.text = message ?: ""

        // Configure buttons
        when {
            onSingleClick != null -> {
                btnPositive.text = positiveText ?: "OK"
                btnPositive.setOnClickListener {
                    onSingleClick?.invoke()
                    dialog.dismiss()
                }
                btnNegative.visibility = View.GONE
            }

            onPositiveClick != null && onNegativeClick != null -> {
                btnPositive.text = positiveText ?: "OK"
                btnNegative.text = negativeText ?: "Cancel"
                btnPositive.setOnClickListener {
                    onPositiveClick?.invoke()
                    dialog.dismiss()
                }
                btnNegative.setOnClickListener {
                    onNegativeClick?.invoke()
                    dialog.dismiss()
                }
            }

            onPositiveClick != null -> {
                btnPositive.text = positiveText ?: "OK"
                btnPositive.setOnClickListener {
                    onPositiveClick?.invoke()
                    dialog.dismiss()
                }
                btnNegative.visibility = View.GONE
            }
        }

        dialog.show()
    }
}
