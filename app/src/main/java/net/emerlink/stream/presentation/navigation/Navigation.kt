@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package net.emerlink.stream.presentation.navigation

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
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.presentation.ui.camera.CameraScreen
import net.emerlink.stream.presentation.ui.settings.ConnectionSettingsScreen
import net.emerlink.stream.presentation.ui.settings.EditConnectionProfileScreen
import net.emerlink.stream.presentation.ui.settings.SettingsScreen
import net.emerlink.stream.service.StreamService

// Define your navigation routes
object NavigationRoutes {
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
    const val STREAM_SETTINGS = "stream_settings"
    const val EDIT_CONNECTION_PROFILE = "edit_connection_profile"
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavigation(streamService: StreamService?) {
    val navController = rememberNavController()
    LocalContext.current

    // Handle screen orientation based on current route
    HandleScreenOrientation(navController)

    NavHost(navController = navController, startDestination = NavigationRoutes.CAMERA) {
        composable(NavigationRoutes.CAMERA) {
            CameraScreen(
                onSettingsClick = { navController.navigate(NavigationRoutes.SETTINGS) },
                streamService = streamService
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

//        composable(NavigationRoutes.ADVANCED_SETTINGS) {
//            AdvancedSettingsScreen(
//                onBackClick = { navController.popBackStack() },
//            )
//        }
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
            NavigationRoutes.CAMERA -> {
                // For camera screen, use the orientation from preferences
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                val orientation =
                    preferences.getString(
                        PreferenceKeys.SCREEN_ORIENTATION,
                        PreferenceKeys.SCREEN_ORIENTATION_DEFAULT
                    ) ?: PreferenceKeys.SCREEN_ORIENTATION_DEFAULT

                activity?.requestedOrientation =
                    when (orientation) {
                        "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
            }

            NavigationRoutes.SETTINGS,
            NavigationRoutes.STREAM_SETTINGS,
            NavigationRoutes.EDIT_CONNECTION_PROFILE,
                -> {
                    // For settings screens, use unspecified orientation
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
        }

        onDispose {}
    }
}
