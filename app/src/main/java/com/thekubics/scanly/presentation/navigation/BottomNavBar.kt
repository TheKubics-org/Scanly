package com.thekubics.scanly.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.thekubics.scanly.R

private data class NavItem(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelRes: Int
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        NavItem(
            route = Screen.Home.route,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            labelRes = R.string.nav_home
        ),
        NavItem(
            route = Screen.Folders.route,
            selectedIcon = Icons.Filled.Folder,
            unselectedIcon = Icons.Outlined.Folder,
            labelRes = R.string.nav_folders
        ),
        NavItem(
            route = Screen.Cloud.route,
            selectedIcon = Icons.Filled.Cloud,
            unselectedIcon = Icons.Outlined.Cloud,
            labelRes = R.string.nav_cloud
        ),
        NavItem(
            route = Screen.Search.route,
            selectedIcon = Icons.Filled.Search,
            unselectedIcon = Icons.Outlined.Search,
            labelRes = R.string.nav_search
        ),
        NavItem(
            route = Screen.Settings.route,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            labelRes = R.string.nav_settings
        )
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            val label = stringResource(id = item.labelRes)
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label) },
                selected = isSelected,
                onClick = { onNavigate(item.route) }
            )
        }
    }
}
