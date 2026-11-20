package com.carlosrpg.cherry.quicksettings.settings

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The newest main build, as CI publishes it to GitHub Pages (`latest/`), and its install through
 * a PackageInstaller session: Android shows its own confirm screen, as for a file-manager install.
 */
object AppUpdater {
    private const val BASE = "https://carlosrpg.github.io/QuickSettings/latest/"
    const val ACTION_RESULT = "com.carlosrpg.cherry.quicksettings.UPDATE_RESULT"
    const val RESULT_PERMISSION = "com.carlosrpg.cherry.quicksettings.permission.UPDATE_RESULT"

    /** The published version name. Blocking: call off the main thread. */
    fun latestVersion(): String = JSONObject(get("version.json").use { it.bufferedReader().readText() }).getString("version")

    /** True when [latest] is a higher MAJOR.MINOR.PATCH than [current]; suffixes are ignored. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = code(latest) ?: return false
        return a > (code(current) ?: return true)
    }

    private fun code(version: String): Long? {
        val parts = version.substringBefore('-').split('.').map { it.toLongOrNull() ?: return null }
        if (parts.size != 3) return null
        return parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
    }

    /** Downloads the APK to the cache. Blocking. */
    fun download(context: Context): File {
        val file = File(context.cacheDir, "update.apk")
        get("QuickSettings.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    /** Commits [apk] to a session; the result arrives as an [ACTION_RESULT] broadcast. Blocking. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Mutable by default at API 30: the installer adds the status extras to this intent.
            val result = PendingIntent.getBroadcast(
                context, id, Intent(ACTION_RESULT).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(result.intentSender)
        }
    }

    /** Fallback when the install is refused: copies [apk] to Download/ for the file manager. Blocking. */
    fun saveToDownloads(context: Context, apk: File): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "QuickSettings-update.apk")
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { out -> apk.inputStream().use { it.copyTo(out) } }
    }.isSuccess

    private fun get(path: String) = (URL(BASE + path).openConnection() as HttpURLConnection).run {
        connectTimeout = 10_000
        readTimeout = 30_000
        useCaches = false
        inputStream
    }
}
