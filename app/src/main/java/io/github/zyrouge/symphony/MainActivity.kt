package io.github.zyrouge.symphony

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.zyrouge.symphony.ui.view.BaseView
import io.github.zyrouge.symphony.utils.Logger
import android.os.Process
import java.io.File
import kotlin.system.exitProcess

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
            // Write the crash report to a plain text file, bypassing Activity/Looper/IPC,
            // so that it can be read with any file explorer on the phone
            // (no PC, no adb, no root).
            try {
                val crashFile = File(getExternalFilesDir(null), "overture_crash_log.txt")
                crashFile.writeText(
                    buildString {
                        append("Fecha: ${java.util.Date()}\n")
                        append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})\n\n")
                        append("Error: $err\n\n")
                        append("Stack trace:\n${err.stackTraceToString()}")
                    }
                )
            } catch (writeErr: Exception) {
                Logger.error("MainActivity", "failed to write crash file", writeErr)
            }
        
            Logger.error("MainActivity", "uncaught exception", err)
        
            // Previously, this left the process running with the Looper dead → frozen forever.
            // Now we force a clean shutdown: you'll see "Overture has stopped" instead of
            // an infinite freeze, and the file has already been saved.
            Process.killProcess(Process.myPid())
            exitProcess(10)
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
