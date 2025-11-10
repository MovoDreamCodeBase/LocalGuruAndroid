package com.core.base


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.core.R


open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, true)


        // ✅ Set solid status bar color (visible, non-transparent)
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorTextLightBlack)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.color_app_background)

        // ✅ Handle system bar icon colors based on theme
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val isLightTheme = isUsingLightTheme()
        controller.isAppearanceLightStatusBars = isLightTheme
        controller.isAppearanceLightNavigationBars = isLightTheme
    }

    /**
     * Detects whether the current theme is light or dark.
     */
    private fun isUsingLightTheme(): Boolean {
        val uiMode = resources.configuration.uiMode
        val nightMask = uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMask != android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

