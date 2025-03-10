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

    private val permissions = mutableListOf(
        Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION
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

        // Set immersive mode
        isSystemBackButtonLocked = true
        isIndicatorEnabled = true

        setupSlides()
    }

    private fun setupSlides() {
        // Welcome slide
        addSlide(
            AppIntroFragment.createInstance(
                title = getString(R.string.welcome_to_emerlink),
                description = getString(R.string.welcome_description),
                imageDrawable = R.drawable.ic_welcome,
                backgroundColorRes = R.color.primary
            )
        )

        // Camera permission slide
        addSlide(
            AppIntroFragment.createInstance(
                title = getString(R.string.camera_permission),
                description = getString(R.string.camera_permission_description),
                imageDrawable = R.drawable.ic_camera,
                backgroundColorRes = R.color.primary
            )
        )
        askForPermissions(
            permissions = arrayOf(Manifest.permission.CAMERA),
            slideNumber = 1,
            required = false
        )

        // Microphone permission slide
        addSlide(
            AppIntroFragment.createInstance(
                title = getString(R.string.microphone_permission),
                description = getString(R.string.microphone_permission_description),
                imageDrawable = R.drawable.ic_microphone,
                backgroundColorRes = R.color.primary
            )
        )
        askForPermissions(
            permissions = arrayOf(Manifest.permission.RECORD_AUDIO),
            slideNumber = 2,
            required = false
        )

        addSlide(
            AppIntroFragment.createInstance(
                title = getString(R.string.location_permission),
                description = getString(R.string.location_permission_description),
                imageDrawable = R.drawable.ic_location,
                backgroundColorRes = R.color.primary
            )
        )

        askForPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            slideNumber = 3,
            required = false
        )


        // Storage permission slide (for Android < 10)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            addSlide(
                AppIntroFragment.createInstance(
                    title = getString(R.string.storage_permission),
                    description = getString(R.string.storage_permission_description),
                    imageDrawable = R.drawable.ic_storage,
                    backgroundColorRes = R.color.primary
                )
            )
            askForPermissions(
                permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                slideNumber = 4,
                required = false
            )
        }

        // Notification permission slide (for Android >= 13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addSlide(
                AppIntroFragment.createInstance(
                    title = getString(R.string.notification_permission),
                    description = getString(R.string.notification_permission_description),
                    imageDrawable = R.drawable.ic_notification,
                    backgroundColorRes = R.color.primary
                )
            )
            askForPermissions(
                permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                slideNumber = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) 4 else 3,
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
        // Просто переходим к следующему слайду, даже если пользователь отказал в разрешении
        goToNextSlide()
    }

    override fun onUserDisabledPermission(permissionName: String) {
        // Просто переходим к следующему слайду, даже если пользователь отключил разрешение
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