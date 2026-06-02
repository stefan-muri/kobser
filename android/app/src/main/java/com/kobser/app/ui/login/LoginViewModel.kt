package com.kobser.app.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.LoginRequest
import kotlinx.coroutines.flow.first
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: KobserApi,
    private val prefs: PreferencesRepository
) : ViewModel() {

    var serverUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            prefs.serverUrl.first()?.let { if (it.isNotEmpty()) serverUrl = it }
            prefs.lastUsername.first()?.let { if (it.isNotEmpty()) username = it }
        }
    }

    fun login(onSuccess: () -> Unit) {
        val url = serverUrl.trim()
        if (url.isEmpty()) {
            error = "Server URL is required"
            return
        }
        
        if (url.toHttpUrlOrNull() == null) {
            error = "Invalid Server URL"
            return
        }

        isLoading = true
        error = null

        viewModelScope.launch {
            try {
                prefs.saveServerUrl(url)
                // Note: In a real app, we might need to recreate the Retrofit instance 
                // if the base URL changes, or use a dynamic base URL interceptor.
                
                val response = api.login(LoginRequest(username, password))
                if (response.isSuccessful) {
                    response.body()?.sessionId?.let {
                        prefs.saveSessionId(it)
                        prefs.saveLastUsername(username)
                        onSuccess()
                    } ?: run {
                        error = "Login failed: No session ID"
                    }
                } else {
                    error = "Login failed: ${response.code()}"
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}
