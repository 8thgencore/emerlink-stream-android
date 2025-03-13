package net.emerlink.stream

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
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
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.navigation.AppNavigation
import net.emerlink.stream.ui.onboarding.OnboardingActivity
import net.emerlink.stream.ui.theme.EmerlinkStreamTheme
import net.emerlink.stream.util.PermissionUtil

class MainActivity : ComponentActivity() {
    private val requiredPermissions =
        mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
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

        // Set default orientation based on preferences
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val orientation =
            preferences.getString(
                PreferenceKeys.SCREEN_ORIENTATION,
                PreferenceKeys.SCREEN_ORIENTATION_DEFAULT
            ) ?: PreferenceKeys.SCREEN_ORIENTATION_DEFAULT

        // Apply orientation
        requestedOrientation =
            when (orientation) {
                "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

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
        setContent {
            EmerlinkStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(EmerlinkStreamApp.getStreamService())
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    EmerlinkStreamTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation(null)
        }
    }
}
