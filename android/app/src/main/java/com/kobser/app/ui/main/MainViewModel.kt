package com.kobser.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.repository.PreferencesRepository
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val musicPlayer: MusicPlayer,
    private val prefs: PreferencesRepository,
    private val api: KobserApi,
) : ViewModel() {

    init {
        // Probe the stored session once on launch. A 401 here is picked up by the
        // auth interceptor, which clears the session so MainActivity swaps back to
        // the login screen instead of every tab quietly loading nothing.
        viewModelScope.launch {
            try { api.me() } catch (_: Exception) { /* offline: keep the session */ }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            prefs.clearSession()
            onSuccess()
        }
    }
}
