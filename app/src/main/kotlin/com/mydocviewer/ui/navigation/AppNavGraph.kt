package com.mydocviewer.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mydocviewer.R
import com.mydocviewer.ui.favorites.FavoritesScreen
import com.mydocviewer.ui.files.FilesScreen
import com.mydocviewer.ui.home.HomeScreen
import com.mydocviewer.ui.settings.SettingsScreen
import com.mydocviewer.ui.viewer.DocumentViewerScreen
import com.mydocviewer.util.FileTypeDetector

private object Routes {
    const val RECENT = "recent"
    const val FILES = "files"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{uri}"
    fun viewer(uri: String) = "viewer/${Uri.encode(uri)}"
}

@Composable
fun AppNavHost(
    incomingDocumentUri: String?,
    onConsumeIncomingDocument: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf(
        Routes.RECENT, Routes.FILES, Routes.FAVORITES, Routes.SETTINGS
    )

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            navController.navigate(Routes.viewer(uri.toString()))
        }
    }

    val openPicker = {
        pickFileLauncher.launch(
            arrayOf(
                FileTypeDetector.PPTX_MIME_TYPE,
                "application/octet-stream",
                "application/zip"
            )
        )
    }

    LaunchedEffect(incomingDocumentUri) {
        if (incomingDocumentUri != null) {
            navController.navigate(Routes.viewer(incomingDocumentUri))
            onConsumeIncomingDocument()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    val items = listOf(
                        Triple(Routes.RECENT, R.string.nav_recent, Icons.Outlined.History),
                        Triple(Routes.FILES, R.string.nav_files, Icons.Outlined.Folder),
                        Triple(Routes.FAVORITES, R.string.nav_favorites, Icons.Outlined.StarOutline),
                        Triple(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings)
                    )
                    items.forEach { (route, labelRes, icon) ->
                        val selected = currentDestination?.hierarchy?.any { it.route == route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(Routes.RECENT) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RECENT,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.RECENT) {
                HomeScreen(
                    onOpenDocument = { uri -> navController.navigate(Routes.viewer(uri)) },
                    onPickSystemFile = openPicker
                )
            }
            composable(Routes.FILES) {
                FilesScreen(
                    onOpenDocument = { uri -> navController.navigate(Routes.viewer(uri)) },
                    onPickSingleFile = openPicker
                )
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    onOpenDocument = { uri -> navController.navigate(Routes.viewer(uri)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.VIEWER,
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri").orEmpty()
                DocumentViewerScreen(
                    uriString = uri,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
