package com.movodream.localguru.login.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.movodream.localguru.data_collection.ui.activities.DashboardActivity
import com.movodream.localguru.databinding.ActivityLoginBinding
import com.movodream.localguru.login.presentation.LoginViewModel
import com.movodream.localguru.login.repository.LoginResult

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        setupObserver()
        setupClicks()
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

                }
                    //binding.progressBar.showProgressBar()

                is LoginResult.ValidationError -> {
                  //  binding.progressBar.hideProgressBar()
                    showDialog(result.message)
                }

                is LoginResult.Success -> {
                  //  binding.progressBar.hideProgressBar()

                    val name = result.data["agentName"]?.toString() ?: "Agent"

                    showDialog("Welcome $name")
                    goToDashboard(result.data)
                }

                LoginResult.InvalidPassword -> {
                 //   binding.progressBar.hideProgressBar()
                    showDialog("Invalid Password")
                }

                LoginResult.UserNotFound -> {
                  //  binding.progressBar.hideProgressBar()
                    showDialog("Agent not found")
                }

                is LoginResult.Error -> {
                 //   binding.progressBar.hideProgressBar()
                    showDialog(result.message)
                }
            }
        }
    }

    private fun showDialog(msg: String, onOk: (() -> Unit)? = null) {
        Toast.makeText(this,msg, Toast.LENGTH_SHORT).show()
    }

    private fun goToDashboard(data: Map<String, Any>) {
        val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
