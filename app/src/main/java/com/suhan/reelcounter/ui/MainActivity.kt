package com.suhan.reelcounter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.suhan.reelcounter.ReelCounterApplication
import com.suhan.reelcounter.ui.screens.FirstLaunchScreen
import com.suhan.reelcounter.ui.screens.HomeScreen
import com.suhan.reelcounter.ui.screens.SettingsScreen
import com.suhan.reelcounter.ui.theme.ReelCounterTheme
import com.suhan.reelcounter.ui.theme.ReelTheme
import com.suhan.reelcounter.viewmodel.MainViewModel
import com.suhan.reelcounter.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as ReelCounterApplication
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(app.countStore, app.preferences) as T
            }
        })[MainViewModel::class.java]

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.currentScreen.value == Screen.Settings) {
                    viewModel.navigateTo(Screen.Home)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            ReelCounterTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = ReelTheme.colors.background
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()

                    // Screens slide in from the right and out to the right, about 350 ms per spec
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == Screen.Settings && initialState == Screen.Home) {
                                (slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(350)
                                ) + fadeIn(animationSpec = tween(350))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { -it / 3 },
                                        animationSpec = tween(350)
                                    ) + fadeOut(animationSpec = tween(350))
                                )
                            } else if (targetState == Screen.Home && initialState == Screen.Settings) {
                                (slideInHorizontally(
                                    initialOffsetX = { -it / 3 },
                                    animationSpec = tween(350)
                                ) + fadeIn(animationSpec = tween(350))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(350)
                                    ) + fadeOut(animationSpec = tween(350))
                                )
                            } else {
                                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                            }
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            Screen.FirstLaunch -> {
                                FirstLaunchScreen(viewModel = viewModel)
                            }
                            Screen.Home -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { viewModel.navigateTo(Screen.Settings) }
                                )
                            }
                            Screen.Settings -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { viewModel.navigateTo(Screen.Home) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAccessibilityPermission(this)
        viewModel.onHomeOpened()
    }
}
