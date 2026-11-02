package com.bnyro.wallpaper.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bnyro.wallpaper.R
import com.bnyro.wallpaper.ui.models.MainModel
import com.bnyro.wallpaper.ui.nav.AppNavHost
import com.bnyro.wallpaper.ui.nav.DrawerScreens
import com.bnyro.wallpaper.ui.theme.NurvaTheme

class MainActivity : BaseActivity() {
    private var mainModel: MainModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: MainModel = ViewModelProvider(this).get()
        mainModel = viewModel
        consumeInviteIntent(intent, viewModel)
        enableEdgeToEdge()
        showContent { MainContent(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainModel?.let { consumeInviteIntent(intent, it) }
    }

    private fun consumeInviteIntent(intent: Intent?, viewModel: MainModel) {
        val uri = intent?.data ?: return
        if (uri.scheme == "nurva" && uri.host == "join") {
            uri.getQueryParameter("token")?.takeIf { it.isNotBlank() }?.let {
                viewModel.pendingInviteToken = it
                viewModel.currentDestination = DrawerScreens.Companion.Groups
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(viewModel: MainModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: DrawerScreens.Companion.Home.route
    val tabs = DrawerScreens.topLevelScreens
    val currentTab = tabs.firstOrNull { it.route == currentRoute }
        ?: if (currentRoute == DrawerScreens.Companion.About.route) DrawerScreens.Companion.Settings else null
    val title = if (currentRoute == DrawerScreens.Companion.About.route) {
        "About Nurva"
    } else {
        currentTab?.titleLabel ?: "Nurva"
    }

    LaunchedEffect(viewModel.pendingInviteToken) {
        if (viewModel.pendingInviteToken.isNotBlank()) {
            navController.navigate(DrawerScreens.Companion.Groups.route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(currentRoute) {
        viewModel.currentDestination = currentTab ?: DrawerScreens.Companion.Home
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "N U R V A",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.5.sp
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = 12.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "Nurva",
                            modifier = Modifier.padding(8.dp),
                            tint = Color.Unspecified
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(DrawerScreens.Companion.Groups.route) { launchSingleTop = true }
                    }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account and groups",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route || (currentRoute == DrawerScreens.Companion.About.route && tab == DrawerScreens.Companion.Settings),
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.titleLabel) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            navController = navController,
            startDestination = DrawerScreens.Companion.Home.route,
            viewModel = viewModel
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    NurvaTheme {
        MainContent(viewModel())
    }
}
