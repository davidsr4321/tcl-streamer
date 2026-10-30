package com.tclstreamer.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * Turns the TV off after a while. Switching the screen off needs D&D to be a device admin, which
 * is a one-off adb command (see the README); without it the tile says so.
 */
object SleepTimer {
    /** Each press steps through these, in minutes. 0 is off. */
    private val STEPS = intArrayOf(0, 30, 60, 90, 120)
    private const val PREFS = "sleep"
    private const val KEY_ENDS = "ends_at"

    fun isSetUp(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return dpm.isAdminActive(ComponentName(context, SleepAdmin::class.java))
    }

    /** Minutes left, rounded up, or 0 when off. Uses elapsed time, so clock changes don't matter. */
    fun minutesLeft(context: Context): Int {
        val ends = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ENDS, 0L)
        val left = ends - SystemClock.elapsedRealtime()
        return if (left <= 0) 0 else ((left + 59_999) / 60_000).toInt()
    }

    /** Moves to the next step up from what is left, wrapping round to off. */
    fun cycle(context: Context) {
        val left = minutesLeft(context)
        val next = STEPS.firstOrNull { it > left } ?: 0
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, SleepAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (next == 0) {
            alarms.cancel(pending)
            prefs.remove(KEY_ENDS).apply()
            return
        }
        val at = SystemClock.elapsedRealtime() + next * 60_000L
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
        prefs.putLong(KEY_ENDS, at).apply()
    }

    /**
     * Gives up the device-admin role, which Android otherwise insists on before D&D can be
     * uninstalled. Holding OK on the sleep timer tile calls this.
     */
    fun release(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        dpm.removeActiveAdmin(ComponentName(context, SleepAdmin::class.java))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ENDS).apply()
    }

    fun sleepNow(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ENDS).apply()
        if (isSetUp(context)) context.getSystemService(DevicePolicyManager::class.java).lockNow()
    }
}

class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SleepTimer.sleepNow(context)
}

/** Lets the sleep timer switch the screen off. It asks for nothing else. */
class SleepAdmin : DeviceAdminReceiver()
