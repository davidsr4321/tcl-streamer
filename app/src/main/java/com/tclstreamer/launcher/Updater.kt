package com.tclstreamer.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updates from the build CI publishes on every push. version.txt next to the APK holds the
 * build number, which is also the APK's versionCode.
 */
object Updater {
    private const val BASE = "https://github.com/davidsr4321/tcl-streamer/releases/download/latest"

    fun installedVersion(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** The newest published build number, or null when offline. */
    suspend fun latestVersion(): Long? = withContext(Dispatchers.IO) {
        runCatching { fetch("$BASE/version.txt").use { it.readBytes() }.decodeToString().trim().toLong() }.getOrNull()
    }

    /** Downloads the new build and hands it to Android, which asks for a confirmation. */
    suspend fun install(context: Context) {
        val apk = File(context.cacheDir, "update.apk")
        withContext(Dispatchers.IO) {
            fetch("$BASE/noir.apk").use { input -> apk.outputStream().use { input.copyTo(it) } }
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("dnd.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val result = PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(result.intentSender)
            }
        }
    }

    private fun fetch(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        inputStream
    }
}

/** Shows Android's install prompt, or says why the update failed. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Toast.makeText(
                context,
                "Update failed: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
