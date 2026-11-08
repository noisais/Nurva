package com.bnyro.wallpaper.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bnyro.wallpaper.sync.db.SyncDeviceEntity

@Composable
internal fun NurvaMosaicOrderDialog(
    devices: List<SyncDeviceEntity>,
    initialOrder: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var orderedIds by remember(devices, initialOrder) {
        val validInitial = initialOrder.filter { id -> devices.any { it.deviceId == id } }
        mutableStateOf(validInitial + devices.map { it.deviceId }.filterNot { it in validInitial })
    }
    val deviceById = remember(devices) { devices.associateBy { it.deviceId } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Arrange panorama screens") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("The first device receives the far-left slice. The last device receives the far-right slice.")
                orderedIds.forEachIndexed { index, deviceId ->
                    val device = deviceById[deviceId] ?: return@forEachIndexed
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}. ${device.displayName}",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(
                            enabled = index > 0,
                            onClick = {
                                val changed = orderedIds.toMutableList()
                                val moved = changed.removeAt(index)
                                changed.add(index - 1, moved)
                                orderedIds = changed
                            }
                        ) { Text("Up") }
                        TextButton(
                            enabled = index < orderedIds.lastIndex,
                            onClick = {
                                val changed = orderedIds.toMutableList()
                                val moved = changed.removeAt(index)
                                changed.add(index + 1, moved)
                                orderedIds = changed
                            }
                        ) { Text("Down") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(orderedIds) }) {
                Text("Choose panorama image")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
