package io.github.ceigt.geolocation.manager

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import io.github.ceigt.geolocation.manager.localization.LocaleController
import io.github.ceigt.geolocation.manager.ui.navigation.AppNavGraph
import io.github.ceigt.geolocation.manager.ui.theme.GeoLocationTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        setContent {
            // The map SDK is intentionally light-only. Keep the app in the same light scheme so
            // switching the system to dark mode never creates a mismatched, high-contrast shell.
            GeoLocationTheme(darkTheme = false) {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }

}
