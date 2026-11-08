package com.bnyro.wallpaper.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bnyro.wallpaper.db.DatabaseHolder
import com.bnyro.wallpaper.sync.auth.NurvaRepository
import com.bnyro.wallpaper.ui.models.MainModel
import com.bnyro.wallpaper.sync.db.SyncDeviceEntity
import com.bnyro.wallpaper.sync.db.SyncGroupEntity
import com.bnyro.wallpaper.sync.db.SyncGroupMemberEntity
import com.bnyro.wallpaper.util.NurvaSyncWorker
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NurvaSyncPage(viewModel: MainModel, onBrowseWallpapers: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { NurvaRepository(context.applicationContext) }
    val dao = remember { DatabaseHolder.Database.nurvaSyncDao() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var session by remember { mutableStateOf(repository.currentSession()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var inviteToken by remember { mutableStateOf("") }
    var selectedGroupId by rememberSaveable { mutableStateOf("") }
    var inviteRole by rememberSaveable { mutableStateOf("editor") }
    var inviteMenuExpanded by remember { mutableStateOf(false) }
    var createdInviteLink by remember { mutableStateOf("") }
    var publishAsMosaic by rememberSaveable { mutableStateOf(false) }
    var showMosaicOrderDialog by remember { mutableStateOf(false) }
    var mosaicDeviceOrder by remember { mutableStateOf(emptyList<String>()) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val groupId = selectedGroupId
        val useMosaic = publishAsMosaic
        publishAsMosaic = false
        if (uri != null && groupId.isNotBlank()) {
            scope.launch {
                isBusy = true
                errorMessage = ""
                statusMessage = ""
                try {
                    val revision = withContext(Dispatchers.IO) {
                        repository.publishWallpaper(
                            groupId, uri, mosaic = useMosaic,
                            mosaicDeviceOrder = mosaicDeviceOrder.takeIf { useMosaic }
                        )
                    }
                    statusMessage = if (useMosaic) {
                        "Panorama mosaic published as revision $revision. Each active device receives its own horizontal slice."
                    } else {
                        "Wallpaper published as revision $revision. Other devices can now download it."
                    }
                } catch (error: Exception) {
                    errorMessage = error.message ?: "Could not publish wallpaper."
                } finally {
                    isBusy = false
                }
            }
        }
    }

    val allGroups by dao.observeGroups().collectAsState(initial = emptyList())
    val groups = remember(allGroups, session?.userId) {
        val userId = session?.userId
        if (userId == null) emptyList() else allGroups.filter { it.groupId in repository.localGroupIds(userId) }
    }
    val membersFlow = remember(selectedGroupId) {
        if (selectedGroupId.isBlank()) flowOf(emptyList<SyncGroupMemberEntity>()) else dao.observeMembers(selectedGroupId)
    }
    val devicesFlow = remember(selectedGroupId) {
        if (selectedGroupId.isBlank()) flowOf(emptyList<SyncDeviceEntity>()) else dao.observeDevices(selectedGroupId)
    }
    val members by membersFlow.collectAsState(initial = emptyList())
    val devices by devicesFlow.collectAsState(initial = emptyList())
    val activeDevices = remember(devices) {
        devices.filter { it.revokedAt == null }
            .sortedWith(compareBy<SyncDeviceEntity> { it.displayName.lowercase() }.thenBy { it.deviceId })
    }
    val selectedGroup = groups.firstOrNull { it.groupId == selectedGroupId }
    val signedInUserRole = members.firstOrNull { it.userId == session?.userId }?.role
    val currentDeviceId = selectedGroupId.takeIf { it.isNotBlank() }?.let(repository::currentDeviceId)
    val thisDevice = devices.firstOrNull { it.deviceId == currentDeviceId }

    LaunchedEffect(selectedGroupId) {
        createdInviteLink = ""
    }
    LaunchedEffect(groups) {
        if (groups.isNotEmpty() && groups.none { it.groupId == selectedGroupId }) {
            selectedGroupId = groups.first().groupId
        }
    }
    LaunchedEffect(viewModel.pendingInviteToken) {
        if (viewModel.pendingInviteToken.isNotBlank()) {
            inviteToken = viewModel.pendingInviteToken
            viewModel.pendingInviteToken = ""
            statusMessage = "Invite link received. Sign in, then tap Join group."
        }
    }
    LaunchedEffect(session?.userId) {
        if (session != null) {
            NurvaSyncWorker.enqueueNow(context)
            runCatching { withContext(Dispatchers.IO) { repository.refreshGroups() } }
                .onFailure {
                    if (repository.currentSession() == null) session = null
                    statusMessage = "Groups could not refresh: ${it.message}"
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("N U R V A", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("Every screen.\nIn sync.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
            Text(
                "Create a group, invite your devices and people, then manage who can change your shared screens.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (!repository.isConfigured()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cloud connection needs setup", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Add NURVA_SUPABASE_URL and NURVA_SUPABASE_PUBLISHABLE_KEY to the app's local.properties file or GitHub Actions secrets, then rebuild. Never put a service-role or secret key in the app.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (session == null) {
            AuthCard(
                email = email,
                onEmailChange = { email = it },
                password = password,
                onPasswordChange = { password = it },
                isSignUp = isSignUp,
                onToggleMode = { isSignUp = !isSignUp; errorMessage = ""; statusMessage = "" },
                enabled = repository.isConfigured() && !isBusy,
                isBusy = isBusy,
                onSubmit = {
                    scope.launch {
                        isBusy = true
                        errorMessage = ""
                        statusMessage = ""
                        try {
                            val result = withContext(Dispatchers.IO) {
                                if (isSignUp) repository.signUp(email, password)
                                else repository.signIn(email, password)
                            }
                            if (result != null) {
                                session = result
                                password = ""
                                statusMessage = "Signed in as ${result.email}."
                            } else {
                                password = ""
                                statusMessage = "Account request sent. Check your email for confirmation, then sign in here."
                                isSignUp = false
                            }
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "Account request failed."
                        } finally {
                            isBusy = false
                        }
                    }
                }
            )
        } else {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SIGNED IN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(session?.email.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                try { withContext(Dispatchers.IO) { repository.signOut() } }
                                catch (_: Exception) { /* Local session is cleared even when the network is unavailable. */ }
                                session = null
                                selectedGroupId = ""
                                createdInviteLink = ""
                                statusMessage = "Signed out."
                                isBusy = false
                            }
                        }
                    ) { Text("Sign out") }
                }
            }

            SectionCard(title = "CREATE A GROUP", subtitle = "Start a shared wallpaper space.") {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Group name") },
                    singleLine = true
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && groupName.trim().isNotEmpty(),
                    onClick = {
                        scope.launch {
                            isBusy = true; errorMessage = ""; statusMessage = ""
                            try {
                                val id = withContext(Dispatchers.IO) { repository.createGroup(groupName) }
                                selectedGroupId = id
                                groupName = ""
                                statusMessage = "Group created. This device is registered as its owner."
                            } catch (error: Exception) { errorMessage = error.message ?: "Could not create group." }
                            finally { isBusy = false }
                        }
                    }
                ) { Text("Create group") }
            }

            SectionCard(title = "JOIN A GROUP", subtitle = "Paste an invite token or open a Nurva invite link on this device.") {
                OutlinedTextField(
                    value = inviteToken,
                    onValueChange = { inviteToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Invite link or token") },
                    minLines = 2
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && inviteToken.trim().isNotEmpty(),
                    onClick = {
                        scope.launch {
                            isBusy = true; errorMessage = ""; statusMessage = ""
                            try {
                                val id = withContext(Dispatchers.IO) { repository.joinGroup(inviteToken) }
                                selectedGroupId = id
                                inviteToken = ""
                                statusMessage = "You joined the group. This device is registered."
                            } catch (error: Exception) { errorMessage = error.message ?: "Could not join group." }
                            finally { isBusy = false }
                        }
                    }
                ) { Text("Join group") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("YOUR GROUPS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                try { withContext(Dispatchers.IO) { repository.refreshGroups() }; statusMessage = "Groups refreshed." }
                                catch (error: Exception) { errorMessage = error.message ?: "Refresh failed." }
                                finally { isBusy = false }
                            }
                        }
                    ) { Text("Refresh") }
                }
                if (groups.isEmpty()) {
                    Text("No groups yet. Create one above or join with an invitation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    groups.forEach { group ->
                        GroupSelector(group = group, selected = group.groupId == selectedGroupId, onClick = { selectedGroupId = group.groupId })
                    }
                }
            }

            if (selectedGroup != null) {
                SectionCard(title = selectedGroup.displayName, subtitle = "Revision ${selectedGroup.currentRevision} · ${members.size} members · ${devices.size} active devices") {
                    Text("SHARED WALLPAPER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (selectedGroup.currentRevision > 0L) "Latest published revision: ${selectedGroup.currentRevision}" else "No wallpaper has been published to this group yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy && signedInUserRole in setOf("owner", "admin", "editor"),
                        onClick = {
                            publishAsMosaic = false
                            wallpaperPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                        }
                    ) { Text("Publish shared wallpaper") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy && activeDevices.size in 2..100 && signedInUserRole in setOf("owner", "admin", "editor"),
                        onClick = {
                            mosaicDeviceOrder = activeDevices.map { it.deviceId }
                            showMosaicOrderDialog = true
                        }
                    ) { Text("Arrange panorama · ${activeDevices.size} devices") }
                    Text(
                        "Choose the left-to-right order of devices before selecting a panorama. Each device receives its matching slice.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (thisDevice != null && selectedGroup.currentRevision > thisDevice.lastAppliedRevision) {
                        Text(
                            if (thisDevice.applyMode == "manual") {
                                "Revision ${selectedGroup.currentRevision} is waiting for your approval."
                            } else {
                                "Revision ${selectedGroup.currentRevision} is queued for automatic sync."
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (thisDevice != null) {
                        Text("THIS DEVICE'S SYNC SETTINGS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        DeviceSyncPreferences(
                            device = thisDevice,
                            enabled = !isBusy,
                            onChange = { target, mode ->
                                scope.launch {
                                    isBusy = true
                                    errorMessage = ""
                                    statusMessage = ""
                                    try {
                                        withContext(Dispatchers.IO) {
                                            repository.updateDevicePreferences(selectedGroup.groupId, target, mode)
                                        }
                                        NurvaSyncWorker.enqueueNow(context)
                                        statusMessage = "This device's sync preferences were saved."
                                    } catch (error: Exception) {
                                        errorMessage = error.message ?: "Could not save device preferences."
                                    } finally {
                                        isBusy = false
                                    }
                                }
                            }
                        )
                    }
                    if (selectedGroup.currentRevision > 0L) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy && thisDevice != null,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    errorMessage = ""
                                    statusMessage = ""
                                    try {
                                        val revision = withContext(Dispatchers.IO) {
                                            repository.applyLatestWallpaper(selectedGroup.groupId)
                                        }
                                        statusMessage = "Revision $revision downloaded, verified and applied to this device."
                                    } catch (error: Exception) {
                                        errorMessage = error.message ?: "Could not download or apply the group wallpaper."
                                    } finally {
                                        isBusy = false
                                    }
                                }
                            }
                        ) {
                            Text(if (thisDevice?.applyMode == "manual") "Approve & apply latest" else "Apply latest now")
                        }
                    }

                    Text("INVITATIONS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { inviteMenuExpanded = true }) { Text("Invite as: ${inviteRole.replaceFirstChar { it.uppercase() }}") }
                        DropdownMenu(expanded = inviteMenuExpanded, onDismissRequest = { inviteMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Editor — can publish and manage themes") }, onClick = { inviteRole = "editor"; inviteMenuExpanded = false })
                            DropdownMenuItem(text = { Text("Viewer — can view and sync") }, onClick = { inviteRole = "viewer"; inviteMenuExpanded = false })
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = !isBusy && (signedInUserRole == "owner" || signedInUserRole == "admin"),
                            onClick = {
                                scope.launch {
                                    isBusy = true; errorMessage = ""; statusMessage = ""
                                    try {
                                        createdInviteLink = withContext(Dispatchers.IO) { repository.createInvite(selectedGroup.groupId, inviteRole) }
                                        statusMessage = "Invitation created. It expires in 7 days and allows up to 10 uses."
                                    } catch (error: Exception) { errorMessage = error.message ?: "Could not create invitation." }
                                    finally { isBusy = false }
                                }
                            }
                        ) { Text("Create invite") }
                    }
                    if (createdInviteLink.isNotBlank()) {
                        Text("Share this link with the person joining:", style = MaterialTheme.typography.bodySmall)
                        Text(createdInviteLink, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Nurva invitation", createdInviteLink))
                                    Toast.makeText(context, "Invitation link copied", Toast.LENGTH_SHORT).show()
                                }
                            ) { Text("Copy link") }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Join my Nurva group")
                                        putExtra(Intent.EXTRA_TEXT, "Join my Nurva group using this invite link:\n$createdInviteLink")
                                    }
                                    context.startActivity(Intent.createChooser(send, "Share Nurva invitation"))
                                }
                            ) { Text("Share invite") }
                        }
                    }

                    Text("MEMBERS & PERMISSIONS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (members.isEmpty()) Text("No member information is available yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    members.forEach { member -> MemberRow(member) }

                    Text("REGISTERED DEVICES", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (devices.isEmpty()) Text("No active devices are registered yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    devices.forEach { device ->
                        val role = members.firstOrNull { it.userId == device.userId }?.role ?: "member"
                        val canRevokeDevice = device.deviceId != currentDeviceId && when (signedInUserRole) {
                            "owner" -> true
                            "admin" -> role !in setOf("owner", "admin")
                            else -> false
                        }
                        DeviceRow(
                            device = device,
                            role = role,
                            isThisDevice = device.deviceId == currentDeviceId,
                            canRevoke = canRevokeDevice,
                            onRevoke = {
                                scope.launch {
                                    isBusy = true; errorMessage = ""; statusMessage = ""
                                    try {
                                        val revoked = withContext(Dispatchers.IO) { repository.revokeDevice(selectedGroup.groupId, device.deviceId) }
                                        statusMessage = if (revoked) "Device access revoked." else "That device was already inactive."
                                    } catch (error: Exception) { errorMessage = error.message ?: "Could not revoke device." }
                                    finally { isBusy = false }
                                }
                            }
                        )
                    }
                    Text(
                        "Owner and admin can invite people and revoke other devices. Editors can publish and edit group content. Viewers have read-only access.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (isBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            Text("Working…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (statusMessage.isNotBlank()) Text(statusMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("BUILT FOR SHARED SCREENS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            FeatureRow(Icons.Default.ScreenLockLandscape, "Shared lock screens", "Keep a group's lock-screen image consistent across devices.")
            FeatureRow(Icons.Default.GridView, "Multi-device mosaics", "Arrange separate screens as tiles of one larger image.")
            FeatureRow(Icons.Default.Today, "Themes and schedules", "Plan wallpaper collections and when they should change.")
            FeatureRow(Icons.Default.Forum, "Invites and device control", "Share access with roles and revoke devices when needed.")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onBrowseWallpapers) { Text("Browse wallpapers") }
        Spacer(Modifier.height(8.dp))
    }

    if (showMosaicOrderDialog && selectedGroup != null) {
        NurvaMosaicOrderDialog(
            devices = activeDevices,
            initialOrder = mosaicDeviceOrder,
            onDismiss = { showMosaicOrderDialog = false },
            onConfirm = { orderedIds ->
                mosaicDeviceOrder = orderedIds
                showMosaicOrderDialog = false
                publishAsMosaic = true
                wallpaperPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }
        )
    }
}

@Composable
private fun AuthCard(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isSignUp: Boolean,
    onToggleMode: () -> Unit,
    enabled: Boolean,
    isBusy: Boolean,
    onSubmit: () -> Unit
) {
    SectionCard(title = if (isSignUp) "CREATE YOUR NURVA ACCOUNT" else "SIGN IN TO NURVA", subtitle = "Your account connects your groups across networks and devices.") {
        OutlinedTextField(value = email, onValueChange = onEmailChange, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password (8+ characters)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Button(modifier = Modifier.fillMaxWidth(), enabled = enabled && !isBusy && email.isNotBlank() && password.length >= 8, onClick = onSubmit) {
            Text(if (isSignUp) "Create account" else "Sign in")
        }
        TextButton(enabled = !isBusy, onClick = onToggleMode) {
            Text(if (isSignUp) "Already have an account? Sign in" else "New to Nurva? Create an account")
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun GroupSelector(group: SyncGroupEntity, selected: Boolean, onClick: () -> Unit) {
    val colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = colors) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Wallpaper revision ${group.currentRevision}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.Sync, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MemberRow(member: SyncGroupMemberEntity) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Group member", style = MaterialTheme.typography.bodyMedium)
            Text("Account ID ${member.userId.take(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(member.role.uppercase(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun DeviceSyncPreferences(
    device: SyncDeviceEntity,
    enabled: Boolean,
    onChange: (target: String, mode: String) -> Unit
) {
    var targetExpanded by remember(device.deviceId) { mutableStateOf(false) }
    var modeExpanded by remember(device.deviceId) { mutableStateOf(false) }

    Text("Wallpaper destination", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    OutlinedButton(enabled = enabled, onClick = { targetExpanded = true }) {
        Text(
            when (device.wallpaperTarget) {
                "home" -> "Home screen only"
                "both" -> "Home + lock screens"
                else -> "Lock screen only"
            }
        )
    }
    DropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
        DropdownMenuItem(text = { Text("Lock screen only") }, onClick = {
            targetExpanded = false
            onChange("lock", device.applyMode)
        })
        DropdownMenuItem(text = { Text("Home screen only") }, onClick = {
            targetExpanded = false
            onChange("home", device.applyMode)
        })
        DropdownMenuItem(text = { Text("Home + lock screens") }, onClick = {
            targetExpanded = false
            onChange("both", device.applyMode)
        })
    }

    Text("When a new group wallpaper arrives", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    OutlinedButton(enabled = enabled, onClick = { modeExpanded = true }) {
        Text(if (device.applyMode == "auto") "Apply automatically" else "Ask me before applying")
    }
    DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
        DropdownMenuItem(text = { Text("Apply automatically") }, onClick = {
            modeExpanded = false
            onChange(device.wallpaperTarget, "auto")
        })
        DropdownMenuItem(text = { Text("Ask me before applying") }, onClick = {
            modeExpanded = false
            onChange(device.wallpaperTarget, "manual")
        })
    }
}

@Composable
private fun DeviceRow(device: SyncDeviceEntity, role: String, isThisDevice: Boolean, canRevoke: Boolean, onRevoke: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(device.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (isThisDevice) Text("THIS DEVICE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
            Text("${role.replaceFirstChar { it.uppercase() }} · ${device.wallpaperTarget} screen · ${device.applyMode} apply", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Last applied revision ${device.lastAppliedRevision}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val lastSeen = device.lastSeenAt?.takeIf { it > 0L }?.let {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
            } ?: "No check-in recorded yet"
            Text("Last check-in: $lastSeen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canRevoke) TextButton(onClick = onRevoke) { Text("Revoke device access", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
