package com.tclstreamer.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private val content = mutableStateOf<List<TvApp>?>(null)

    /** Bumped on every remote press; the screensaver timer restarts whenever it changes. */
    private val lastInput = mutableIntStateOf(0)
    private val screensaverOn = mutableStateOf(false)
    private val inFront = mutableStateOf(false)
    private var swallowKeyUp = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            repository.invalidate()
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(applicationContext)

        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            // Home is the bottom of the stack: Back has nowhere to go.
            BackHandler {}
            Box {
                HomeScreen(apps = content.value, loadShelfArt = repository::loadShelfArt, onLaunch = ::launch)

                LaunchedEffect(lastInput.intValue, inFront.value) {
                    if (!inFront.value) return@LaunchedEffect
                    delay(IDLE_MS)
                    screensaverOn.value = true
                }
                AnimatedVisibility(
                    visible = screensaverOn.value,
                    enter = fadeIn(tween(1200)),
                    exit = fadeOut(tween(250)),
                ) {
                    PixelPalsScreensaver()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inFront.value = true
        wake()
        refresh()
    }

    override fun onPause() {
        // Never let the screensaver run behind another app.
        inFront.value = false
        screensaverOn.value = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home button while we are already in front.
        wake()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && screensaverOn.value) {
            // The first press only dismisses the screensaver; it never moves focus or opens an app.
            wake()
            swallowKeyUp = true
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && swallowKeyUp) {
            swallowKeyUp = false
            return true
        }
        lastInput.intValue++
        return super.dispatchKeyEvent(event)
    }

    private fun wake() {
        screensaverOn.value = false
        lastInput.intValue++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun refresh() {
        lifecycleScope.launch {
            content.value = withContext(Dispatchers.IO) { repository.load() }
        }
    }

    private fun launch(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    private companion object {
        /** How long the remote has to sit untouched before the screensaver comes out. */
        const val IDLE_MS = 60_000L
    }
}
