package com.bnyro.wallpaper.sync.auth

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.bnyro.wallpaper.BuildConfig
import com.bnyro.wallpaper.db.DatabaseHolder
import com.bnyro.wallpaper.enums.WallpaperTarget
import com.bnyro.wallpaper.sync.db.SyncDeviceEntity
import com.bnyro.wallpaper.sync.db.SyncGroupEntity
import com.bnyro.wallpaper.sync.db.SyncGroupMemberEntity
import com.bnyro.wallpaper.sync.db.MosaicTileAssignmentEntity
import com.bnyro.wallpaper.sync.db.SyncOutboxEntity
import com.bnyro.wallpaper.sync.db.SyncThemeEntity
import com.bnyro.wallpaper.sync.db.SyncThemeItemEntity
import com.bnyro.wallpaper.sync.db.SyncWallpaperRevisionEntity
import com.bnyro.wallpaper.sync.security.DeviceCredentialStore
import com.bnyro.wallpaper.util.WallpaperHelper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Small, password-auth + RPC client. It never embeds or accepts a Supabase secret/service-role key. */
class NurvaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sessionStore = SecureSessionStore(appContext)
    private val deviceCredentials = DeviceCredentialStore(appContext)
    private val localIndex = LocalDeviceIndex(appContext)
    private val dao = DatabaseHolder.Database.nurvaSyncDao()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.NURVA_SUPABASE_URL.trim().trimEnd('/')
    private val publishableKey = BuildConfig.NURVA_SUPABASE_PUBLISHABLE_KEY.trim()

    fun isConfigured(): Boolean =
        baseUrl.startsWith("https://") && publishableKey.startsWith("sb_publishable_")

    fun currentSession(): AuthSession? = sessionStore.load()
    fun localGroupIds(userId: String): Set<String> = localIndex.groupIds(userId)

    fun signUp(email: String, password: String): AuthSession? {
        requireConfiguration()
        require(email.isNotBlank() && password.length >= 8) { "Enter a valid email and a password with at least 8 characters." }
        val response = requestJson(
            path = "/auth/v1/signup",
            body = JSONObject().put("email", email.trim()).put("password", password),
            bearer = null
        )
        return adoptSession(response)
    }

    fun signIn(email: String, password: String): AuthSession {
        requireConfiguration()
        require(email.isNotBlank() && password.isNotBlank()) { "Enter your email and password." }
        val response = requestJson(
            path = "/auth/v1/token?grant_type=password",
            body = JSONObject().put("email", email.trim()).put("password", password),
            bearer = null
        )
        return adoptSession(response) ?: error("No session was returned. Confirm your email, then sign in again.")
    }

    fun signOut() {
        val session = sessionStore.load()
        try {
            if (session != null && isConfigured()) {
                requestText("/auth/v1/logout", JSONObject(), session.accessToken)
            }
        } finally {
            sessionStore.clear()
        }
    }

    suspend fun createGroup(name: String): String {
        require(name.trim().length in 1..60) { "Group names must be 1–60 characters." }
        val session = validSession()
        val identity = deviceCredentials.createIdentity()
        val deviceName = deviceName()
        val groupId = try {
            val raw = rpc(
                "nurva_create_group",
                JSONObject()
                    .put("p_name", name.trim())
                    .put("p_device", identity.deviceId)
                    .put("p_secret", identity.secret)
                    .put("p_device_name", deviceName),
                session
            )
            raw.trim().removeSurrounding("\"").also {
                require(UUID_PATTERN.matches(it)) { "The server returned an invalid group ID." }
            }
        } catch (error: Exception) {
            deviceCredentials.deleteIdentity(identity.deviceId)
            throw error
        }
        localIndex.remember(session.userId, groupId, identity.deviceId)
        // The group creation is already committed. A second read may fail if connectivity drops.
        runCatching { refreshGroup(groupId) }.onFailure {
            val now = System.currentTimeMillis()
            dao.upsertGroup(
                SyncGroupEntity(
                    groupId = groupId,
                    displayName = name.trim(),
                    ownerUserId = session.userId,
                    currentRevision = 0L,
                    currentThemeId = null,
                    currentAssetPath = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return groupId
    }

    suspend fun joinGroup(inviteToken: String): String {
        val token = inviteToken.trim().let { value ->
            if (value.startsWith("nurva://")) Uri.parse(value).getQueryParameter("token") ?: value else value
        }
        require(token.length in 32..256) { "Paste a valid Nurva invite link or token." }
        val session = validSession()
        val identity = deviceCredentials.createIdentity()
        val groupId = try {
            val raw = rpc(
                "nurva_join_with_invite",
                JSONObject()
                    .put("p_token", token)
                    .put("p_device", identity.deviceId)
                    .put("p_secret", identity.secret)
                    .put("p_name", deviceName())
                    .put("p_target", "lock")
                    .put("p_mode", "manual"),
                session
            )
            val row = JSONArray(raw).optJSONObject(0) ?: error("The server did not return the joined group.")
            row.getString("group_id").also {
                require(UUID_PATTERN.matches(it)) { "The server returned an invalid group ID." }
            }
        } catch (error: Exception) {
            deviceCredentials.deleteIdentity(identity.deviceId)
            throw error
        }
        localIndex.remember(session.userId, groupId, identity.deviceId)
        // The join already succeeded remotely; preserve the device registration if the follow-up read drops.
        runCatching { refreshGroup(groupId) }.onFailure {
            val now = System.currentTimeMillis()
            dao.upsertGroup(
                SyncGroupEntity(
                    groupId = groupId,
                    displayName = "Joined group · refresh needed",
                    ownerUserId = "",
                    currentRevision = 0L,
                    currentThemeId = null,
                    currentAssetPath = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return groupId
    }

    fun createInvite(groupId: String, role: String): String {
        require(role == "editor" || role == "viewer") { "Choose editor or viewer access." }
        val session = validSession()
        val identity = localIdentity(session.userId, groupId)
        val expiresAt = Instant.now().plus(Duration.ofDays(7)).toString()
        val raw = rpc(
            "nurva_create_invite",
            JSONObject()
                .put("p_group", groupId)
                .put("p_device", identity.deviceId)
                .put("p_secret", identity.secret)
                .put("p_role", role)
                .put("p_expires", expiresAt)
                .put("p_uses", 10),
            session
        )
        val invite = JSONArray(raw).optJSONObject(0) ?: error("The server did not return an invitation.")
        val token = invite.getString("invite_token")
        return Uri.Builder()
            .scheme("nurva")
            .authority("join")
            .appendQueryParameter("token", token)
            .build()
            .toString()
    }

    suspend fun refreshGroups() {
        val session = validSession()
        localIndex.groupIds(session.userId).forEach { groupId ->
            refreshGroup(groupId)
        }
    }

    suspend fun refreshGroup(groupId: String) {
        val session = validSession()
        val identity = localIdentity(session.userId, groupId)
        val stateText = rpc(
            "nurva_get_sync_state",
            JSONObject()
                .put("p_group", groupId)
                .put("p_device", identity.deviceId)
                .put("p_secret", identity.secret),
            session
        )
        persistSnapshot(groupId, JSONObject(stateText))
    }

    /**
     * Uploads an image through a short-lived signed Storage capability, then asks the
     * publish Edge Function to verify its actual bytes and create the next group revision.
     */
    suspend fun publishWallpaper(
        groupId: String,
        imageUri: Uri,
        title: String? = null,
        mosaic: Boolean = false,
        mosaicDeviceOrder: List<String>? = null
    ): Long {
        val session = validSession()
        val identity = localIdentity(session.userId, groupId)
        val mimeType = imageMimeType(imageUri)
        val tempFile = File.createTempFile("nurva-upload-", ".img", appContext.cacheDir)
        try {
            copyImageToFile(imageUri, tempFile)
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, dimensions)
            val width = dimensions.outWidth
            val height = dimensions.outHeight
            check(width in 1..30_000 && height in 1..30_000 && width.toLong() * height <= MAX_IMAGE_PIXELS) {
                "This image is too large. Nurva allows up to 60 million pixels."
            }

            // Generate the roster-ordered tile plan before upload. The server stores it
            // in the same transaction that creates the revision, avoiding a full-image race.
            val mosaicTiles = if (mosaic) buildMosaicTiles(groupId, mosaicDeviceOrder) else null
            val assetId = UUID.randomUUID().toString()
            val signedUpload = requestJson(
                "/functions/v1/sign-wallpaper-upload",
                JSONObject()
                    .put("groupId", groupId)
                    .put("deviceId", identity.deviceId)
                    .put("deviceSecret", identity.secret)
                    .put("assetId", assetId)
                    .put("mimeType", mimeType),
                session.accessToken
            )
            val uploadUrl = requireTrustedStorageUrl(signedUpload.getString("signedUrl"))
            check(signedUpload.optString("token").isNotBlank()) {
                "The server returned an invalid upload authorization."
            }

            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .put(tempFile.asRequestBody(mimeType.toMediaType()))
                .header("apikey", publishableKey)
                .header("Content-Type", mimeType)
                .header("cache-control", "max-age=3600")
                .header("x-upsert", "false")
                .build()
            http.newCall(uploadRequest).execute().use { response ->
                check(response.isSuccessful) { "Wallpaper upload failed (HTTP ${response.code})." }
            }

            val publishBody = JSONObject()
                .put("groupId", groupId)
                .put("deviceId", identity.deviceId)
                .put("deviceSecret", identity.secret)
                .put("assetId", assetId)
                .put("mimeType", mimeType)
                .put("title", title?.trim()?.take(120))
            if (mosaicTiles != null) publishBody.put("mosaicTiles", mosaicTiles)
            val published = requestJson(
                "/functions/v1/publish-wallpaper",
                publishBody,
                session.accessToken
            )

            // The server has committed the revision. A failed follow-up read must not
            // turn a successful publish into a misleading upload failure.
            runCatching { refreshGroup(groupId) }
            val publishedRevision = published.optLong("revision", 0L)
            val currentRevision = dao.getGroup(groupId)?.currentRevision ?: 0L
            val revision = maxOf(publishedRevision, currentRevision)
            check(revision > 0L) {
                "Wallpaper was published, but its revision could not be confirmed. Refresh the group."
            }
            return revision
        } finally {
            tempFile.delete()
        }
    }

    /** Downloads the group's latest private revision and verifies its SHA-256 before saving. */
    suspend fun downloadCurrentWallpaper(groupId: String): File {
        refreshGroup(groupId)
        val group = dao.getGroup(groupId) ?: error("Group information is not available.")
        check(group.currentRevision > 0L) { "This group has not published a wallpaper yet." }
        val revision = dao.getLatestRevision(groupId)
            ?.takeIf { it.revision == group.currentRevision }
            ?: error("Wallpaper metadata is not available. Refresh the group and try again.")

        val extension = when (revision.mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> error("The group wallpaper has an unsupported image type.")
        }
        val directory = File(appContext.noBackupFilesDir, "nurva-wallpapers/$groupId")
        check(directory.isDirectory || directory.mkdirs()) { "Could not create private wallpaper storage." }
        val destination = File(directory, "revision-${revision.revision}.$extension")

        if (destination.isFile && destination.length() == revision.byteSize &&
            sha256File(destination).equals(revision.sha256, ignoreCase = true)
        ) {
            dao.updateRevisionLocalState(revision.revisionId, "ready", destination.absolutePath)
            return destination
        }

        val session = validSession()
        val identity = localIdentity(session.userId, groupId)
        val downloadInfo = requestJson(
            "/functions/v1/sign-wallpaper-download",
            JSONObject()
                .put("groupId", groupId)
                .put("deviceId", identity.deviceId)
                .put("deviceSecret", identity.secret)
                .put("revision", revision.revision),
            session.accessToken
        )
        check(downloadInfo.optLong("revision") == revision.revision) {
            "The server returned a different wallpaper revision."
        }
        check(downloadInfo.optString("sha256").equals(revision.sha256, ignoreCase = true)) {
            "The server wallpaper checksum does not match the group metadata."
        }
        val downloadUrl = requireTrustedStorageUrl(downloadInfo.getString("url"))
        val temporaryFile = File.createTempFile("nurva-download-", ".part", directory)
        try {
            val request = Request.Builder().url(downloadUrl).get().build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Wallpaper download failed (HTTP ${response.code})." }
                val input = response.body?.byteStream()
                    ?: error("The wallpaper download returned an empty response.")
                val digest = MessageDigest.getInstance("SHA-256")
                var totalBytes = 0L
                input.use { source ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            check(totalBytes <= MAX_WALLPAPER_BYTES) {
                                "Downloaded wallpaper exceeds Nurva's 50 MiB limit."
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                check(totalBytes == revision.byteSize) {
                    "Downloaded wallpaper size does not match the published revision."
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                check(actualHash.equals(revision.sha256, ignoreCase = true)) {
                    "Wallpaper verification failed. The downloaded image was not saved."
                }
            }

            if (destination.exists()) check(destination.delete()) { "Could not replace the cached wallpaper." }
            check(temporaryFile.renameTo(destination)) { "Could not save the downloaded wallpaper." }
            dao.updateRevisionLocalState(revision.revisionId, "ready", destination.absolutePath)
            return destination
        } finally {
            temporaryFile.delete()
        }
    }

    /** Save this device's target screens and whether new wallpapers need approval. */
    suspend fun updateDevicePreferences(groupId: String, target: String, mode: String) {
        require(target in setOf("lock", "home", "both")) { "Choose lock screen, home screen or both." }
        require(mode in setOf("auto", "manual")) { "Choose automatic apply or manual approval." }
        val session = validSession()
        val identity = localIdentity(session.userId, groupId)
        rpc(
            "nurva_update_device_preferences",
            JSONObject()
                .put("p_group", groupId)
                .put("p_device", identity.deviceId)
                .put("p_secret", identity.secret)
                .put("p_target", target)
                .put("p_mode", mode),
            session
        )
        refreshGroup(groupId)
    }

    /**
     * Poll every joined group, persist an apply operation locally, and process due automatic
     * operations. WorkManager requires a network connection and retries this worker on failure.
     */
    suspend fun syncAndApplyAvailableWallpapers() {
        val session = currentSession() ?: return
        localIndex.groupIds(session.userId).forEach { groupId ->
            refreshGroup(groupId)
            val deviceId = localIndex.deviceId(session.userId, groupId) ?: return@forEach
            val device = dao.getDevice(deviceId) ?: return@forEach
            val group = dao.getGroup(groupId) ?: return@forEach
            val revision = dao.getLatestRevision(groupId)
                ?.takeIf { it.revision == group.currentRevision }
                ?: return@forEach
            if (revision.revision <= device.lastAppliedRevision) return@forEach

            val operationId = wallpaperApplyOperationId(groupId, revision.revision)
            val existing = dao.getOperation(operationId)
            val nextAttempt = if (device.applyMode == "auto") System.currentTimeMillis() else Long.MAX_VALUE
            if (existing == null) {
                dao.enqueue(
                    SyncOutboxEntity(
                        operationId = operationId,
                        groupId = groupId,
                        operationType = "apply_wallpaper",
                        payloadJson = JSONObject()
                            .put("groupId", groupId)
                            .put("revision", revision.revision)
                            .toString(),
                        createdAt = System.currentTimeMillis(),
                        attempts = 0,
                        nextAttemptAt = nextAttempt,
                        lastError = if (device.applyMode == "manual") "Waiting for approval" else null
                    )
                )
            } else if (device.applyMode == "manual" && existing.nextAttemptAt != Long.MAX_VALUE) {
                dao.scheduleOperation(operationId, Long.MAX_VALUE, "Waiting for approval")
            } else if (device.applyMode == "auto" && existing.nextAttemptAt == Long.MAX_VALUE) {
                dao.scheduleOperation(operationId, System.currentTimeMillis(), null)
            }
        }
        processQueuedWallpaperApplications()
    }

    /** Explicit user approval uses the same verified download/apply path as automatic sync. */
    suspend fun applyLatestWallpaper(groupId: String): Long {
        refreshGroup(groupId)
        val session = validSession()
        val deviceId = localIndex.deviceId(session.userId, groupId)
            ?: error("This device is not registered in that group.")
        var device = dao.getDevice(deviceId)
            ?: error("This device's settings are not available. Refresh the group.")
        val group = dao.getGroup(groupId)
            ?: error("Group information is not available.")
        check(group.currentRevision > 0L) { "This group has not published a wallpaper yet." }
        if (device.lastAppliedRevision >= group.currentRevision) {
            dao.completeOperation(wallpaperApplyOperationId(groupId, group.currentRevision))
            return group.currentRevision
        }

        // This call refreshes the server state again and returns the exact revision file.
        val file = downloadCurrentWallpaper(groupId)
        val downloadedRevision = file.nameWithoutExtension
            .removePrefix("revision-")
            .toLongOrNull()
            ?: error("The downloaded wallpaper revision could not be identified.")
        device = dao.getDevice(deviceId)
            ?: error("This device's settings are not available.")
        if (downloadedRevision > device.lastAppliedRevision) {
            val target = when (device.wallpaperTarget) {
                "home" -> WallpaperTarget.HOME
                "both" -> WallpaperTarget.BOTH
                else -> WallpaperTarget.LOCK
            }
            val mosaicTile = dao.getMosaicTiles(groupId, downloadedRevision)
                .firstOrNull { it.deviceId == deviceId }
            if (mosaicTile == null) {
                WallpaperHelper.setWallpaperFromFile(appContext, file, target)
            } else {
                WallpaperHelper.setWallpaperFromMosaicFile(
                    appContext, file,
                    mosaicTile.cropLeft, mosaicTile.cropTop,
                    mosaicTile.cropRight, mosaicTile.cropBottom, target
                )
            }
            rpc(
                "nurva_mark_revision_applied",
                JSONObject()
                    .put("p_group", groupId)
                    .put("p_device", deviceId)
                    .put("p_secret", localIdentity(session.userId, groupId).secret)
                    .put("p_revision", downloadedRevision),
                validSession()
            )
            dao.markRevisionApplied(deviceId, downloadedRevision)
        }
        dao.completeOperation(wallpaperApplyOperationId(groupId, downloadedRevision))
        return downloadedRevision
    }

    private suspend fun processQueuedWallpaperApplications() {
        val due = dao.getDueOperations(System.currentTimeMillis())
        for (operation in due) {
            if (operation.operationType != "apply_wallpaper") continue
            val groupId = runCatching { JSONObject(operation.payloadJson).getString("groupId") }.getOrNull()
            if (groupId.isNullOrBlank()) {
                dao.completeOperation(operation.operationId)
                continue
            }
            val session = currentSession() ?: return
            val deviceId = localIndex.deviceId(session.userId, groupId)
            if (deviceId == null) {
                dao.completeOperation(operation.operationId)
                continue
            }
            val device = dao.getDevice(deviceId) ?: continue
            if (device.applyMode != "auto") {
                dao.scheduleOperation(operation.operationId, Long.MAX_VALUE, "Waiting for approval")
                continue
            }
            try {
                applyLatestWallpaper(groupId)
                dao.completeOperation(operation.operationId)
            } catch (error: Exception) {
                // Persist the failed operation as immediately due. WorkManager owns the
                // exponential delay and waits for CONNECTED before attempting it again.
                dao.recordRetry(
                    operation.operationId,
                    System.currentTimeMillis(),
                    error.message?.take(500)
                )
                throw error
            }
        }
    }

    private fun wallpaperApplyOperationId(groupId: String, revision: Long): String =
        "wallpaper_apply:$groupId:$revision"

    private suspend fun buildMosaicTiles(
        groupId: String,
        requestedDeviceOrder: List<String>?
    ): JSONArray {
        val devices = orderMosaicDevices(dao.getActiveDevices(groupId), requestedDeviceOrder)
        val metrics = appContext.resources.displayMetrics
        val outputWidth = metrics.widthPixels.coerceIn(1, 10_000)
        val outputHeight = metrics.heightPixels.coerceIn(1, 10_000)
        return JSONArray().apply {
            devices.forEachIndexed { index, device ->
                put(
                    JSONObject()
                        .put("deviceId", device.deviceId)
                        .put("tileIndex", index)
                        .put("rowIndex", 0)
                        .put("columnIndex", index)
                        .put("cropLeft", index.toDouble() / devices.size)
                        .put("cropTop", 0.0)
                        .put("cropRight", (index + 1).toDouble() / devices.size)
                        .put("cropBottom", 1.0)
                        .put("outputWidth", outputWidth)
                        .put("outputHeight", outputHeight)
                )
            }
        }
    }

    private fun imageMimeType(uri: Uri): String {
        val providerType = appContext.contentResolver.getType(uri)?.substringBefore(';')?.lowercase()
        if (providerType != null && providerType in SUPPORTED_IMAGE_TYPES) return providerType
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> error("Choose a JPEG, PNG or WebP image.")
        }
    }

    private fun copyImageToFile(uri: Uri, destination: File) {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: error("Nurva could not open the selected image.")
        var totalBytes = 0L
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    check(totalBytes <= MAX_WALLPAPER_BYTES) { "Choose an image smaller than 50 MiB." }
                    output.write(buffer, 0, count)
                }
            }
        }
        check(totalBytes > 0L) { "The selected image is empty." }
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Only HTTPS URLs on this Supabase project's own host are accepted. */
    private fun requireTrustedStorageUrl(raw: String): String {
        val value = raw.trim()
        val resolved = when {
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("/storage/v1/") -> baseUrl + value
            value.startsWith("/object/") -> "$baseUrl/storage/v1$value"
            else -> error("The server returned an invalid Storage URL.")
        }
        val parsed = Uri.parse(resolved)
        val expectedHost = Uri.parse(baseUrl).host
        check(parsed.scheme == "https" && !expectedHost.isNullOrBlank() && parsed.host == expectedHost) {
            "The server returned an untrusted Storage URL."
        }
        return resolved
    }

    suspend fun revokeDevice(groupId: String, targetDeviceId: String): Boolean {
        val session = validSession()
        val identity = localIdentity(session.userId, groupId)
        require(targetDeviceId != identity.deviceId) { "You cannot revoke the device currently being used." }
        val raw = rpc(
            "nurva_revoke_device",
            JSONObject()
                .put("p_group", groupId)
                .put("p_target_device", targetDeviceId)
                .put("p_requester", identity.deviceId)
                .put("p_secret", identity.secret),
            session
        )
        val revoked = raw.trim().equals("true", ignoreCase = true)
        if (revoked) refreshGroup(groupId)
        return revoked
    }

    fun currentDeviceId(groupId: String): String? {
        val session = sessionStore.load() ?: return null
        return localIndex.deviceId(session.userId, groupId)
    }

    /** Saves 1–100 locally known wallpaper revisions as a shared group theme. */
    suspend fun saveSharedTheme(groupId: String, rawName: String, revisionIds: List<String>): String {
        val name = normalizedSharedThemeName(rawName)
        require(revisionIds.size in 1..100 && revisionIds.distinct().size == revisionIds.size) {
            "Choose between 1 and 100 different wallpaper revisions."
        }
        if (dao.getGroup(groupId) == null) refreshGroup(groupId)
        val revisionsById = dao.getRevisions(groupId).associateBy { it.revisionId }
        val selectedRevisions = revisionIds.map { id ->
            revisionsById[id] ?: error("A selected wallpaper revision is no longer available. Refresh the group and try again.")
        }

        val session = validSession()
        val identity = localIdentity(session.userId, groupId)
        val themeId = UUID.randomUUID().toString()
        val items = JSONArray().apply {
            selectedRevisions.forEach { revision ->
                put(JSONObject().put("revisionId", revision.revisionId))
            }
        }
        val rawResult = rpc(
            "nurva_save_theme",
            JSONObject()
                .put("p_group", groupId)
                .put("p_device", identity.deviceId)
                .put("p_secret", identity.secret)
                .put("p_theme", themeId)
                .put("p_name", name)
                .put("p_layout", "shared")
                .put("p_items", items)
                .put("p_activate", true),
            session
        )
        val returnedId = rawResult.trim().removeSurrounding("\"")
        check(returnedId.equals(themeId, ignoreCase = true)) {
            "The server saved the theme but returned an unexpected theme ID. Refresh the group."
        }

        val now = System.currentTimeMillis()
        dao.upsertTheme(
            SyncThemeEntity(
                themeId = themeId,
                groupId = groupId,
                name = name,
                layoutMode = "shared",
                description = null,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.replaceThemeItems(
            themeId,
            selectedRevisions.mapIndexed { index, revision ->
                SyncThemeItemEntity(themeId, index, revision.revisionId, null)
            }
        )
        dao.getGroup(groupId)?.let { dao.upsertGroup(it.copy(currentThemeId = themeId, updatedAt = now)) }
        // The RPC commit is authoritative; a failed refresh must not imply the save failed.
        runCatching { refreshGroup(groupId) }
        return themeId
    }

    /** Selects an existing shared theme. Selection updates metadata; device wallpaper apply is separate. */
    suspend fun activateSharedTheme(groupId: String, themeId: String) {
        val theme = dao.getTheme(groupId, themeId)
            ?: error("That shared theme is not available locally. Refresh the group and try again.")
        val savedItems = dao.getThemeItems(themeId)
        require(savedItems.isNotEmpty()) { "This shared theme has no wallpaper items." }
        val session = validSession()
        val identity = localIdentity(session.userId, groupId)
        val items = JSONArray().apply {
            savedItems.forEach { item ->
                put(JSONObject().put("revisionId", item.revisionId).apply {
                    item.deviceId?.let { put("deviceId", it) }
                })
            }
        }
        val rawResult = rpc(
            "nurva_save_theme",
            JSONObject()
                .put("p_group", groupId)
                .put("p_device", identity.deviceId)
                .put("p_secret", identity.secret)
                .put("p_theme", themeId)
                .put("p_name", theme.name)
                .put("p_layout", theme.layoutMode)
                .put("p_items", items)
                .put("p_activate", true),
            session
        )
        check(rawResult.trim().removeSurrounding("\"").equals(themeId, ignoreCase = true)) {
            "The server did not confirm the selected theme. Refresh the group."
        }
        val now = System.currentTimeMillis()
        dao.getGroup(groupId)?.let { dao.upsertGroup(it.copy(currentThemeId = themeId, updatedAt = now)) }
        runCatching { refreshGroup(groupId) }
    }

    private suspend fun persistSnapshot(groupId: String, state: JSONObject) {
        val group = state.getJSONObject("group")
        val wallpaper = state.optJSONObject("currentWallpaper")
        val groupEntity = SyncGroupEntity(
            groupId = group.getString("id"),
            displayName = group.getString("displayName"),
            ownerUserId = group.getString("ownerUserId"),
            currentRevision = group.optLong("currentRevision", 0),
            currentThemeId = group.optNullableString("currentThemeId"),
            currentAssetPath = wallpaper?.optNullableString("storagePath"),
            createdAt = 0L,
            updatedAt = parseTimestamp(group.optString("updatedAt"))
        )

        val membersJson = state.optJSONArray("members") ?: JSONArray()
        val members = (0 until membersJson.length()).mapNotNull { index ->
            val item = membersJson.optJSONObject(index) ?: return@mapNotNull null
            SyncGroupMemberEntity(
                groupId = groupId,
                userId = item.getString("userId"),
                role = item.getString("role"),
                joinedAt = parseTimestamp(item.optString("joinedAt")),
                removedAt = null
            )
        }

        val devicesJson = state.optJSONArray("devices") ?: JSONArray()
        val devices = (0 until devicesJson.length()).mapNotNull { index ->
            val item = devicesJson.optJSONObject(index) ?: return@mapNotNull null
            SyncDeviceEntity(
                deviceId = item.getString("id"),
                groupId = groupId,
                userId = item.getString("userId"),
                displayName = item.optString("displayName", "Android device"),
                wallpaperTarget = item.optString("wallpaperTarget", "lock"),
                applyMode = item.optString("applyMode", "manual"),
                lastAppliedRevision = item.optLong("lastAppliedRevision", 0),
                lastSeenAt = item.optNullableString("lastSeenAt")?.let(::parseTimestamp),
                revokedAt = null
            )
        }
        dao.replaceRemoteSnapshot(groupEntity, members, devices)

        val mosaicJson = state.optJSONArray("mosaicTiles") ?: JSONArray()
        val mosaicAssignments = (0 until mosaicJson.length()).mapNotNull { index ->
            val item = mosaicJson.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                MosaicTileAssignmentEntity(
                    groupId = groupId,
                    revision = groupEntity.currentRevision,
                    deviceId = item.getString("deviceId"),
                    tileIndex = item.getInt("tileIndex"),
                    rowIndex = item.getInt("rowIndex"),
                    columnIndex = item.getInt("columnIndex"),
                    cropLeft = item.getDouble("cropLeft").toFloat(),
                    cropTop = item.getDouble("cropTop").toFloat(),
                    cropRight = item.getDouble("cropRight").toFloat(),
                    cropBottom = item.getDouble("cropBottom").toFloat(),
                    outputWidth = item.getInt("outputWidth"),
                    outputHeight = item.getInt("outputHeight")
                )
            }.getOrNull()
        }
        if (mosaicAssignments.isNotEmpty()) dao.upsertMosaicTiles(mosaicAssignments)

        if (wallpaper != null) {
            val revisionNumber = wallpaper.getLong("revision")
            val previous = dao.getLatestRevision(groupId)?.takeIf { it.revision == revisionNumber }
            dao.upsertRevision(
                SyncWallpaperRevisionEntity(
                    revisionId = wallpaper.getString("revisionId"),
                    groupId = groupId,
                    revision = revisionNumber,
                    createdByUserId = "",
                    storagePath = wallpaper.getString("storagePath"),
                    localFilePath = previous?.localFilePath,
                    sha256 = wallpaper.getString("sha256"),
                    mimeType = wallpaper.getString("mimeType"),
                    byteSize = wallpaper.getLong("byteSize"),
                    imageWidth = wallpaper.getInt("imageWidth"),
                    imageHeight = wallpaper.getInt("imageHeight"),
                    createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                    localState = previous?.localState ?: "pending_download"
                )
            )
        }
        // Mirror shared themes and their revision references into Room for offline display.
        val themesJson = state.optJSONArray("themes") ?: JSONArray()
        for (themeIndex in 0 until themesJson.length()) {
            val themeJson = themesJson.optJSONObject(themeIndex) ?: continue
            val themeId = themeJson.optString("id").takeIf { it.isNotBlank() } ?: continue
            val previousTheme = dao.getTheme(groupId, themeId)
            val now = System.currentTimeMillis()
            dao.upsertTheme(
                SyncThemeEntity(
                    themeId = themeId,
                    groupId = groupId,
                    name = themeJson.optString("name", "Shared theme").take(80),
                    layoutMode = themeJson.optString("layoutMode", "shared")
                        .takeIf { it in setOf("shared", "per_device", "mosaic") } ?: "shared",
                    description = themeJson.optString("description")
                        .takeIf { it.isNotBlank() && it != "null" },
                    createdAt = previousTheme?.createdAt ?: now,
                    updatedAt = now
                )
            )
            val themeItemJson = themeJson.optJSONArray("items") ?: JSONArray()
            val themeItems = (0 until themeItemJson.length()).mapNotNull { itemIndex ->
                val themeItem = themeItemJson.optJSONObject(itemIndex) ?: return@mapNotNull null
                val revisionId = themeItem.optString("revisionId").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                SyncThemeItemEntity(
                    themeId = themeId,
                    position = themeItem.optInt("position", itemIndex),
                    revisionId = revisionId,
                    deviceId = themeItem.optString("deviceId")
                        .takeIf { it.isNotBlank() && it != "null" }
                )
            }
            dao.replaceThemeItems(themeId, themeItems)
        }
    }

    private fun localIdentity(userId: String, groupId: String): DeviceCredentialStore.DeviceIdentity {
        val deviceId = localIndex.deviceId(userId, groupId) ?: error("This device is not registered in that group.")
        return deviceCredentials.loadIdentity(deviceId) ?: error("This device's encrypted credential is missing. Rejoin the group to register it again.")
    }

    private fun validSession(): AuthSession {
        val current = sessionStore.load() ?: error("Sign in to your Nurva account first.")
        if (current.expiresAtEpochSeconds > Instant.now().epochSecond + 90) return current
        return try {
            val refreshed = requestJson(
                path = "/auth/v1/token?grant_type=refresh_token",
                body = JSONObject().put("refresh_token", current.refreshToken),
                bearer = null
            )
            adoptSession(refreshed) ?: throw IllegalStateException("Your session expired. Sign in again.")
        } catch (error: HttpStatusException) {
            // A rejected refresh token is terminal; transient server/network errors must not erase it.
            if (error.statusCode in 400..499) sessionStore.clear()
            throw IllegalStateException(
                if (error.statusCode in 400..499) "Your session expired. Sign in again."
                else "Nurva could not refresh your session. Check your connection and try again.",
                error
            )
        } catch (error: Exception) {
            throw IllegalStateException("Nurva could not refresh your session. Check your connection and try again.", error)
        }
    }

    private fun adoptSession(response: JSONObject): AuthSession? {
        val nestedSession = response.optJSONObject("session") ?: response
        val user = response.optJSONObject("user") ?: nestedSession.optJSONObject("user") ?: response
        val accessToken = nestedSession.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        val refreshToken = nestedSession.optString("refresh_token").takeIf { it.isNotBlank() } ?: return null
        val userId = user.optString("id").takeIf { it.isNotBlank() }
            ?: nestedSession.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: error("The authentication response did not include a user ID.")
        val session = AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = Instant.now().epochSecond + nestedSession.optLong("expires_in", 3600),
            userId = userId,
            email = user.optString("email")
        )
        sessionStore.save(session)
        return session
    }

    private fun rpc(name: String, args: JSONObject, session: AuthSession = validSession()): String =
        requestText("/rest/v1/rpc/$name", args, session.accessToken)

    private fun requestJson(path: String, body: JSONObject, bearer: String?): JSONObject =
        JSONObject(requestText(path, body, bearer))

    private fun requestText(path: String, body: JSONObject?, bearer: String?): String {
        requireConfiguration()
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("apikey", publishableKey)
            .header("Accept", "application/json")
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        val payload = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        val request = builder.post(payload ?: ByteArray(0).toRequestBody(null)).build()
        return try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        val json = JSONObject(text)
                        json.optString("msg").ifBlank {
                            json.optString("message").ifBlank { json.optString("error_description") }
                        }
                    }.getOrNull().orEmpty()
                    throw HttpStatusException(
                        response.code,
                        detail.ifBlank { "Nurva server returned HTTP ${response.code}." }
                    )
                }
                text
            }
        } catch (error: IOException) {
            throw IllegalStateException("Network request failed. Check your connection and try again.", error)
        }
    }

    private class HttpStatusException(
        val statusCode: Int,
        message: String
    ) : IllegalStateException(message)

    private fun requireConfiguration() {
        check(isConfigured()) {
            "Nurva cloud is not configured. Add NURVA_SUPABASE_URL and NURVA_SUPABASE_PUBLISHABLE_KEY to local.properties, then rebuild."
        }
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80).ifBlank { "Android device" }

    private class LocalDeviceIndex(context: Context) {
        private val prefs = context.getSharedPreferences("nurva_local_device_index", Context.MODE_PRIVATE)

        fun remember(userId: String, groupId: String, deviceId: String) {
            val groups = prefs.getStringSet(groupKey(userId), mutableSetOf()).orEmpty().toMutableSet()
            groups.add(groupId)
            check(
                prefs.edit()
                    .putStringSet(groupKey(userId), groups)
                    .putString(deviceKey(userId, groupId), deviceId)
                    .commit()
            ) { "Could not save this device's group registration." }
        }

        fun groupIds(userId: String): Set<String> = prefs.getStringSet(groupKey(userId), mutableSetOf()).orEmpty().toSet()
        fun deviceId(userId: String, groupId: String): String? = prefs.getString(deviceKey(userId, groupId), null)

        private fun groupKey(userId: String) = "groups_$userId"
        private fun deviceKey(userId: String, groupId: String) = "device_${userId}_$groupId"
    }

    companion object {
        private const val MAX_WALLPAPER_BYTES = 50L * 1024L * 1024L
        private const val MAX_IMAGE_PIXELS = 60_000_000L
        private val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        private fun JSONObject.optNullableString(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun parseTimestamp(value: String): Long =
            runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    }
}
