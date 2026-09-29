package com.thekubics.scanly.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thekubics.scanly.presentation.cloud.CloudScreen
import com.thekubics.scanly.presentation.cloud.CloudViewModel
import com.thekubics.scanly.presentation.cloud.StorageDashboardScreen
import com.thekubics.scanly.presentation.cloud.StorageDashboardViewModel
import com.thekubics.scanly.presentation.cloud.StorageProvidersScreen
import com.thekubics.scanly.presentation.cloud.StorageProvidersViewModel
import com.thekubics.scanly.presentation.cloud.StorageSetupGuideScreen
import com.thekubics.scanly.presentation.common.AppLockGate
import com.thekubics.scanly.presentation.common.OnboardingDialog
import com.thekubics.scanly.presentation.editor.EditorScreen
import com.thekubics.scanly.presentation.editor.EditorViewModel
import com.thekubics.scanly.presentation.folders.FolderDetailScreen
import com.thekubics.scanly.presentation.folders.FolderDetailViewModel
import com.thekubics.scanly.presentation.folders.FoldersScreen
import com.thekubics.scanly.presentation.folders.FoldersViewModel
import com.thekubics.scanly.presentation.home.HomeScreen
import com.thekubics.scanly.presentation.home.HomeViewModel
import com.thekubics.scanly.presentation.scanner.ScannerScreen
import com.thekubics.scanly.presentation.scanner.ScannerViewModel
import com.thekubics.scanly.presentation.search.SearchScreen
import com.thekubics.scanly.presentation.search.SearchViewModel
import com.thekubics.scanly.presentation.settings.SettingsScreen
import com.thekubics.scanly.presentation.settings.SettingsViewModel
import com.thekubics.scanly.presentation.trash.TrashScreen
import com.thekubics.scanly.presentation.trash.TrashViewModel
import com.thekubics.scanly.presentation.viewer.ViewerScreen
import com.thekubics.scanly.presentation.viewer.ViewerViewModel
import com.thekubics.scanly.data.storage.StorageProviderType

/**
 * Root navigation composable that wires all screens together with Hilt ViewModels and M3 motion transitions.
 */
