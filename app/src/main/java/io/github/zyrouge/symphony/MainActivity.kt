package io.github.zyrouge.symphony

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.zyrouge.symphony.ui.view.BaseView
import io.github.zyrouge.symphony.utils.Logger

class MainActivity : ComponentActivity() {
    private var gSymphony: Symphony? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val ignition: ActivityIgnition by viewModels()
        
        // CRITICAL FIX: installSplashScreen() MUST be called BEFORE super.onCreate()
        // Otherwise, the AndroidX library fails to attach the dismissal listener on 
        // OEM skins like MIUI/HyperOS, causing the app to freeze on the logo infinitely.
        installSplashScreen().apply {
            setKeepOnScreenCondition { !ignition.ready.value }
        }
        
        super.onCreate(savedInstanceState)

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

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(Unit) {
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
}
