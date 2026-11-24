package com.naos.pikminbee.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/** Installed package metadata, never a hard-coded release label. */
data class InstalledAppVersion(val name: String?, val code: Long?, val environment: String) {
    val displayName: String get() = name?.let {
        if (environment == "UAT") it.removeSuffix("-uat").removeSuffix(" UAT") + " UAT" else it
    } ?: "版本待確認"

    companion object {
        @Suppress("DEPRECATION")
        fun read(context: Context): InstalledAppVersion {
            val info = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
            val environment = if (context.packageName.endsWith(".uat")) "UAT" else "DEV"
            return InstalledAppVersion(info?.versionName, info?.let(PackageInfoCompat::getLongVersionCode),
                environment)
        }
    }
}