@Composable
fun AppNavigation(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by settingsViewModel.settings.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val hideBottomBarRoutes = listOf(
        Screen.Scanner.route,
        Screen.Editor.route,
        Screen.Viewer.route,
        Screen.StorageDashboard.route,
        Screen.StorageProviders.route,
        Screen.StorageSetupGuide.route
    )
    val shouldShowBottomBar = currentRoute !in hideBottomBarRoutes

    AppLockGate(isEnabled = settings.appLockEnabled) {
        if (!settings.hasSeenOnboarding) {
            OnboardingDialog(onComplete = { settingsViewModel.completeOnboarding() })
        }
        Scaffold(
            // Each screen owns the status bar through its top bar. Leaving the
            // default insets here painted a second status-bar band above that
            // bar and pushed the last rows under the system navigation.
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            bottomBar = {
                if (shouldShowBottomBar) {
                    BottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                enterTransition = {
                    fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200)) + slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(200)
                    )
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300)
                    )
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(200)) + slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(200)
                    )
                }
            ) {
                // Home
                composable(Screen.Home.route) {
                    val viewModel: HomeViewModel = hiltViewModel()
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToViewer = { docId ->
                            navController.navigate(Screen.Viewer.createRoute(docId))
                        },
                        onNavigateToScanner = {
                            navController.navigate(Screen.Scanner.route)
                        }
                    )
                }

                // Scanner
                composable(Screen.Scanner.route) {
                    val viewModel: ScannerViewModel = hiltViewModel()
                    ScannerScreen(
                        viewModel = viewModel,
                        onNavigateToEditor = { docId ->
                            navController.navigate(Screen.Editor.createRoute(docId)) {
                                popUpTo(Screen.Scanner.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Editor
                composable(
                    route = Screen.Editor.route,
                    arguments = listOf(navArgument("documentId") { type = NavType.StringType })
                ) {
                    val viewModel: EditorViewModel = hiltViewModel()
                    EditorScreen(
                        viewModel = viewModel,
                        onNavigateToViewer = { docId ->
                            navController.navigate(Screen.Viewer.createRoute(docId)) {
                                popUpTo(Screen.Home.route)
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Viewer
                composable(
                    route = Screen.Viewer.route,
                    arguments = listOf(navArgument("documentId") { type = NavType.StringType })
                ) {
                    val viewModel: ViewerViewModel = hiltViewModel()
                    ViewerScreen(
                        viewModel = viewModel,
                        onNavigateToEditor = { docId ->
                            navController.navigate(Screen.Editor.createRoute(docId))
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Folders
                composable(Screen.Folders.route) {
                    val viewModel: FoldersViewModel = hiltViewModel()
                    FoldersScreen(
                        viewModel = viewModel,
                        onNavigateToFolder = { folderId ->
                            navController.navigate(Screen.FolderDetail.createRoute(folderId))
                        }
                    )
                }

                // Folder Detail
                composable(
                    route = Screen.FolderDetail.route,
                    arguments = listOf(navArgument("folderId") { type = NavType.StringType })
                ) {
                    val viewModel: FolderDetailViewModel = hiltViewModel()
                    FolderDetailScreen(
                        viewModel = viewModel,
                        onNavigateToViewer = { docId ->
                            navController.navigate(Screen.Viewer.createRoute(docId))
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Cloud Storage Main Screen
                composable(Screen.Cloud.route) {
                    val viewModel: CloudViewModel = hiltViewModel()
                    CloudScreen(
                        viewModel = viewModel,
                        onNavigateToDashboard = { navController.navigate(Screen.StorageDashboard.route) },
                        onNavigateToProviders = { navController.navigate(Screen.StorageProviders.route) },
                        onDocumentClick = { docId ->
                            navController.navigate(Screen.Viewer.createRoute(docId))
                        }
                    )
                }

                // Storage Dashboard
                composable(Screen.StorageDashboard.route) {
                    val viewModel: StorageDashboardViewModel = hiltViewModel()
                    StorageDashboardScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Storage Providers Route
                composable(Screen.StorageProviders.route) {
                    val viewModel: StorageProvidersViewModel = hiltViewModel()
                    StorageProvidersScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToGuide = { providerType ->
                            navController.navigate(Screen.StorageSetupGuide.createRoute(providerType))
                        }
                    )
                }

                // Search
                composable(Screen.Search.route) {
                    val viewModel: SearchViewModel = hiltViewModel()
                    SearchScreen(
                        viewModel = viewModel,
                        onNavigateToViewer = { docId ->
                            navController.navigate(Screen.Viewer.createRoute(docId))
                        }
                    )
                }

                // Settings
                composable(Screen.Settings.route) {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToTrash = { navController.navigate(Screen.Trash.route) },
                        onNavigateToDashboard = { navController.navigate(Screen.StorageDashboard.route) },
                        onNavigateToProviders = { navController.navigate(Screen.StorageProviders.route) },
                        onNavigateToCloud = { navController.navigate(Screen.Cloud.route) },
                        onNavigateToSetupGuide = { providerType -> navController.navigate(Screen.StorageSetupGuide.route + "/$providerType") },
                        onNavigateToAbout = { /* TODO: navigate to about screen */ }
                    )
                }

                // Trash
                composable(Screen.Trash.route) {
                    val viewModel: TrashViewModel = hiltViewModel()
                    TrashScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Storage Setup Guide (per-provider help)
                composable(
                    route = Screen.StorageSetupGuide.route,
                    arguments = listOf(
                        navArgument("providerType") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val providerTypeStr = backStackEntry.arguments?.getString("providerType")
                    val providerType = runCatching {
                        StorageProviderType.valueOf(providerTypeStr ?: "TELEGRAM")
                    }.getOrDefault(StorageProviderType.TELEGRAM)
                    StorageSetupGuideScreen(
                        providerType = providerType,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
