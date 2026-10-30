package com.tclstreamer.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvContract
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
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
import java.time.Instant
import java.time.LocalDate

class HomeActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private val content = mutableStateOf<List<TvApp>?>(null)

    /** Bumped on every remote press; the screensaver timer restarts whenever it changes. */
    private val lastInput = mutableIntStateOf(0)
    private val screensaverOn = mutableStateOf(false)
    private val inFront = mutableStateOf(false)
    private var swallowKeyUp = false

    /** A newer build waiting on GitHub, if any, and whether it is downloading. */
    private val updateTo = mutableStateOf<Long?>(null)
    private val updating = mutableStateOf(false)
    private var lastUpdateCheck = 0L
    private val hdmi = mutableStateOf<List<Pair<String, String>>>(emptyList())
    /** Bumped every half minute to redraw the sleep timer's minutes left and the time of day. */
    private val tick = mutableIntStateOf(0)
    private var config: DnDConfig? = null
    private val conditions = mutableStateOf<Conditions?>(null)
    private var lastWeatherCheck = 0L
    private val occasion = mutableStateOf<Occasion?>(null)

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
                tick.intValue // re-read the time of day whenever this changes
                val ambience = Ambience(
                    daylight = daylightAt(Instant.now(), conditions.value),
                    weather = conditions.value?.weather ?: Weather.CLEAR,
                    occasion = occasion.value,
                )
                HomeScreen(
                    apps = content.value,
                    quickTiles = quickTiles(),
                    ambience = ambience,
                    temperature = conditions.value?.temperature,
                    onLaunch = ::launch,
                )

                LaunchedEffect(inFront.value) {
                    while (inFront.value) {
                        delay(30_000)
                        tick.intValue++
                        config?.let {
                            occasion.value = occasionOn(LocalDate.now(), it)
                            checkWeather(it)
                        }
                    }
                }

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
                    SwordFightScreensaver()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inFront.value = true
        wake()
        refresh()
        hdmi.value = hdmiInputs(this)
        tick.intValue++
        checkForUpdate()
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { DnDConfig.load(applicationContext) }
            config = loaded
            occasion.value = occasionOn(LocalDate.now(), loaded)
            checkWeather(loaded)
        }
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
        if (event.keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
            // Holding a number opens that app: 1 is the first tile, 2 the second, and so on.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 1) {
                content.value?.getOrNull(event.keyCode - KeyEvent.KEYCODE_1)?.let { launch(it.launchIntent) }
            }
            return true
        }
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

    private fun quickTiles(): List<QuickTile> = buildList {
        add(QuickTile("wifi", "Wi-Fi", null, QuickIcons.WIFI) { launch(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        for ((id, name) in hdmi.value) {
            add(QuickTile(id, name, "Input", QuickIcons.HDMI) {
                launch(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)))
            })
        }
        tick.intValue // re-read the minutes left whenever this changes
        val setUp = SleepTimer.isSetUp(this@HomeActivity)
        val left = SleepTimer.minutesLeft(this@HomeActivity)
        val sleepDetail = when {
            !setUp -> "Needs setup"
            left == 0 -> "Off"
            else -> "$left min left"
        }
        val release: () -> Unit = {
            SleepTimer.release(this@HomeActivity)
            tick.intValue++
        }
        add(QuickTile("sleep", "Sleep timer", sleepDetail, QuickIcons.MOON, onLongClick = release) {
            if (setUp) SleepTimer.cycle(this@HomeActivity)
            tick.intValue++
        })
        updateTo.value?.let { build ->
            val detail = if (updating.value) "Downloading…" else "Build $build"
            add(QuickTile("update", "Update D&D", detail, QuickIcons.UPDATE, onClick = ::startUpdate))
        }
        add(QuickTile("settings", "Settings", null, QuickIcons.GEAR) { launch(Intent(Settings.ACTION_SETTINGS)) })
    }

    private suspend fun checkWeather(config: DnDConfig) {
        val now = SystemClock.elapsedRealtime()
        if (lastWeatherCheck != 0L && now - lastWeatherCheck < WEATHER_CHECK_MS) return
        lastWeatherCheck = now
        WeatherService.fetch(config)?.let { conditions.value = it }
    }

    private fun checkForUpdate() {
        val now = SystemClock.elapsedRealtime()
        if (lastUpdateCheck != 0L && now - lastUpdateCheck < UPDATE_CHECK_MS) return
        lastUpdateCheck = now
        lifecycleScope.launch {
            val latest = Updater.latestVersion() ?: return@launch
            if (latest > Updater.installedVersion(this@HomeActivity)) updateTo.value = latest
        }
    }

    private fun startUpdate() {
        if (updating.value) return
        if (!packageManager.canRequestPackageInstalls()) {
            // Android has to allow D&D to install apps first. On TVs without this screen, the
            // README has the adb command instead.
            launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        updating.value = true
        lifecycleScope.launch {
            runCatching { Updater.install(this@HomeActivity) }
                .onFailure { Toast.makeText(this@HomeActivity, "Couldn't download the update", Toast.LENGTH_LONG).show() }
            updating.value = false
        }
    }

    private companion object {
        /** How long the remote has to sit untouched before the screensaver comes out. */
        const val IDLE_MS = 60_000L
        const val UPDATE_CHECK_MS = 3 * 60 * 60 * 1000L
        const val WEATHER_CHECK_MS = 30 * 60 * 1000L
    }
}
