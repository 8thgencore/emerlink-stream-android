package net.emerlink.stream.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.preference.PreferenceManager
import net.emerlink.stream.R
import net.emerlink.stream.core.navigation.AppNavigation
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.presentation.onboarding.OnboardingActivity
import net.emerlink.stream.presentation.theme.EmerlinkStreamTheme
import net.emerlink.stream.util.PermissionUtil

class MainActivity : ComponentActivity() {
    private val requiredPermissions =
        mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        ).apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
            }
        }.toTypedArray()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> startMainUI() }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val isFirstRun = preferences.getBoolean(PreferenceKeys.FIRST_RUN, true)
        if (isFirstRun) {
            startOnboarding()
            return
        }

        if (!PermissionUtil.hasPermissions(this, requiredPermissions)) {
            requestPermissionsLauncher.launch(requiredPermissions)
            return
        }

        startMainUI()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startMainUI() {
        // Установить тему с черным фоном для экрана камеры
        setTheme(R.style.Theme_EmerlinkStream_Camera)

        setContent {
            EmerlinkStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun startOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Suppress("ktlint:standard:function-naming")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    EmerlinkStreamTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation()
        }
    }
}
