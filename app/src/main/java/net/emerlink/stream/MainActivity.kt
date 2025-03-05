package net.emerlink.stream

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.emerlink.stream.service.StreamService
import net.emerlink.stream.ui.camera.CameraScreen
import net.emerlink.stream.ui.onboarding.OnboardingActivity
import net.emerlink.stream.ui.settings.AdvancedSettingsScreen
import net.emerlink.stream.ui.settings.SettingsScreen
import net.emerlink.stream.ui.theme.EmerlinkStreamTheme
import net.emerlink.stream.util.PermissionUtil

class MainActivity : ComponentActivity() {
    
    private val requiredPermissions = mutableListOf(
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (!PermissionUtil.hasPermissions(this, requiredPermissions)) {
            startOnboarding()
            return
        }
        
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
        
        // Start the streaming service
        Intent(this, StreamService::class.java).also { intent ->
            startForegroundService(intent)
        }
    }
    
    private fun startOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onAdvancedSettingsClick = { navController.navigate("advanced_settings") }
            )
        }
        composable("advanced_settings") {
            AdvancedSettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}