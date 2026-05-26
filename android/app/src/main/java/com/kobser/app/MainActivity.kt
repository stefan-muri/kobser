package com.kobser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.kobser.app.data.repository.PreferencesRepository
import com.kobser.app.ui.login.LoginScreen
import com.kobser.app.ui.main.MainScreen
import com.kobser.app.ui.theme.KobserTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KobserTheme {
                val sessionId by prefs.sessionId.collectAsState(initial = null)
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (sessionId == null) {
                        LoginScreen(onLoginSuccess = {
                            // Navigation to main content will happen via sessionId state change
                        })
                    } else {
                        MainScreen()
                    }
                }
            }
        }
    }
}
