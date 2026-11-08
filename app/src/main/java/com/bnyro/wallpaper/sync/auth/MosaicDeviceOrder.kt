package com.bnyro.wallpaper.sync.auth

import com.bnyro.wallpaper.sync.db.SyncDeviceEntity

/** Validates a user-selected left-to-right mosaic order against the current active roster. */
internal fun orderMosaicDevices(
    devices: List<SyncDeviceEntity>,
    requestedDeviceOrder: List<String>?
): List<SyncDeviceEntity> {
    val activeDevices = devices.filter { it.revokedAt == null }
    require(activeDevices.size in 2..100) {
        "A panorama mosaic needs 2–100 active devices in this group. Refresh the group and try again."
    }
    val defaultOrder = activeDevices
        .sortedWith(compareBy<SyncDeviceEntity> { it.displayName.lowercase() }.thenBy { it.deviceId })
        .map { it.deviceId }
    val order = requestedDeviceOrder ?: defaultOrder
    val activeIds = activeDevices.map { it.deviceId }.toSet()
    require(
        order.size == activeDevices.size &&
            order.distinct().size == order.size &&
            order.toSet() == activeIds
    ) {
        "The active device list changed while you were arranging the panorama. Refresh the group and try again."
    }
    val byId = activeDevices.associateBy { it.deviceId }
    return order.map { deviceId ->
        byId[deviceId] ?: error("The selected panorama device is no longer active. Refresh and try again.")
    }
}
