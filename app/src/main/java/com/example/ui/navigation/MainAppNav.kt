package com.example.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.viewmodel.*

import com.example.data.db.AppDatabase
import com.example.data.supabase.SupabaseClientProvider
import com.example.data.supabase.SupabaseSyncRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Login : Screen("login", "Login", Icons.Default.Lock)
    object Setup : Screen("setup", "Setup", Icons.Default.Build)
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Contacts : Screen("contacts", "Contacts", Icons.Default.People)
    object Relations : Screen("relations", "Directory", Icons.Default.ContactPhone)
    object Memory : Screen("memory", "Memory", Icons.Default.Psychology)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainAppNav() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val auth = remember { SupabaseClientProvider.client.auth }
    val isAuthenticated = remember { mutableStateOf(auth.currentUserOrNull() != null) }

    LaunchedEffect(isAuthenticated.value) {
        if (isAuthenticated.value) {
            try {
                com.example.service.AutoReplyForegroundService.startService(context)
            } catch (e: Exception) {
                android.util.Log.e("MainAppNav", "Failed to auto-start foreground service: ${e.message}")
            }
        }
    }

    val mainStartDestination = remember {
        val hasNotifAccess = isNotificationListenerEnabled(context)
        val hasPostNotif = isPostNotificationsPermissionGranted(context)
        if (!hasNotifAccess || !hasPostNotif) {
            Screen.Setup.route
        } else {
            Screen.Home.route
        }
    }

    val initialStartDestination = if (isAuthenticated.value) mainStartDestination else Screen.Login.route

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Contacts,
        Screen.Memory,
        Screen.Profile,
        Screen.Settings
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.testTag("main_bottom_navigation_bar")
                ) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialStartDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        isAuthenticated.value = true
                        scope.launch {
                            val db = AppDatabase.getDatabase(context)
                            SupabaseSyncRepository(db).restoreAllDataFromCloud(context)
                        }
                        navController.navigate(mainStartDestination) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Setup.route) {
                SetupScreen(
                    onFinishSetup = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                val homeVm: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeVm,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToSetup = { navController.navigate(Screen.Setup.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
                )
            }

            composable(Screen.Contacts.route) {
                val contactsVm: ContactsViewModel = viewModel()
                ContactsScreen(
                    viewModel = contactsVm,
                    onSelectContact = { contactId ->
                        navController.navigate("contact_detail/$contactId")
                    }
                )
            }

            composable(Screen.Relations.route) {
                val relationsVm: KnownRelationsViewModel = viewModel()
                KnownRelationsScreen(viewModel = relationsVm)
            }

            composable(Screen.Memory.route) {
                val memoryVm: MemoryManagerViewModel = viewModel()
                MemoryManagerScreen(viewModel = memoryVm)
            }

            composable(Screen.Profile.route) {
                val profileVm: ProfileViewModel = viewModel()
                ProfileScreen(
                    viewModel = profileVm,
                    onNavigateBack = { navController.popBackStack() },
                    onSignOut = {
                        isAuthenticated.value = false
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                val settingsVm: SettingsViewModel = viewModel()
                SettingsScreen(
                    viewModel = settingsVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "contact_detail/{contactId}",
                arguments = listOf(navArgument("contactId") { type = NavType.StringType })
            ) { backStackEntry ->
                val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
                val context = LocalContext.current
                val factory = remember(contactId) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ContactDetailViewModel(context.applicationContext as Application, contactId) as T
                        }
                    }
                }
                val detailVm: ContactDetailViewModel = viewModel(factory = factory)

                ContactDetailScreen(
                    viewModel = detailVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
