package com.bnyro.wallpaper.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.bnyro.wallpaper.App
import com.bnyro.wallpaper.R
import com.bnyro.wallpaper.util.Either
import com.bnyro.wallpaper.util.Preferences

/** Top-level Nurva destinations. Kept as a small route model for the single bottom-nav shell. */
open class DrawerScreens(
    val title: Either<Int, String>,
    val route: String,
    val icon: ImageVector,
    val divideBefore: Boolean = false,
    val titleLabel: String
) {
    companion object {
        object Home : DrawerScreens(Either.Left(R.string.nav_home), "home", Icons.Default.Home, titleLabel = "Home")
        object Groups : DrawerScreens(Either.Left(R.string.nav_groups), "groups", Icons.Default.Groups, titleLabel = "Groups")
        object Explore : DrawerScreens(Either.Left(R.string.nav_explore), "explore", Icons.Default.Explore, titleLabel = "Explore")
        object Library : DrawerScreens(Either.Left(R.string.nav_library), "library", Icons.Default.Favorite, titleLabel = "Library")
        object Settings : DrawerScreens(Either.Left(R.string.settings), "settings", Icons.Default.Settings, titleLabel = "Settings")
        object About : DrawerScreens(Either.Left(R.string.about), "about", Icons.Default.Info, titleLabel = "About")

        val topLevelScreens by lazy { listOf(Home, Groups, Explore, Library, Settings) }

        // Retain the source routes for compatibility with saved route preferences and existing pages.
        val apiScreens by lazy {
            App.apis.map {
                DrawerScreens(
                    title = Either.Right(it.name),
                    icon = it.icon,
                    route = it.route,
                    titleLabel = it.name
                )
            }
        }

        val screens by lazy {
            topLevelScreens + apiScreens + listOfNotNull(About)
        }
    }
}
