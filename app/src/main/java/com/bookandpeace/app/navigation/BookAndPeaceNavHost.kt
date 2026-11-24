package com.bookandpeace.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bookandpeace.app.data.AppContainer
import com.bookandpeace.app.ui.discover.DiscoverScreen
import com.bookandpeace.app.ui.library.LibraryScreen
import com.bookandpeace.app.ui.player.AudiobookPlayerScreen
import com.bookandpeace.app.ui.player.MiniAudioPlayerBar
import com.bookandpeace.app.ui.reader.ReaderScreen
import com.bookandpeace.app.ui.settings.SettingsScreen
import com.bookandpeace.app.ui.storyteller.StorytellerScreen

object Routes {
    const val LIBRARY = "library"
    const val DISCOVER = "discover"
    const val SETTINGS = "settings"
    const val READER = "reader/{bookId}"
    const val STORYTELLER = "storyteller/{bookId}"
    const val PLAYER = "player"

    fun reader(bookId: Long) = "reader/$bookId"
    fun storyteller(bookId: Long) = "storyteller/$bookId"
}

@Composable
fun BookAndPeaceRoot(
    container: AppContainer,
    externalBookIdToOpen: Long? = null,
    onConsumedExternalBook: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(externalBookIdToOpen) {
        val targetId = externalBookIdToOpen ?: return@LaunchedEffect
        navController.navigate(Routes.reader(targetId))
        onConsumedExternalBook()
    }

    val showBottomChrome = currentRoute in setOf(Routes.LIBRARY, Routes.DISCOVER, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (showBottomChrome) {
                Column {
                    MiniAudioPlayerBar(
                        container = container,
                        onOpenFullPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == Routes.LIBRARY,
                            onClick = {
                                navController.navigate(Routes.LIBRARY) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Sách của tôi") },
                            label = { Text("Sách của tôi") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.DISCOVER,
                            onClick = {
                                navController.navigate(Routes.DISCOVER) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Default.Explore, contentDescription = "Kho sách") },
                            label = { Text("Kho sách") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.SETTINGS,
                            onClick = {
                                navController.navigate(Routes.SETTINGS) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Cài đặt") },
                            label = { Text("Cài đặt") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomChrome) innerPadding.calculateBottomPadding() else innerPadding.calculateTopPadding() * 0f)
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    container = container,
                    onOpenReader = { bookId -> navController.navigate(Routes.reader(bookId)) },
                    onOpenStoryteller = { bookId -> navController.navigate(Routes.storyteller(bookId)) },
                    onOpenDiscover = { navController.navigate(Routes.DISCOVER) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                )
            }

            composable(Routes.DISCOVER) {
                DiscoverScreen(
                    container = container,
                    onBookDownloadedAndOpen = { bookId -> navController.navigate(Routes.reader(bookId)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(container = container)
            }

            composable(
                route = Routes.READER,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                ReaderScreen(
                    bookId = bookId,
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenStoryteller = { id -> navController.navigate(Routes.storyteller(id)) }
                )
            }

            composable(
                route = Routes.STORYTELLER,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                StorytellerScreen(
                    bookId = bookId,
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                )
            }

            composable(Routes.PLAYER) {
                AudiobookPlayerScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
