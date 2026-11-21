package com.shod.client.platform.android.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale

/**
 * Reads installed applications for the split-tunnel picker.
 *
 * Call from an IO dispatcher: PackageManager metadata and drawable loading can
 * be expensive and must not run on the UI thread.
 */
internal class InstalledAppCatalog(
    context: Context,
) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    fun loadSelectableApps(): List<InstalledAppEntry> {
        val packageManager = appContext.packageManager

        return packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { app ->
                AppRoutingCatalogPolicy.isSelectable(
                    packageName = app.packageName,
                    shodPackageName = appContext.packageName,
                )
            }
            .map { app ->
                InstalledAppEntry(
                    label = app.loadLabel(packageManager).toString(),
                    packageName = app.packageName,
                    icon = runCatching {
                        app.loadIcon(packageManager).toBitmap()
                    }.getOrNull(),
                    isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedWith(
                compareBy<InstalledAppEntry> { it.isSystem }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName },
            )
            .toList()
    }
}
