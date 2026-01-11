package com.core.customviews

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import com.core.R

class MasterDownloadProgressDialog(
    private val context: Context
) {

    private var dialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var tvPercent: TextView? = null
    private var tvSubtitle: TextView? = null

    fun show() {
        if (dialog?.isShowing == true) return

        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_master_download_progress, null)

        progressBar = view.findViewById(R.id.progressBar)
        tvPercent = view.findViewById(R.id.tvPercent)
        tvSubtitle = view.findViewById(R.id.tvSubtitle)

        progressBar?.progress = 0
        tvPercent?.text = "0%"

        dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog?.show()
    }

    fun update(percent: Int) {
        progressBar?.progress = percent
        tvPercent?.text = "$percent%"
        tvSubtitle?.text = "Downloading... $percent%"
    }

    fun hide() {
        dialog?.dismiss()
        dialog = null
    }
}
