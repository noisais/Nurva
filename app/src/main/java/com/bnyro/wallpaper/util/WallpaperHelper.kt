package com.bnyro.wallpaper.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.File
import java.io.FileInputStream
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.bnyro.wallpaper.enums.ResizeMethod
import com.bnyro.wallpaper.enums.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

object WallpaperHelper {
    private const val LIGHT_BRIGHTNESS_MIN = 0.8f
    private const val DARK_BRIGHTNESS_MAX = 0.3f

    private const val TARGET_BRIGHTNESS_DARK = 0.7f
    private const val TARGET_BRIGHTNESS_LIGHT = 0.4f

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setWallpaperUp(context: Context, imageBitmap: Bitmap, mode: Int) {
        val (width, height) = getMetrics(context)
        val wallpaperManager = WallpaperManager.getInstance(context)
        wallpaperManager.suggestDesiredDimensions(width, height)

        if (!wallpaperManager.isWallpaperSupported) return
        wallpaperManager.setBitmap(imageBitmap, null, true, mode)
    }

    private fun setWallpaperLegacy(context: Context, imageBitmap: Bitmap) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        wallpaperManager.setBitmap(imageBitmap)
    }

    suspend fun setWallpaperWithFilters(context: Context, src: Bitmap, mode: WallpaperTarget) {
        withContext(Dispatchers.IO) {
            var bitmap = BitmapProcessor.processBitmapByPrefs(src)
            bitmap = resizeBitmapByPreference(context, bitmap = bitmap)
            if (Preferences.getBoolean(Preferences.autoLightenDarkenKey, false)) {
                bitmap = lightenOrDarkenBitmapIfNeeded(context, bitmap)
            }
            setWallpaper(context, bitmap, mode)
        }
    }

    suspend fun setWallpaperWithoutFilters(context: Context, src: Bitmap, mode: WallpaperTarget) {
        withContext(Dispatchers.IO) {
            val bitmap = resizeBitmapByPreference(context, src)
            setWallpaper(context, bitmap, mode)
        }
    }

    /** Apply a verified private Nurva download without decoding the full file into memory. */
    suspend fun setWallpaperFromFile(context: Context, file: File, mode: WallpaperTarget) {
        withContext(Dispatchers.IO) {
            require(file.isFile && file.length() > 0L) { "The downloaded wallpaper file is missing." }
            val wallpaperManager = WallpaperManager.getInstance(context)
            check(wallpaperManager.isWallpaperSupported) { "This device does not support system wallpapers." }
            val flags = when (mode) {
                WallpaperTarget.BOTH -> listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK)
                WallpaperTarget.HOME -> listOf(WallpaperManager.FLAG_SYSTEM)
                WallpaperTarget.LOCK -> listOf(WallpaperManager.FLAG_LOCK)
            }
            flags.forEach { flag ->
                FileInputStream(file).use { input ->
                    wallpaperManager.setStream(input, null, true, flag)
                }
            }
        }
    }

    /** Decode and apply only this device's verified panorama slice. */
    suspend fun setWallpaperFromMosaicFile(
        context: Context,
        file: File,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
        mode: WallpaperTarget
    ) {
        withContext(Dispatchers.IO) {
            require(file.isFile && file.length() > 0L) { "The downloaded mosaic image is missing." }
            require(cropLeft >= 0f && cropTop >= 0f && cropRight <= 1f && cropBottom <= 1f &&
                cropLeft < cropRight && cropTop < cropBottom) { "The mosaic tile bounds are invalid." }
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false)
                ?: error("Nurva could not read the panorama image.")
            try {
                val sourceWidth = decoder.width
                val sourceHeight = decoder.height
                val left = (sourceWidth * cropLeft).toInt().coerceIn(0, sourceWidth - 1)
                val top = (sourceHeight * cropTop).toInt().coerceIn(0, sourceHeight - 1)
                val right = ceil(sourceWidth * cropRight).toInt().coerceIn(left + 1, sourceWidth)
                val bottom = ceil(sourceHeight * cropBottom).toInt().coerceIn(top + 1, sourceHeight)
                val region = Rect(left, top, right, bottom)
                val (targetWidth, targetHeight) = getMetrics(context)
                val sampleLimit = minOf(
                    region.width() / targetWidth.coerceAtLeast(1),
                    region.height() / targetHeight.coerceAtLeast(1)
                ).coerceAtLeast(1)
                var sampleSize = 1
                while (sampleSize <= sampleLimit / 2) sampleSize *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val tileBitmap = decoder.decodeRegion(region, options)
                    ?: error("Nurva could not decode this device's panorama slice.")
                try {
                    val resized = resizeBitmapByPreference(context, tileBitmap)
                    try {
                        check(WallpaperManager.getInstance(context).isWallpaperSupported) {
                            "This device does not support system wallpapers."
                        }
                        setWallpaper(context, resized, mode)
                    } finally {
                        if (resized !== tileBitmap) resized.recycle()
                    }
                } finally {
                    tileBitmap.recycle()
                }
            } finally {
                decoder.recycle()
            }
        }
    }

    fun setWallpaper(context: Context, bitmap: Bitmap, mode: WallpaperTarget) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (mode in listOf(WallpaperTarget.BOTH, WallpaperTarget.HOME)) {
                setWallpaperUp(context, bitmap, WallpaperManager.FLAG_SYSTEM)
            }
            if (mode in listOf(WallpaperTarget.BOTH, WallpaperTarget.LOCK)) {
                setWallpaperUp(context, bitmap, WallpaperManager.FLAG_LOCK)
            }
        } else {
            setWallpaperLegacy(context, bitmap)
        }
    }

    /** The display size in its natural orientation, which is how a wallpaper is stored. */
    private fun getMetrics(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

        // `width` and `height` depend on the current screen orientation. To get the real metrics,
        // we additionally check the current rotation of the display.
        return when (getDisplayRotation(context)) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> height to width
            else -> width to height
        }
    }

    private fun getDisplayRotation(context: Context): Int {
        val display = context.getSystemService<DisplayManager>()
            ?.getDisplay(Display.DEFAULT_DISPLAY)

        return display?.rotation ?: Surface.ROTATION_0
    }

    private fun resizeBitmapByPreference(context: Context, bitmap: Bitmap): Bitmap {
        val resizeMethod = Preferences.getString(
            Preferences.resizeMethodKey,
            ResizeMethod.ZOOM.name
        ).let { ResizeMethod.valueOf(it) }

        val (width, height) = getMetrics(context)

        return when (resizeMethod) {
            ResizeMethod.CROP -> getResizedBitmap(bitmap, width, height)
            ResizeMethod.ZOOM -> getZoomedBitmap(bitmap, width, height)
            ResizeMethod.FIT_WIDTH -> getBitmapFitWidth(bitmap, width)
            ResizeMethod.FIT_HEIGHT -> getBitmapFitHeight(bitmap, height)
            ResizeMethod.NONE -> bitmap
        }
    }

    private fun getResizedBitmap(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        filter: Boolean = true
    ): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, filter)
    }

    private fun getZoomedBitmap(bitmap: Bitmap, screenWidth: Int, screenHeight: Int): Bitmap {
        val bitmapRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val screenRatio = screenHeight.toFloat() / screenWidth.toFloat()
        val scaleRatio = (bitmapRatio / screenRatio)

        var (newWidth, newHeight) = bitmap.width to bitmap.height
        if (bitmapRatio > screenRatio) {
            newHeight = (bitmap.height / scaleRatio).toInt()
        } else {
            newWidth = (scaleRatio * bitmap.width).toInt()
        }

        val gapX = (bitmap.width - newWidth) / 2
        val gapY = (bitmap.height - newHeight) / 2
        val centeredBitmap = Bitmap.createBitmap(bitmap, gapX, gapY, newWidth, newHeight)

        return getResizedBitmap(centeredBitmap, screenWidth, screenHeight)
    }

    private fun getBitmapFitWidth(bitmap: Bitmap, width: Int): Bitmap {
        val heightRatio = width.toFloat() / bitmap.width.toFloat()

        return getResizedBitmap(bitmap, width, (bitmap.height * heightRatio).toInt())
    }

    private fun getBitmapFitHeight(bitmap: Bitmap, height: Int): Bitmap {
        val widthRatio = height.toFloat() / bitmap.height.toFloat()

        return getResizedBitmap(bitmap, (bitmap.width * widthRatio).toInt(), height)
    }

    private fun lightenOrDarkenBitmapIfNeeded(context: Context, bitmap: Bitmap): Bitmap {
        val bitmapBrightness = BitmapProcessor.calculateBrightnessEstimate(bitmap)
        val isDarkMode = ThemeHelper.isNightMode(context)

        return when {
            isDarkMode && bitmapBrightness > LIGHT_BRIGHTNESS_MIN -> {
                val newBrightness = TARGET_BRIGHTNESS_DARK / bitmapBrightness
                BitmapProcessor.changeBrightness(bitmap, newBrightness)
            }
            !isDarkMode && bitmapBrightness < DARK_BRIGHTNESS_MAX -> {
                val newBrightness = TARGET_BRIGHTNESS_LIGHT / bitmapBrightness
                BitmapProcessor.changeBrightness(bitmap, newBrightness)
            }
            else -> bitmap
        }
    }
}
