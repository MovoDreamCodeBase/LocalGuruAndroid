package com.movodream.localguru

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.core.preferences.MyPreference
import com.network.client.ApiClient

class AppBase : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        ApiClient.initRetrofit()
        MyPreference.init(this)
    }
}