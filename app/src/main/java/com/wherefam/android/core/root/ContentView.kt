package com.wherefam.android.core.root

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wherefam.android.core.home.HomeView
import com.wherefam.android.core.onboarding.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentView(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable("Onboarding") {
            OnboardingView(
                navController,
                pages = listOf(
                    { FirstPageView() },
                    { SecondPageView() },
                    { ThirdPageView() },
                    { FourthPageView() },
                    { FifthPageView() }
                )
            )
        }

        composable("Home") {
            HomeView()
        }
    }
}
