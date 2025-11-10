package com.core.theme

import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    fun applyLightMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    fun applyDarkMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    fun applySystemMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}
//in Application class
// Apply saved theme
//when (MyPreference.getString("theme_mode", "system")) {
//    "light" -> ThemeHelper.applyLightMode()
//    "dark" -> ThemeHelper.applyDarkMode()
//    else -> ThemeHelper.applySystemMode()
//}
