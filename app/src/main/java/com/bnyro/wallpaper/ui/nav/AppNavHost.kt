package com.bnyro.wallpaper.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bnyro.wallpaper.ui.models.MainModel
import com.bnyro.wallpaper.ui.pages.AboutPage
import com.bnyro.wallpaper.ui.pages.FavoritesPage
import com.bnyro.wallpaper.ui.pages.HistoryPage
import com.bnyro.wallpaper.ui.pages.NurvaExplorePage
import com.bnyro.wallpaper.ui.pages.NurvaHomePage
import com.bnyro.wallpaper.ui.pages.NurvaLibraryPage
import com.bnyro.wallpaper.ui.pages.NurvaSyncPage
import com.bnyro.wallpaper.ui.pages.SettingsPage
import com.bnyro.wallpaper.ui.pages.WallpaperPage
import com.bnyro.wallpaper.util.Preferences

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = DrawerScreens.Companion.Home.route,
    viewModel: MainModel
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination
    ) {
        composable(DrawerScreens.Companion.Home.route) {
            viewModel.titleResource = DrawerScreens.Companion.Home.title
            NurvaHomePage(
                onOpenGroups = { navController.navigate(DrawerScreens.Companion.Groups.route) },
                onExplore = { navController.navigate(DrawerScreens.Companion.Explore.route) },
                onLibrary = { navController.navigate(DrawerScreens.Companion.Library.route) }
            )
        }
        composable(DrawerScreens.Companion.Groups.route) {
            viewModel.titleResource = DrawerScreens.Companion.Groups.title
            NurvaSyncPage(
                viewModel = viewModel,
                onBrowseWallpapers = { navController.navigate(DrawerScreens.Companion.Explore.route) }
            )
        }
        composable(DrawerScreens.Companion.Explore.route) {
            viewModel.titleResource = DrawerScreens.Companion.Explore.title
            NurvaExplorePage(viewModel)
        }
        composable(DrawerScreens.Companion.Library.route) {
            viewModel.titleResource = DrawerScreens.Companion.Library.title
            NurvaLibraryPage(viewModel)
        }
        composable(DrawerScreens.Companion.Settings.route) {
            viewModel.titleResource = DrawerScreens.Companion.Settings.title
            SettingsPage(
                viewModel = viewModel,
                onManageAccount = { navController.navigate(DrawerScreens.Companion.Groups.route) },
                onAbout = { navController.navigate(DrawerScreens.Companion.About.route) }
            )
        }
        composable(DrawerScreens.Companion.About.route) {
            viewModel.titleResource = DrawerScreens.Companion.About.title
            AboutPage()
        }

        // Keep old saved routes valid; source browsing now lives in the Explore tab.
        DrawerScreens.apiScreens.forEach { screen ->
            composable(screen.route) {
                viewModel.titleResource = screen.title
                viewModel.api = Preferences.getApiByRoute(screen.route)
                WallpaperPage(viewModel)
            }
        }
        composable("favorites") { FavoritesPage(viewModel) }
        composable("history") { HistoryPage(viewModel) }
    }
}
