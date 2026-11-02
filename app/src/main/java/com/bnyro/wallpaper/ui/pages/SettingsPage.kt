package com.bnyro.wallpaper.ui.pages

import android.annotation.SuppressLint
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bnyro.wallpaper.R
import com.bnyro.wallpaper.enums.ThemeMode
import com.bnyro.wallpaper.ext.formatBinarySize
import com.bnyro.wallpaper.obj.WallpaperConfig
import com.bnyro.wallpaper.ui.components.ButtonWithIcon
import com.bnyro.wallpaper.ui.components.WallpaperChangerPrefDialog
import com.bnyro.wallpaper.ui.components.prefs.CheckboxPref
import com.bnyro.wallpaper.ui.components.prefs.ListPreference
import com.bnyro.wallpaper.ui.models.MainModel
import com.bnyro.wallpaper.util.BackupHelper
import com.bnyro.wallpaper.util.Preferences
import com.bnyro.wallpaper.util.WorkerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(
    viewModel: MainModel,
    onManageAccount: () -> Unit,
    onAbout: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val activity = LocalActivity.current
    val wallpaperConfigs = remember { Preferences.getWallpaperConfigs().toMutableStateList() }
    val scope = rememberCoroutineScope()

    val createFavoritesBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupHelper.JSON_MIME)
    ) { uri ->
        scope.launch(Dispatchers.IO) {
            uri?.let { BackupHelper.backupFavorites(it, context) }
        }
    }
    val restoreFavoritesBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        scope.launch(Dispatchers.IO) {
            uri?.let { BackupHelper.restoreFavorites(it, context) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("PREFERENCES", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("Make Nurva yours", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Manage your account, appearance, wallpaper behavior, and local data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsPanel(
            title = "Account & devices",
            description = "Manage your Nurva account, shared spaces, invitations, and device permissions."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Your account and group access", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onManageAccount) {
                Text("Manage account and groups")
            }
        }

        SettingsPanel(title = "Appearance", description = "Choose the look and feel of Nurva.") {
            ListPreference(
                prefKey = Preferences.themeModeKey,
                title = stringResource(R.string.theme_mode),
                entries = listOf(
                    stringResource(R.string.theme_system),
                    stringResource(R.string.theme_light),
                    stringResource(R.string.theme_dark)
                ),
                values = (0..2).map { it.toString() },
                defaultValue = ThemeMode.AUTO.toString()
            ) {
                viewModel.themeMode = ThemeMode.entries[it.toInt()]
            }
            CheckboxPref(
                prefKey = Preferences.autoLightenDarkenKey,
                title = stringResource(R.string.auto_darken_lighten_by_theme),
                summary = stringResource(R.string.auto_darken_lighten_by_theme_desc)
            )
        }

        SettingsPanel(title = "Wallpaper behavior", description = "Control automatic changes and your local wallpaper history.") {
            CheckboxPref(
                prefKey = Preferences.wallpaperHistory,
                title = stringResource(R.string.wallpaper_history),
                defaultValue = true
            )
            var wallpaperChangerEnabled by remember {
                mutableStateOf(Preferences.getBoolean(Preferences.wallpaperChangerKey, false))
            }
            CheckboxPref(
                prefKey = Preferences.wallpaperChangerKey,
                title = stringResource(R.string.wallpaper_changer),
                summary = stringResource(R.string.wallpaper_changer_summary)
            ) { newValue ->
                wallpaperChangerEnabled = newValue
                WorkerHelper.enqueueOrCancelAll(context, wallpaperConfigs)
                if (newValue) {
                    val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
                    if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                        val intent = Intent().apply {
                            @SuppressLint("BatteryLife")
                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            data = "package:${context.packageName}".toUri()
                        }
                        activity?.startActivity(intent)
                    }
                }
            }
            AnimatedVisibility(visible = wallpaperChangerEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var editIndex by remember { mutableStateOf<Int?>(null) }
                    wallpaperConfigs.forEachIndexed { index, config ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(config.getSummary(context), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            ButtonWithIcon(icon = Icons.Default.Edit) { editIndex = index }
                            ButtonWithIcon(icon = Icons.Default.Delete) {
                                WorkerHelper.cancelWork(context, config)
                                wallpaperConfigs.removeAt(index)
                                Preferences.setWallpaperConfigs(wallpaperConfigs)
                            }
                        }
                    }
                    editIndex?.let { index ->
                        WallpaperChangerPrefDialog(
                            wallpaperConfigs[index],
                            onConfigChange = { newConfig ->
                                wallpaperConfigs[index] = newConfig
                                Preferences.setWallpaperConfigs(wallpaperConfigs)
                                WorkerHelper.enqueue(context, newConfig, true)
                            },
                            onDismissRequest = { editIndex = null }
                        )
                    }
                    var newConfig by remember { mutableStateOf<WallpaperConfig?>(null) }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val nextId = (wallpaperConfigs.maxOfOrNull { it.id } ?: -1) + 1
                            newConfig = WallpaperConfig(id = nextId)
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_wallpaper_changer_rule))
                    }
                    newConfig?.let { config ->
                        WallpaperChangerPrefDialog(
                            config,
                            onConfigChange = { updated ->
                                wallpaperConfigs.add(updated)
                                Preferences.setWallpaperConfigs(wallpaperConfigs)
                                WorkerHelper.enqueue(context, updated, true)
                            },
                            onDismissRequest = { newConfig = null }
                        )
                    }
                }
            }
        }

        val cacheSizes = listOf(16L, 32L, 64L, 128L, 256L).map { it * 1024L * 1024L }
        SettingsPanel(title = "Storage & cache", description = "Choose how much temporary image data Nurva can keep on this device.") {
            ListPreference(
                prefKey = Preferences.diskCacheKey,
                title = stringResource(R.string.coil_cache),
                entries = cacheSizes.map { it.formatBinarySize() },
                values = cacheSizes.map { it.toString() },
                defaultValue = Preferences.defaultDiskCacheSize.toString()
            )
        }

        SettingsPanel(title = "Backup & restore", description = "Move your locally saved favorites between devices.") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { restoreFavoritesBackup.launch(arrayOf(BackupHelper.JSON_MIME)) }
                ) { Text(stringResource(R.string.import_favorites)) }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { createFavoritesBackup.launch("nurva_favorites_backup.json") }
                ) { Text(stringResource(R.string.export_favorites)) }
            }
        }

        SettingsPanel(title = "About", description = "Version information, open-source licenses, and project links.") {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onAbout) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("About Nurva")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        )
    }
}
