package com.naos.pikminbee.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/** Installed package metadata, never a hard-coded release label. */
data class InstalledAppVersion(val name: String?, val code: Long?, val environment: String) {
    val displayName: String get() = name?.let {
        if (environment == "UAT") it.removeSuffix("-uat").removeSuffix(" UAT") + " UAT" else it
    } ?: "版本待確認"

    companion object {
        fun environmentFor(packageName: String): String = when {
            packageName.endsWith(".uat") -> "UAT"
            packageName.endsWith(".prod") -> "PROD"
            else -> "DEV"
        }

        @Suppress("DEPRECATION")
        fun read(context: Context): InstalledAppVersion {
            val info = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
            val environment = environmentFor(context.packageName)
            return InstalledAppVersion(info?.versionName, info?.let(PackageInfoCompat::getLongVersionCode),
                environment)
        }
    }
}
