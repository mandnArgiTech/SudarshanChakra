package com.sudarshanchakra.mdm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URL

class SilentInstaller(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    fun downloadAndInstall(apkUrl: String) {
        scope.launch(Dispatchers.IO) {
            var session: PackageInstaller.Session? = null
            try {
                Log.i(TAG, "Downloading APK from $apkUrl")
                val connection = URL(apkUrl).openConnection()
                val contentLength = connection.contentLength
                if (contentLength > MAX_APK_SIZE_BYTES) {
                    Log.w(TAG, "APK too large: $contentLength bytes (max $MAX_APK_SIZE_BYTES)")
                    return@launch
                }

                val inputStream = connection.getInputStream()

                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                )
                val sessionId = installer.createSession(params)
                session = installer.openSession(sessionId)

                session.openWrite("app_update.apk", 0, -1).use { out ->
                    inputStream.use { input ->
                        input.copyTo(out)
                    }
                    session.fsync(out)
                }

                val callbackIntent = Intent(ACTION_INSTALL_COMPLETE).apply {
                    setPackage(context.packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )

                session.commit(pendingIntent.intentSender)
                Log.i(TAG, "PackageInstaller session committed (id=$sessionId)")
            } catch (e: IOException) {
                Log.e(TAG, "APK download/install failed", e)
                session?.abandon()
            } catch (e: SecurityException) {
                Log.e(TAG, "Install not permitted", e)
                session?.abandon()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected install error", e)
                session?.abandon()
            }
        }
    }

    companion object {
        private const val TAG = "SilentInstaller"
        const val ACTION_INSTALL_COMPLETE = "com.sudarshanchakra.INSTALL_COMPLETE"
        private const val MAX_APK_SIZE_BYTES = 200 * 1024 * 1024 // 200 MB
    }
}
