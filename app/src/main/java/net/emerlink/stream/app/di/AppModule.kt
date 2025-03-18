package net.emerlink.stream.app.di

import android.content.Context
import net.emerlink.stream.core.ErrorHandler
import net.emerlink.stream.data.repository.ConnectionProfileRepository
import net.emerlink.stream.data.repository.SettingsRepository
import net.emerlink.stream.presentation.camera.viewmodel.CameraViewModel
import net.emerlink.stream.presentation.settings.viewmodel.ConnectionProfilesViewModel
import net.emerlink.stream.presentation.settings.viewmodel.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        // Repositories
        single { ConnectionProfileRepository(androidContext()) }
        single { SettingsRepository(androidContext()) }

        // ViewModels
        viewModel { SettingsViewModel(androidContext() as android.app.Application) }
        viewModel { ConnectionProfilesViewModel(androidContext() as android.app.Application) }
        viewModel { CameraViewModel() }

        // Utils
        single { ErrorHandler(androidContext()) }

        // Providers
        factory { (context: Context) -> ErrorHandler(context) }
    }
