package com.kobser.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kobser.app.ui.library.AlbumDetailScreen
import com.kobser.app.ui.library.ArtistDetailScreen
import com.kobser.app.ui.library.LibraryScreen
import com.kobser.app.ui.player.ExpandedPlayerScreen
import com.kobser.app.ui.player.MiniPlayer
import com.kobser.app.ui.playlists.PlaylistDetailScreen
import com.kobser.app.ui.playlists.PlaylistsScreen
import com.kobser.app.ui.downloads.DownloadsScreen
import com.kobser.app.ui.search.SearchScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Favorites : Screen("favorites", "Favorites", Icons.Default.Favorite)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Downloads : Screen("downloads", "Downloads", Icons.Default.Download)
    object Playlists : Screen("playlists", "Playlists", Icons.AutoMirrored.Filled.PlaylistPlay)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Library,
        Screen.Search,
        Screen.Downloads,
        Screen.Playlists,
        Screen.Favorites,
    )

    var expandedPlayerOpen by rememberSaveable { mutableStateOf(false) }

    val currentSong by viewModel.musicPlayer.currentSong.collectAsState()
    if (currentSong == null && expandedPlayerOpen) {
        expandedPlayerOpen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayer(onExpand = { expandedPlayerOpen = true })
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(screen.label) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    indicatorColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController,
                startDestination = Screen.Library.route,
                Modifier.padding(innerPadding)
            ) {
                composable(Screen.Library.route) {
                    LibraryScreen(
                        onSongClick = { /* TODO: Play song */ },
                        onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    )
                }
                composable(Screen.Favorites.route) {
                    LibraryScreen(isFavorites = true, onSongClick = { /* TODO: Play song */ })
                }
                composable(Screen.Search.route) {
                    SearchScreen()
                }
                composable(Screen.Downloads.route) {
                    DownloadsScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(onLogout = { viewModel.logout {} })
                }
                composable(Screen.Playlists.route) {
                    PlaylistsScreen(
                        onPlaylistClick = { id -> navController.navigate("playlist/$id") },
                    )
                }
                composable(
                    route = "artist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) {
                    ArtistDetailScreen(
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                    )
                }
                composable(
                    route = "album/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) {
                    AlbumDetailScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) {
                    PlaylistDetailScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expandedPlayerOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300),
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300),
            ),
        ) {
            ExpandedPlayerScreen(onClose = { expandedPlayerOpen = false })
        }
    }
}

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLogout) {
            Text("Logout")
        }
    }
}
