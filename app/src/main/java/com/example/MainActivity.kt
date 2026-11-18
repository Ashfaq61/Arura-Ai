package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.ai.configureAppCheck
import com.example.aura.ui.AuraRootShell
import com.example.aura.ui.AuraViewModel
import com.example.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {

    private val auraViewModel: AuraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent BEFORE setContent or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val prefs by auraViewModel.preferences.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                auraViewModel.expansion.handleIncomingIntent(intent)
            }

            AuraTheme(themeMode = prefs.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuraRootShell(viewModel = auraViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        auraViewModel.expansion.handleIncomingIntent(intent)
    }
}
