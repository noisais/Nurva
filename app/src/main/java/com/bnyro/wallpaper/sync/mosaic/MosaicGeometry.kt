package com.bnyro.wallpaper.sync.mosaic

import com.bnyro.wallpaper.sync.db.MosaicTileAssignmentEntity
import kotlin.math.abs

data class MosaicDevicePlacement(
    val deviceId: String,
    val row: Int,
    val column: Int,
    val outputWidth: Int,
    val outputHeight: Int,
)

/** Deterministic, normalized tile planner for a regular grid of similarly shaped screens. */
object MosaicGeometry {
    private const val MAX_GRID = 10
    private const val ASPECT_TOLERANCE = 0.04

    fun plan(groupId: String, revision: Long, rows: Int, columns: Int, sourceWidth: Int, sourceHeight: Int, devices: List<MosaicDevicePlacement>): List<MosaicTileAssignmentEntity> {
        require(groupId.isNotBlank())
        require(revision > 0)
        require(rows in 1..MAX_GRID && columns in 1..MAX_GRID)
        require(sourceWidth > 0 && sourceHeight > 0)
        require(devices.size == rows * columns) { "Every mosaic cell must have exactly one device" }
        require(devices.map { it.deviceId }.distinct().size == devices.size) { "Device IDs must be unique" }
        require(devices.all { it.deviceId.isNotBlank() && it.row in 0 until rows && it.column in 0 until columns })
        require(devices.map { it.row to it.column }.distinct().size == devices.size) { "Two devices cannot occupy the same cell" }
        require(devices.all { it.outputWidth > 0 && it.outputHeight > 0 })

        val aspects = devices.map { it.outputWidth.toDouble() / it.outputHeight }
        val referenceAspect = aspects.average()
        require(aspects.all { abs(it - referenceAspect) / referenceAspect <= ASPECT_TOLERANCE }) {
            "Regular mosaic grids require similar screen aspect ratios (within 4%)"
        }
        val sourceAspect = sourceWidth.toDouble() / sourceHeight
        val gridAspect = columns.toDouble() * referenceAspect / rows
        var l = 0.0; var t = 0.0; var r = 1.0; var b = 1.0
        if (sourceAspect > gridAspect) {
            val width = gridAspect / sourceAspect
            l = (1.0 - width) / 2.0; r = l + width
        } else if (sourceAspect < gridAspect) {
            val height = sourceAspect / gridAspect
            t = (1.0 - height) / 2.0; b = t + height
        }
        val visibleW = r - l; val visibleH = b - t
        return devices.sortedWith(compareBy<MosaicDevicePlacement> { it.row }.thenBy { it.column }).map { d ->
            MosaicTileAssignmentEntity(
                groupId = groupId, revision = revision, deviceId = d.deviceId,
                tileIndex = d.row * columns + d.column, rowIndex = d.row, columnIndex = d.column,
                cropLeft = (l + visibleW * d.column / columns).toFloat(),
                cropTop = (t + visibleH * d.row / rows).toFloat(),
                cropRight = (l + visibleW * (d.column + 1) / columns).toFloat(),
                cropBottom = (t + visibleH * (d.row + 1) / rows).toFloat(),
                outputWidth = d.outputWidth, outputHeight = d.outputHeight,
            )
        }
    }
}
