package com.core.context

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AppContext {
    private var context: Context? = null

    fun init(app: Application) {
        context = app.applicationContext
    }

    fun get(): Context {
        return context
            ?: throw IllegalStateException("AppContext not initialized. Call AppContext.init(app) in Application class.")
    }
}