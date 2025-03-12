package net.emerlink.stream.di

import android.content.Context
import net.emerlink.stream.service.StreamService
import net.emerlink.stream.util.ErrorHandler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule =
    module {
        // Repositories

        // ViewModels

        // Use cases

        // Services
        single { StreamService() }

        // Utils
        single { ErrorHandler(androidContext()) }

        // Providers
        factory { (context: Context) -> ErrorHandler(context) }
    }
