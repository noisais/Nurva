package com.bnyro.wallpaper.ui.pages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bnyro.wallpaper.App
import com.bnyro.wallpaper.ui.models.MainModel

@Composable
fun NurvaExplorePage(viewModel: MainModel) {
    val sources = remember { App.apis }
    var selectedRoute by rememberSaveable { mutableStateOf(viewModel.api.route) }
    val selectedApi = sources.firstOrNull { it.route == selectedRoute } ?: sources.first()
    viewModel.api = selectedApi

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("FIND YOUR NEXT SCREEN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("Explore", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEach { source ->
                    FilterChip(
                        selected = selectedRoute == source.route,
                        onClick = { selectedRoute = source.route },
                        label = { Text(source.name) }
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(Modifier.fillMaxSize()) {
                key(selectedRoute) {
                    viewModel.api = selectedApi
                    WallpaperPage(viewModel)
                }
            }
        }
    }
}
