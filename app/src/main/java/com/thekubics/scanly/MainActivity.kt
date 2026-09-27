package com.thekubics.scanly

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.thekubics.scanly.presentation.navigation.AppNavigation
import com.thekubics.scanly.presentation.settings.SettingsViewModel
import com.thekubics.scanly.presentation.theme.DocScannerTheme
import com.thekubics.scanly.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()

            // Dynamic Window FLAG_SECURE: Prevent screen recording and task switcher leaks when app lock is enabled
            LaunchedEffect(settings.appLockEnabled) {
                if (settings.appLockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val themeMode = when (settings.theme) {
                com.thekubics.scanly.domain.model.UserSettings.ThemeMode.SYSTEM -> ThemeMode.SYSTEM
                com.thekubics.scanly.domain.model.UserSettings.ThemeMode.LIGHT -> ThemeMode.LIGHT
                com.thekubics.scanly.domain.model.UserSettings.ThemeMode.DARK -> ThemeMode.DARK
            }
            DocScannerTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}
