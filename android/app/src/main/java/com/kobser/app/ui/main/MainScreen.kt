package com.kobser.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kobser.app.ui.library.AlbumDetailScreen
import com.kobser.app.ui.library.ArtistDetailScreen
import com.kobser.app.ui.library.ArtistsScreen
import com.kobser.app.ui.library.LibraryScreen
import com.kobser.app.ui.player.ExpandedPlayerScreen
import com.kobser.app.ui.player.MiniPlayer
import com.kobser.app.ui.playlists.PlaylistDetailScreen
import com.kobser.app.ui.playlists.PlaylistsScreen
import com.kobser.app.ui.downloads.DownloadsScreen
import com.kobser.app.ui.search.SearchScreen
import com.kobser.app.ui.settings.SettingsScreen
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
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Downloads : Screen("downloads", "Downloads", Icons.Default.Download)
    object Playlists : Screen("playlists", "Playlists", Icons.AutoMirrored.Filled.PlaylistPlay)
    object Favorites : Screen("favorites", "Favorites", Icons.Default.Favorite)
    object Artists : Screen("artists", "Artists", Icons.Default.Person)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navItems = listOf(
        Screen.Library,
        Screen.Favorites,
        Screen.Search,
        Screen.Playlists,
    )

    var expandedPlayerOpen by rememberSaveable { mutableStateOf(false) }
    var moreSheetOpen by remember { mutableStateOf(false) }

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
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        navItems.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(screen.label, fontSize = 10.sp) },
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
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                            label = { Text("More", fontSize = 10.sp) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == Screen.Downloads.route ||
                                it.route == Screen.Artists.route ||
                                it.route == Screen.Settings.route
                            } == true,
                            onClick = { moreSheetOpen = true },
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
        ) { innerPadding ->
            NavHost(
                navController,
                startDestination = Screen.Library.route,
                Modifier.padding(innerPadding)
            ) {
                composable(Screen.Library.route) {
                    LibraryScreen(
                        onSongClick = { expandedPlayerOpen = true },
                        onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    )
                }
                composable(Screen.Favorites.route) {
                    LibraryScreen(isFavorites = true, onSongClick = { expandedPlayerOpen = true })
                }
                composable(Screen.Artists.route) {
                    ArtistsScreen(
                        onArtistClick = { artist -> navController.navigate("artist/${artist.id}") },
                    )
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

    if (moreSheetOpen) {
        MoreSheet(
            onDismiss = { moreSheetOpen = false },
            onNavigate = { route ->
                moreSheetOpen = false
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = listOf(
        Triple(Screen.Downloads.route, "Downloads", Icons.Default.Download),
        Triple(Screen.Artists.route, "Artists", Icons.Default.Person),
        Triple(Screen.Settings.route, "Settings", Icons.Default.Settings),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "More",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            items.forEach { (route, label, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(route) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

