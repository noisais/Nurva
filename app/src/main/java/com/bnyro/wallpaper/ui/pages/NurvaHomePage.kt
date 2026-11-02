package com.bnyro.wallpaper.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.bnyro.wallpaper.db.DatabaseHolder
import com.bnyro.wallpaper.sync.auth.NurvaRepository

@Composable
fun NurvaHomePage(
    onOpenGroups: () -> Unit,
    onExplore: () -> Unit,
    onLibrary: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repository = remember { NurvaRepository(context) }
    val session = repository.currentSession()
    val dao = remember { DatabaseHolder.Database.nurvaSyncDao() }
    val allGroups by dao.observeGroups().collectAsState(initial = emptyList())
    val queued by dao.observePendingOperationCount().collectAsState(initial = 0)
    val groups = remember(allGroups, session?.userId) {
        val userId = session?.userId
        if (userId == null) emptyList() else allGroups.filter { it.groupId in repository.localGroupIds(userId) }
    }
    val visibleQueued = if (session == null) 0 else queued

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "YOUR SCREENS,\nCONNECTED.",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 38.sp
            )
            Text(
                text = "One shared space for the wallpapers and moments you want on every device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.padding(11.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = if (groups.isEmpty()) "Start your first shared space" else "Your shared spaces are ready",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (groups.isEmpty()) {
                        "Create a group, invite another Android device, and decide who can change the shared screen."
                    } else {
                        "Open your groups to invite devices, review permissions, and manage the screens connected to each space."
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onOpenGroups) {
                    Text(if (groups.isEmpty()) "Create or join a group" else "Manage groups")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }

        Text(
            text = "AT A GLANCE",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Groups,
                value = groups.size.toString(),
                label = "Spaces"
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Devices,
                value = visibleQueued.toString(),
                label = "Queued actions"
            )
        }

        Text(
            text = "MAKE IT YOURS",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        ActionCard(
            icon = Icons.Default.Explore,
            title = "Discover wallpapers",
            description = "Browse collections from your favorite wallpaper sources.",
            action = "Explore",
            onClick = onExplore
        )
        ActionCard(
            icon = Icons.Default.Favorite,
            title = "Your library",
            description = "Return to saved favorites and recently applied wallpapers.",
            action = "Open library",
            onClick = onLibrary
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryTile(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(13.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}
