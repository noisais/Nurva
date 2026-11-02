package com.bnyro.wallpaper.sync.mosaic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicGeometryTest {
    @Test fun threePhonesReceiveContiguousHorizontalTiles() {
        val tiles = MosaicGeometry.plan("group", 1, 1, 3, 1350, 1000, listOf(
            MosaicDevicePlacement("c", 0, 2, 1080, 2400),
            MosaicDevicePlacement("a", 0, 0, 1080, 2400),
            MosaicDevicePlacement("b", 0, 1, 1080, 2400),
        ))
        assertEquals(listOf("a", "b", "c"), tiles.map { it.deviceId })
        assertEquals(0f, tiles.first().cropLeft)
        assertEquals(tiles[0].cropRight, tiles[1].cropLeft, 0.0001f)
        assertEquals(tiles[1].cropRight, tiles[2].cropLeft, 0.0001f)
        assertEquals(1f, tiles.last().cropRight)
    }

    @Test fun veryWideImageIsCenterCroppedToGridAspectRatio() {
        val tiles = MosaicGeometry.plan("group", 2, 1, 3, 6000, 1000, (0..2).map {
            MosaicDevicePlacement("p$it", 0, it, 1080, 2400)
        })
        assertTrue(tiles.first().cropLeft > 0f)
        assertTrue(tiles.last().cropRight < 1f)
    }

    @Test fun duplicateCellsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MosaicGeometry.plan("g", 1, 1, 2, 1000, 1000, listOf(
                MosaicDevicePlacement("a", 0, 0, 1000, 2000),
                MosaicDevicePlacement("b", 0, 0, 1000, 2000),
            ))
        }
    }
}
