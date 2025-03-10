package net.emerlink.stream.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import net.emerlink.stream.MainActivity
import net.emerlink.stream.R
import net.emerlink.stream.data.preferences.PreferenceKeys

class OnboardingActivity : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set immersive mode
        isSystemBackButtonLocked = true
        isIndicatorEnabled = true

        setupSlides()
    }

    private fun setupSlides() {
        // Welcome slide (слайд 0)
        addSlide(
            AppIntroFragment.createInstance(
                title = getString(R.string.welcome_to_emerlink),
                description = getString(R.string.welcome_description),
                imageDrawable = R.drawable.ic_welcome,
                backgroundColorRes = R.color.primary
            )
        )

        // Camera permission slide (слайд 1)
        addSlide(
            AppIntroFragment.createInstance(
                title = getString(R.string.camera_permission),
                description = getString(R.string.camera_permission_description),
                imageDrawable = R.drawable.ic_camera,
                backgroundColorRes = R.color.primary
            )
        )

        // Microphone permission slide (слайд 2)
        addSlide(
            AppIntroFragment.createInstance(
                title = getString(R.string.microphone_permission),
                description = getString(R.string.microphone_permission_description),
                imageDrawable = R.drawable.ic_microphone,
                backgroundColorRes = R.color.primary
            )
        )

        // Location permission slide (слайд 3)
        addSlide(
            AppIntroFragment.createInstance(
                title = getString(R.string.location_permission),
                description = getString(R.string.location_permission_description),
                imageDrawable = R.drawable.ic_location,
                backgroundColorRes = R.color.primary
            )
        )

        // Storage permission slide (слайд 4, только для Android < 10)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            addSlide(
                AppIntroFragment.createInstance(
                    title = getString(R.string.storage_permission),
                    description = getString(R.string.storage_permission_description),
                    imageDrawable = R.drawable.ic_storage,
                    backgroundColorRes = R.color.primary
                )
            )
        }

        // Notification permission slide (слайд 5 или 4, в зависимости от версии Android)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addSlide(
                AppIntroFragment.createInstance(
                    title = getString(R.string.notification_permission),
                    description = getString(R.string.notification_permission_description),
                    imageDrawable = R.drawable.ic_notification,
                    backgroundColorRes = R.color.primary
                )
            )
        }

        // Настраиваем запросы разрешений для каждого слайда
        // Запрос разрешения для камеры (на слайде 1)
        askForPermissions(
            permissions = arrayOf(Manifest.permission.CAMERA),
            slideNumber = 2,
            required = false
        )

        // Запрос разрешения для микрофона (на слайде 2)
        askForPermissions(
            permissions = arrayOf(Manifest.permission.RECORD_AUDIO),
            slideNumber = 3,
            required = false
        )

        // Запрос разрешения для местоположения (на слайде 3)
        askForPermissions(
            permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            slideNumber = 4,
            required = false
        )

        // Запрос разрешения для хранилища (на слайде 4, только для Android < 10)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            askForPermissions(
                permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                slideNumber = 5,
                required = false
            )
        }

        // Запрос разрешения для уведомлений (на слайде 5 или 4, в зависимости от версии Android)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationSlideNumber = 5
            askForPermissions(
                permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                slideNumber = notificationSlideNumber,
                required = false
            )
        }
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        finishOnboarding()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        finishOnboarding()
    }

    override fun onUserDeniedPermission(permissionName: String) {
        goToNextSlide()
    }

    override fun onUserDisabledPermission(permissionName: String) {
        goToNextSlide()
    }

    private fun finishOnboarding() {
        // Отмечаем онбординг как завершенный (устанавливаем флаг в false, чтобы показать, что это НЕ первый запуск)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit {
            putBoolean(PreferenceKeys.FIRST_RUN, false)
            apply()
        }

        // Start main activity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
} 