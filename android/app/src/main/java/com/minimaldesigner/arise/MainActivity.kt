package com.minimaldesigner.arise

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimaldesigner.arise.ui.AppRoot
import com.minimaldesigner.arise.ui.AppViewModel
import com.minimaldesigner.arise.ui.theme.AriseTheme
import com.minimaldesigner.arise.ui.theme.ThemePref

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val system = isSystemInDarkTheme()
            val dark = when (state.theme) {
                ThemePref.SYSTEM -> system
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            // The app theme can differ from the system's, so set the bar icons from ours.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            AriseTheme(dark, state.settings.numbers, state.settings.accent) { AppRoot(vm, state) }
        }
    }
}
