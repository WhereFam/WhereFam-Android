package com.wherefam.android.di

import androidx.room.Room
import com.wherefam.android.core.home.HomeViewModel
import com.wherefam.android.core.home.people.PeopleViewModel
import com.wherefam.android.core.home.places.PlacesViewModel
import com.wherefam.android.core.home.safety.SafetyViewModel
import com.wherefam.android.core.home.settings.SettingsViewModel
import com.wherefam.android.core.home.share.ShareViewModel
import com.wherefam.android.core.onboarding.OnboardingViewModel
import com.wherefam.android.core.onboarding.SplashViewModel
import com.wherefam.android.core.onboarding.ThirdPageViewModel
import com.wherefam.android.data.MIGRATION_1_2
import com.wherefam.android.data.MIGRATION_2_3
import com.wherefam.android.data.UserRepository
import com.wherefam.android.data.WhereFamDatabase
import com.wherefam.android.data.ipc.IPCProvider
import com.wherefam.android.data.ipc.UserRepositoryImpl
import com.wherefam.android.data.local.DataStoreRepository
import com.wherefam.android.manager.LocationManager
import com.wherefam.android.processing.GenericMessageProcessor
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { LocationManager(get()) }
    single { IPCProvider.ipc!! }
    single { DataStoreRepository(get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }

    single {
        Room.databaseBuilder(get(), WhereFamDatabase::class.java, "wherefam_database")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    single { get<WhereFamDatabase>().peerDao }
    single { get<WhereFamDatabase>().placeDao }
    single { get<WhereFamDatabase>().locationHistoryDao }

    single { GenericMessageProcessor(get(), get(), get(), get(), get(), get()) }
}

val viewModel = module {
    viewModel { SplashViewModel(get()) }
    viewModel { OnboardingViewModel(get()) }
    viewModel { ThirdPageViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { ShareViewModel(get()) }
    viewModel { PeopleViewModel(get(), get()) }
    viewModel { PlacesViewModel(get()) }
    viewModel { SafetyViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}