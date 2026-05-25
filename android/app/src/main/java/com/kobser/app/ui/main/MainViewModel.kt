package com.kobser.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.repository.PreferencesRepository
import com.kobser.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val musicPlayer: MusicPlayer,
    private val prefs: PreferencesRepository
) : ViewModel() {

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            prefs.clearSession()
            onSuccess()
        }
    }
}
