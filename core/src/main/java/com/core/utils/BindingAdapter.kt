package com.core.utils

import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import com.core.R
import com.core.constants.AppConstants
import com.google.android.material.card.MaterialCardView

@BindingAdapter("hideIfNotEmpty")
fun AppCompatImageView.hideIfNotEmpty(value: String?) {
    visibility = if (!value.isNullOrBlank()) View.VISIBLE else View.INVISIBLE
}
@BindingAdapter(/* ...value = */ "app:src")
fun setImageResource(imageView: AppCompatImageView, resId: Int) {

    AppCompatResources.getDrawable(imageView.context, resId)?.let {
        imageView.setImageDrawable(it)
    }
}

@BindingAdapter("app:backgroundRes")
fun setBackgroundResource(view: AppCompatImageView, resId: Int) {
    view.background = ContextCompat.getDrawable(view.context, resId)
}

@BindingAdapter("taskPriorityTextBackground")
fun setTaskPriorityTextBackground(view: View, status: String?) {
    val backgroundRes =
        when (status) {


            AppConstants.STATUS_REVISION_NEEDED -> R.drawable.bg_status_revison
            AppConstants.STATUS_NOT_STARTED -> R.drawable.bg_status_not_started
            AppConstants.STATUS_HIGH_PRIORITY -> R.drawable.bg_status_high_priority
            AppConstants.STATUS_COMPLETED -> R.drawable.bg_status_completed
            else -> R.drawable.bg_status_not_started
        }
    view.setBackgroundResource(backgroundRes)
}

@BindingAdapter(/* ...value = */ "app:srcActionButton2")
fun setImage(imageView: AppCompatImageView, status: String?) {
    val backgroundRes = when (status) {
        AppConstants.STATUS_REVISION_NEEDED -> R.drawable.ic_message
        AppConstants.STATUS_COMPLETED -> R.drawable.ic_share
        else -> R.drawable.ic_warning
    }

    AppCompatResources.getDrawable(imageView.context, backgroundRes)?.let {
        imageView.setImageDrawable(it)
    }

}

@BindingAdapter("setCardAction1Background")
fun setCardBackground(card: MaterialCardView, status: String?) {

    val color = when (status) {

        AppConstants.STATUS_REVISION_NEEDED -> ContextCompat.getColor(card.context, R.color.colorLightRed)
        AppConstants.STATUS_COMPLETED -> ContextCompat.getColor(card.context, R.color.colorLightGreen)
        else -> ContextCompat.getColor(card.context, R.color.colorSkyBlue)
    }

    card.setCardBackgroundColor(color)
}

@BindingAdapter("statusTint")
fun setStatusTint(imageView: AppCompatImageView, status: String?) {

    val color = when (status) {

        AppConstants.STATUS_REVISION_NEEDED -> ContextCompat.getColor(imageView.context, R.color.colorRed)
        AppConstants.STATUS_COMPLETED -> ContextCompat.getColor(imageView.context, R.color.colorGreen)
        else -> ContextCompat.getColor(imageView.context, R.color.white)
    }

    imageView.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
}

@BindingAdapter(/* ...value = */ "app:srcActionButton1")
fun setActionImage(imageView: AppCompatImageView, status: String?) {
    val backgroundRes = when (status) {

        AppConstants.STATUS_REVISION_NEEDED -> R.drawable.ic_revision
        AppConstants.STATUS_NOT_STARTED -> R.drawable.ic_play
        AppConstants.STATUS_HIGH_PRIORITY -> R.drawable.ic_edit
        AppConstants.STATUS_COMPLETED -> R.drawable.ic_view
        else -> R.drawable.ic_play

    }
    AppCompatResources.getDrawable(imageView.context, backgroundRes)?.let {
        imageView.setImageDrawable(it)
    }
    //imageView.setImageResource(backgroundRes)
}

@BindingAdapter("statusTextColor")
fun setStatusTextColor(textView: AppCompatTextView, status: String?) {

    val color = when (status) {

        AppConstants.STATUS_REVISION_NEEDED -> ContextCompat.getColor(textView.context, R.color.colorRed)
        AppConstants.STATUS_COMPLETED -> ContextCompat.getColor(textView.context, R.color.colorGreen)
        else -> ContextCompat.getColor(textView.context, R.color.white)
    }

    textView.setTextColor(color)
}

@BindingAdapter("priorityTextColor")
fun setPriorityTextColor(textView: AppCompatTextView, status: String?) {

    val color = when (status) {

        AppConstants.STATUS_REVISION_NEEDED -> ContextCompat.getColor(
            textView.context,
            R.color.colorRed
        )

        AppConstants.STATUS_NOT_STARTED -> ContextCompat.getColor(
            textView.context,
            R.color.colorOrange
        )

        AppConstants.STATUS_HIGH_PRIORITY -> ContextCompat.getColor(
            textView.context,
            R.color.colorPink
        )

        AppConstants.STATUS_COMPLETED -> ContextCompat.getColor(
            textView.context,
            R.color.colorGreen
        )

        else -> ContextCompat.getColor(textView.context, R.color.black)
    }

    textView.setTextColor(color)
}

@BindingAdapter("taskBackgroundColor")
fun setTaskBackgroundColor(view: View, status: String?) {
    val color = when (status) {
        AppConstants.STATUS_REVISION_NEEDED ->
            ContextCompat.getColor(view.context, R.color.colorRed)

        AppConstants.STATUS_NOT_STARTED ->
            ContextCompat.getColor(view.context, R.color.colorOrange)

        AppConstants.STATUS_HIGH_PRIORITY ->
            ContextCompat.getColor(view.context, R.color.colorPink)

        AppConstants.STATUS_COMPLETED ->
            ContextCompat.getColor(view.context, R.color.colorGreen)

        else -> ContextCompat.getColor(view.context, R.color.black)
    }

    view.setBackgroundColor(color)
}

@BindingAdapter("actionButton1Text")
fun setActionButtonText(textView: AppCompatTextView, status: String?) {
    val text = when (status) {
        AppConstants.STATUS_REVISION_NEEDED -> "Revise"
        AppConstants.STATUS_NOT_STARTED -> "Start"
        AppConstants.STATUS_HIGH_PRIORITY -> "Continue"
        AppConstants.STATUS_COMPLETED -> "View"
        else -> "Start"
    }
    textView.text = text
}

@BindingAdapter("actionButton2Text")
fun setActionButton2Text(textView: AppCompatTextView, status: String?) {
    val text = when (status) {
        AppConstants.STATUS_REVISION_NEEDED -> "Message"
        AppConstants.STATUS_NOT_STARTED -> "Issue"
        AppConstants.STATUS_HIGH_PRIORITY -> "Issue"
        AppConstants.STATUS_COMPLETED -> "Share"
        else -> "Issue"
    }
    textView.text = text
}

@BindingAdapter("initialsFromName")
fun AppCompatTextView.setInitialsFromName(name: String?) {
    if (name.isNullOrBlank()) {
        text = ""
        return
    }

    val parts = name.trim().split(" ")

    val initials = parts
        .filter { it.isNotBlank() }
        .map { it.first().uppercaseChar() }
        .take(2)     // only first 2 characters (J + D)
        .joinToString("")

    text = initials
}

