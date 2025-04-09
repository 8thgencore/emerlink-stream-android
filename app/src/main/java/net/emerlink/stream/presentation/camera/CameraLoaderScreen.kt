import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import net.emerlink.stream.data.model.ScreenOrientation
import net.emerlink.stream.data.preferences.PreferenceKeys

@Suppress("ktlint:standard:function-naming")
@Composable
fun CameraLoaderScreen(onReady: () -> Unit) {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val orientation =
        ScreenOrientation.fromString(
            preferences.getString(
                PreferenceKeys.SCREEN_ORIENTATION,
                ScreenOrientation.LANDSCAPE.name
            ) ?: ScreenOrientation.LANDSCAPE.name
        )

    LaunchedEffect(Unit) {
        val activity = context as? ComponentActivity
        when (orientation) {
            ScreenOrientation.LANDSCAPE -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenOrientation.PORTRAIT -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        delay(100)
        onReady()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    )
}
