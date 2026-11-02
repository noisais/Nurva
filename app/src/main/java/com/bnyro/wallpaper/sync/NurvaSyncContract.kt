package com.bnyro.wallpaper.sync

object NurvaSyncContract {
    object Role { const val OWNER = "owner"; const val ADMIN = "admin"; const val EDITOR = "editor"; const val VIEWER = "viewer" }
    object WallpaperTarget { const val LOCK = "lock"; const val HOME = "home"; const val BOTH = "both" }
    object ApplyMode { const val AUTO = "auto"; const val MANUAL = "manual" }
    object LayoutMode { const val SHARED = "shared"; const val PER_DEVICE = "per_device"; const val MOSAIC = "mosaic" }
    object LocalAssetState { const val PENDING_DOWNLOAD = "pending_download"; const val READY = "ready"; const val FAILED = "failed" }
    object Operation { const val PUBLISH_WALLPAPER = "publish_wallpaper"; const val ACKNOWLEDGE_REVISION = "acknowledge_revision"; const val UPDATE_DEVICE_PREFERENCES = "update_device_preferences"; const val APPLY_SCHEDULED_THEME = "apply_scheduled_theme" }
}
