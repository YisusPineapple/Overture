package io.github.zyrouge.symphony

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.zyrouge.symphony.ui.view.BaseView
import io.github.zyrouge.symphony.utils.Logger

class MainActivity : ComponentActivity() {
    private var gSymphony: Symphony? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ignition: ActivityIgnition by viewModels()
        if (savedInstanceState == null) {
            installSplashScreen().apply {
                setKeepOnScreenCondition { !ignition.ready.value }
            }
        }

        Thread.setDefaultUncaughtExceptionHandler { _, err ->
            Logger.error("MainActivity", "uncaught exception", err)
            ErrorActivity.start(this, err)
            finish()
        }

        val symphony: Symphony by viewModels()
        symphony.permission.handle(this)
        gSymphony = symphony
        symphony.emitActivityReady()
        attachHandlers()

        // Optimize display refresh rate for smooth 90Hz/120Hz/144Hz/165Hz rendering
        setupHighRefreshRate()

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(LocalContext.current) {
                ignition.emitReady()
            }
            BaseView(symphony = symphony, activity = this)
        }
    }

    override fun onPause() {
        super.onPause()
        gSymphony?.emitActivityPause()
    }

    override fun onResume() {
        super.onResume()
        // Trigger a full state re-sync so the UI and notification recover
        // correctly after the activity returns from background.
        gSymphony?.emitActivityResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        gSymphony?.emitActivityDestroy()
    }

    private fun attachHandlers() {
        gSymphony?.closeApp = {
            finish()
        }
    }

    private fun setupHighRefreshRate() {
        // Display.Mode was introduced in API 23, completely safe for minSdk 24
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                @Suppress("DEPRECATION")
                val display = window.windowManager.defaultDisplay
                display?.let { d ->
                    val maxRefreshRate = d.supportedModes
                        .map { it.refreshRate }
                        .maxOrNull() ?: 60f
                    
                    // Find the highest resolution mode that supports the maximum refresh rate
                    val bestMode = d.supportedModes
                        .filter { it.refreshRate == maxRefreshRate }
                        .maxByOrNull { it.physicalWidth * it.physicalHeight }
                    
                    bestMode?.let { mode ->
                        val params = window.attributes
                        params.preferredDisplayModeId = mode.modeId
                        window.attributes = params
                        Logger.warn("MainActivity", "Display configured to max refresh rate: ${mode.refreshRate}Hz")
                    }
                }
            } catch (err: Exception) {
                Logger.error("MainActivity", "Failed to configure high refresh rate: $err")
            }
        }
    }
}