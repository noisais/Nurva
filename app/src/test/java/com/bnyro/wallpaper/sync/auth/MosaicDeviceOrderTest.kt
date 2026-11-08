package com.bnyro.wallpaper.sync.auth

import com.bnyro.wallpaper.sync.db.SyncDeviceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MosaicDeviceOrderTest {
    private fun device(id: String, name: String, revokedAt: Long? = null) = SyncDeviceEntity(
        deviceId = id,
        groupId = "group",
        userId = "user",
        displayName = name,
        wallpaperTarget = "lock",
        applyMode = "manual",
        lastAppliedRevision = 0L,
        lastSeenAt = null,
        revokedAt = revokedAt
    )

    @Test
    fun defaultOrderIsStableAndAlphabetical() {
        val devices = listOf(device("z", "Tablet"), device("b", "Phone"), device("a", "phone"))
        val result = orderMosaicDevices(devices, null)
        assertEquals(listOf("a", "b", "z"), result.map { it.deviceId })
    }

    @Test
    fun requestedOrderIsPreservedExactly() {
        val devices = listOf(device("a", "Phone A"), device("b", "Phone B"), device("c", "Tablet"))
        val result = orderMosaicDevices(devices, listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), result.map { it.deviceId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateDeviceIdsAreRejected() {
        orderMosaicDevices(
            listOf(device("a", "Phone A"), device("b", "Phone B")),
            listOf("a", "a")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteRosterIsRejected() {
        orderMosaicDevices(
            listOf(device("a", "Phone A"), device("b", "Phone B")),
            listOf("a")
        )
    }
}
