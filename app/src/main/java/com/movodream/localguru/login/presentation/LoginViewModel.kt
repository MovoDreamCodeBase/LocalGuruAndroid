package com.movodream.localguru.login.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.movodream.localguru.login.repository.LoginRepository
import com.movodream.localguru.login.repository.LoginResult


class LoginViewModel : ViewModel() {

    private val repository = LoginRepository()   // ← direct repo creation

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> get() = _loginResult

    fun login(agentId: String?, password: String?) {

        if (agentId.isNullOrBlank()) {
            _loginResult.value = LoginResult.ValidationError("Please enter Agent ID")
            return
        }

        if (password.isNullOrBlank()) {
            _loginResult.value = LoginResult.ValidationError("Please enter Password")
            return
        }

        _loginResult.value = LoginResult.Loading

        val repoLiveData = repository.loginAgent(agentId, password)

        repoLiveData.observeForever(object : Observer<LoginResult> {
            override fun onChanged(result: LoginResult) {
                _loginResult.value = result
                repoLiveData.removeObserver(this)
            }
        })
    }
}
