package com.bnyro.wallpaper.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bnyro.wallpaper.ui.models.MainModel

@Composable
fun NurvaLibraryPage(viewModel: MainModel) {
    var showHistory by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("YOUR COLLECTION", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("Library", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showHistory,
                    onClick = { showHistory = false },
                    label = { Text("Favorites") }
                )
                FilterChip(
                    selected = showHistory,
                    onClick = { showHistory = true },
                    label = { Text("Recently applied") }
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (showHistory) HistoryPage(viewModel) else FavoritesPage(viewModel)
        }
    }
}
