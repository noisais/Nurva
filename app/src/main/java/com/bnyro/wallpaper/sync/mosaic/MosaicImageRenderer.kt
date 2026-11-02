package com.bnyro.wallpaper.sync.mosaic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.bnyro.wallpaper.sync.db.MosaicTileAssignmentEntity
import kotlin.math.ceil
import kotlin.math.floor

object MosaicImageRenderer {
    private const val MAX_DIMENSION = 10_000
    private const val MAX_PIXELS = 60_000_000L

    fun render(source: Bitmap, tile: MosaicTileAssignmentEntity): Bitmap {
        require(!source.isRecycled && source.width > 0 && source.height > 0)
        require(tile.outputWidth in 1..MAX_DIMENSION && tile.outputHeight in 1..MAX_DIMENSION)
        require(tile.outputWidth.toLong() * tile.outputHeight <= MAX_PIXELS)
        require(tile.cropLeft.isFinite() && tile.cropTop.isFinite() && tile.cropRight.isFinite() && tile.cropBottom.isFinite())
        require(tile.cropLeft >= 0f && tile.cropTop >= 0f && tile.cropRight <= 1f && tile.cropBottom <= 1f)
        require(tile.cropLeft < tile.cropRight && tile.cropTop < tile.cropBottom)
        val left = floor(tile.cropLeft * source.width).toInt().coerceIn(0, source.width - 1)
        val top = floor(tile.cropTop * source.height).toInt().coerceIn(0, source.height - 1)
        val right = ceil(tile.cropRight * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = ceil(tile.cropBottom * source.height).toInt().coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(tile.outputWidth, tile.outputHeight, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(source, Rect(left, top, right, bottom), Rect(0, 0, tile.outputWidth, tile.outputHeight), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        }
    }
}
