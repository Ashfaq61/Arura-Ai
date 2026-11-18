package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aura.ai.configureAppCheck
import com.example.aura.ui.AuraRootShell
import com.example.aura.ui.AuraViewModel
import com.example.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent BEFORE setContent or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val auraViewModel: AuraViewModel = viewModel()
            val prefs by auraViewModel.preferences.collectAsStateWithLifecycle()

            AuraTheme(themeMode = prefs.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuraRootShell(viewModel = auraViewModel)
                }
            }
        }
    }
}
