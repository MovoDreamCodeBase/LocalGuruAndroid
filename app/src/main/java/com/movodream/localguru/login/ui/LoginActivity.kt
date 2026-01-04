package com.movodream.localguru.login.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.core.preferences.MyPreference
import com.core.preferences.PrefKey
import com.core.utils.Utils
import com.movodream.localguru.data_collection.ui.activities.DashboardActivity
import com.movodream.localguru.databinding.ActivityLoginBinding
import com.movodream.localguru.login.presentation.LoginViewModel
import com.movodream.localguru.login.repository.LoginResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        setVersion()
        setupObserver()
        setupClicks()
    }

    private fun setVersion() {
        val versionName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                packageManager.getPackageInfo(packageName, 0).versionName
            }
        } catch (e: Exception) {
            "N/A"
        }
         binding.tvVersion.text = "Version + $versionName"


    }

    private fun setupClicks() {

        binding.btnLogin.setOnClickListener {

            val agentId = binding.edtMobileNumber.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            viewModel.login(agentId, password)
        }
    }

    private fun setupObserver() {

        viewModel.loginResult.observe(this) { result ->

            when (result) {

                LoginResult.Loading ->{
                Utils.showProgressDialog(this)
                }


                is LoginResult.ValidationError -> {
                  Utils.hideProgressDialog()
                    showDialog(result.message)
                }

                is LoginResult.Success -> {
                    Utils.hideProgressDialog()

                    val name = result.data["agentName"]?.toString() ?: "Agent"

                  //  showDialog("Welcome $name")
                    goToDashboard(result.data)
                }

                LoginResult.InvalidPassword -> {
                    Utils.hideProgressDialog()
                    showDialog("Invalid Password")
                }

                LoginResult.UserNotFound -> {
                    Utils.hideProgressDialog()
                    showDialog("Agent not found")
                }

                is LoginResult.Error -> {
                    Utils.hideProgressDialog()
                    showDialog(result.message)
                }
            }
        }
    }

    private fun showDialog(msg: String, onOk: (() -> Unit)? = null) {
        Toast.makeText(this,msg, Toast.LENGTH_SHORT).show()
    }

    private fun goToDashboard(data: Map<String, Any>) {
        MyPreference.setValueString(PrefKey.PHONE_NUMBER,data["agentId"].toString())
        MyPreference.setValueString(PrefKey.LOGIN_DATE,getTodayDate())
        val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
        intent.putExtra("KEY_AGENT_ID",data["agentId"]?.toString())
        startActivity(intent)
        finish()
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}
