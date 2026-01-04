package com.movodream.localguru.login.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.core.preferences.MyPreference
import com.core.preferences.PrefKey
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.ui.activities.DashboardActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        Handler(Looper.getMainLooper()).postDelayed({
            navigateNext()
        }, 2000)
    }

    private fun navigateNext() {
        val savedMobile = MyPreference.getValueString(PrefKey.PHONE_NUMBER,"")
        val lastLoginDate = MyPreference.getValueString(PrefKey.LOGIN_DATE,"")

        val todayDate = getTodayDate()

        val intent = if (!savedMobile.isNullOrEmpty() && lastLoginDate == todayDate) {
            Intent(this, DashboardActivity::class.java).putExtra("KEY_AGENT_ID",savedMobile)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        startActivity(intent)
        finish()
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }


}