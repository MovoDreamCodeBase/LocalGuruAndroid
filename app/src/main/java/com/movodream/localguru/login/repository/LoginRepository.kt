package com.movodream.localguru.login.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.core.constants.AppConstants
import com.google.firebase.database.FirebaseDatabase

sealed class LoginResult {
    object Loading : LoginResult()
    data class Success(val data: Map<String, Any>) : LoginResult()
    object InvalidPassword : LoginResult()
    object UserNotFound : LoginResult()
    data class ValidationError(val message: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}
class LoginRepository {

    private val rootRef = FirebaseDatabase.getInstance().reference



    fun loginAgent(agentId: String, password: String): LiveData<LoginResult> {
        val result = MutableLiveData<LoginResult>()

        val agentRef = rootRef.child(AppConstants.FIREBASE_LOCAL_GURU_DB)
            .child(AppConstants.AGENT_LOGIN_CREDENTIALS)
            .child(agentId)

        try {
            agentRef.get()
                .addOnSuccessListener { snapshot ->

                    if (!snapshot.exists()) {
                        result.value = LoginResult.UserNotFound
                        return@addOnSuccessListener
                    }

                    val dataMap = snapshot.value as? Map<String, Any>

                    if (dataMap == null) {
                        result.value = LoginResult.Error("Invalid agent data format")
                        return@addOnSuccessListener
                    }

                    val storedPassword = dataMap["password"]?.toString()

                    if (storedPassword.isNullOrBlank()) {
                        result.value = LoginResult.Error("Password missing in database")
                        return@addOnSuccessListener
                    }

                    if (storedPassword.equals(password, ignoreCase = true)) {
                        result.value = LoginResult.Success(dataMap)
                    } else {
                        result.value = LoginResult.InvalidPassword
                    }
                }
                .addOnFailureListener { e ->
                    result.value = LoginResult.Error(e.message ?: "Something went wrong")
                }
        } catch (e: Exception) {
           e.stackTrace
        }

        return result
    }
}

