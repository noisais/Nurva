package com.bnyro.wallpaper.sync.auth

import android.content.Context
import android.net.Uri
import android.os.Build
import com.bnyro.wallpaper.BuildConfig
import com.bnyro.wallpaper.db.DatabaseHolder
import com.bnyro.wallpaper.sync.db.SyncDeviceEntity
import com.bnyro.wallpaper.sync.db.SyncGroupEntity
import com.bnyro.wallpaper.sync.db.SyncGroupMemberEntity
import com.bnyro.wallpaper.sync.security.DeviceCredentialStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        private fun JSONObject.optNullableString(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun parseTimestamp(value: String): Long =
            runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    }
}
