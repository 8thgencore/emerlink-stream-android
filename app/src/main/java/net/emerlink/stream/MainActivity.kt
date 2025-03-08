package net.emerlink.stream

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import net.emerlink.stream.data.preferences.PreferenceKeys
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

    // Регистрируем обработчик для множественного запроса разрешений
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Если все необходимые разрешения получены или пользователь отказал
        // Всё равно запускаем интерфейс
        startMainUI()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set default orientation based on preferences
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val orientation = preferences.getString(
            PreferenceKeys.SCREEN_ORIENTATION,
            PreferenceKeys.SCREEN_ORIENTATION_DEFAULT
        ) ?: PreferenceKeys.SCREEN_ORIENTATION_DEFAULT

        // Apply orientation
        requestedOrientation = when (orientation) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//            "auto" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // Получаем флаг, прошел ли пользователь онбординг
        val isFirstRun = preferences.getBoolean(PreferenceKeys.FIRST_RUN, true)

        // Запускаем онбординг только если это первый запуск
        if (isFirstRun) {
            startOnboarding()
            return
        }

        // Проверяем разрешения после онбординга
        if (!PermissionUtil.hasPermissions(this, requiredPermissions)) {
            // Запрашиваем разрешения с помощью нового API
            requestPermissionsLauncher.launch(requiredPermissions)
            return
        }

        // Если все разрешения уже есть, запускаем обычный интерфейс
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
                    // Получаем сервис из Application
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
@Composable
fun AppNavigation(streamService: StreamService?) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Track current destination to set orientation
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route

    // Set orientation based on current screen
    DisposableEffect(currentDestination) {
        val activity = context as? ComponentActivity

        when (currentDestination) {
            "camera" -> {
                // For camera screen, use the orientation from preferences
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                val orientation = preferences.getString(
                    PreferenceKeys.SCREEN_ORIENTATION,
                    PreferenceKeys.SCREEN_ORIENTATION_DEFAULT
                ) ?: PreferenceKeys.SCREEN_ORIENTATION_DEFAULT

                activity?.requestedOrientation = when (orientation) {
                    "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }

            "settings", "advanced_settings" -> {
                // Для экранов настроек устанавливаем портретную ориентацию
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        onDispose {}
    }

    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                onSettingsClick = { navController.navigate("settings") },
                streamService = streamService
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