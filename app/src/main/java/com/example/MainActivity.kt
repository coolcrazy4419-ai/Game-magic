package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.core.SoundManager
import com.example.core.SystemTelemetryEngine
import com.example.core.TurboSettingsStore
import com.example.ui.MainScreen
import com.example.ui.theme.GamingTurboTheme
import com.example.ui.theme.ObsidianGreenBg
import com.example.ui.viewmodel.TurboViewModel

class MainActivity : ComponentActivity() {

    private val turboViewModel: TurboViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TurboSettingsStore.init(applicationContext)
        SystemTelemetryEngine.applySustainedPerformance(
            window,
            TurboSettingsStore.state.value.sustainedPerformanceEnabled
        )

        // Play crisp futuristic Game Magic power-up chime immediately on app open
        if (savedInstanceState == null) {
            SoundManager.playAppOpenChime(applicationContext)
        }

        enableEdgeToEdge()
        setContent {
            GamingTurboTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianGreenBg
                ) {
                    MainScreen(viewModel = turboViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        turboViewModel.refreshPermissions()
    }
}
