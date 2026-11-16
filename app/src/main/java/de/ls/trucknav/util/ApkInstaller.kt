package de.ls.trucknav.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Installiert eine heruntergeladene APK ueber den System-Installer. */
object ApkInstaller {

    /** true, wenn die App APKs installieren darf ("Unbekannte Apps"). */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Fuehrt den Nutzer zu den Einstellungen fuer "Unbekannte Apps installieren". */
    fun requestInstallPermission(context: Context) {
        val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(settings) }
    }

    /** Oeffnet den System-Installer fuer [apk]. */
    fun install(context: Context, apk: File) {
        if (!canInstall(context)) {
            requestInstallPermission(context)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
