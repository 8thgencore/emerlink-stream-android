@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package net.emerlink.stream.core.navigation

import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.preference.PreferenceManager
import net.emerlink.stream.data.model.ScreenOrientation
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.presentation.camera.CameraScreen
import net.emerlink.stream.presentation.settings.ConnectionSettingsScreen
import net.emerlink.stream.presentation.settings.EditConnectionProfileScreen
import net.emerlink.stream.presentation.settings.SettingsScreen

// Define your navigation routes
object NavigationRoutes {
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
    const val STREAM_SETTINGS = "stream_settings"
    const val EDIT_CONNECTION_PROFILE = "edit_connection_profile"
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    LocalContext.current

    // Handle screen orientation based on current route
    HandleScreenOrientation(navController)

    NavHost(navController = navController, startDestination = NavigationRoutes.CAMERA) {
        composable(NavigationRoutes.CAMERA) {
            CameraScreen(
                onSettingsClick = { navController.navigate(NavigationRoutes.SETTINGS) }
            )
        }

        composable(NavigationRoutes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onStreamSettingsClick = { navController.navigate(NavigationRoutes.STREAM_SETTINGS) }
            )
        }

        composable(NavigationRoutes.STREAM_SETTINGS) {
            ConnectionSettingsScreen(
                onBackClick = { navController.popBackStack() },
                onEditProfile = { profileId ->
                    navController.navigate("${NavigationRoutes.EDIT_CONNECTION_PROFILE}/$profileId")
                },
                onCreateProfile = {
                    navController.navigate(NavigationRoutes.EDIT_CONNECTION_PROFILE)
                }
            )
        }

        // Edit connection profile screen
        composable(
            route = "${NavigationRoutes.EDIT_CONNECTION_PROFILE}/{profileId}",
            arguments =
                listOf(
                    navArgument("profileId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
            EditConnectionProfileScreen(
                profileId = profileId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Create new connection profile screen
        composable(NavigationRoutes.EDIT_CONNECTION_PROFILE) {
            EditConnectionProfileScreen(
                profileId = null,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun HandleScreenOrientation(navController: NavHostController) {
    val context = LocalContext.current
    val currentDestination =
        navController
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route

    DisposableEffect(currentDestination) {
        val activity = context as? ComponentActivity

        when (currentDestination) {
            NavigationRoutes.SETTINGS,
            NavigationRoutes.STREAM_SETTINGS,
            NavigationRoutes.EDIT_CONNECTION_PROFILE,
                -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            else -> {
                // For camera screen, use the orientation from preferences
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                val orientation =
                    ScreenOrientation.fromString(
                        preferences.getString(
                            PreferenceKeys.SCREEN_ORIENTATION,
                            ScreenOrientation.LANDSCAPE.name
                        ) ?: ScreenOrientation.LANDSCAPE.name
                    )
                activity?.requestedOrientation =
                    when (orientation) {
                        ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
            }
        }

        onDispose {}
    }
}
