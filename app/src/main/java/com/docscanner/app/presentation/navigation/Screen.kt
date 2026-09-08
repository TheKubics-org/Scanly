package com.docscanner.app.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Editor : Screen("editor/{documentId}") {
        fun createRoute(documentId: String) = "editor/$documentId"
    }
    object Viewer : Screen("viewer/{documentId}") {
        fun createRoute(documentId: String) = "viewer/$documentId"
    }
    object Folders : Screen("folders")
    object FolderDetail : Screen("folder/{folderId}") {
        fun createRoute(folderId: String) = "folder/$folderId"
    }
    object Search : Screen("search")
    object Cloud : Screen("cloud")
    object StorageDashboard : Screen("storage_dashboard")
    object StorageProviders : Screen("storage_providers")
    object Settings : Screen("settings")
    object Trash : Screen("trash")
}
