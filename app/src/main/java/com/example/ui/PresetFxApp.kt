package com.example.ui

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.repository.PresetRepository
import com.example.domain.video.VideoExportManager
import com.example.ui.auth.SignInScreen
import com.example.ui.exports.ExportsScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.studio.StudioScreen
import com.example.ui.studio.StudioViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
data object StudioRoute

@Serializable
data object LibraryRoute

@Serializable
data object ExportsRoute

private data class TopLevelDestination<T : Any>(
    val route: T,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@Composable
fun PresetFxApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope,
            )
        }
    }

    val user = currentUser
    if (user == null) {
        SignInScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedStudioShell(
                user = user,
                onSignOut = {
                    signOutUser(credentialManager = credentialManager, scope = scope)
                },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun AuthenticatedStudioShell(
    user: FirebaseUser,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val useNavigationRail = configuration.screenWidthDp >= 600 ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val viewModel: StudioViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                StudioViewModel(
                    repository = PresetRepository(db),
                    currentUserId = user.uid,
                    exportManager = VideoExportManager(app),
                )
            }
        },
    )

    val presetsState by viewModel.presetsState.collectAsStateWithLifecycle()
    val exportsState by viewModel.exportsState.collectAsStateWithLifecycle()
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
    val bypassEffects by viewModel.bypassEffects.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val destinations = remember {
        listOf(
            TopLevelDestination(
                route = StudioRoute,
                labelRes = R.string.nav_studio,
                selectedIcon = Icons.Filled.MovieFilter,
                unselectedIcon = Icons.Outlined.MovieFilter,
                testTag = "nav_studio",
            ),
            TopLevelDestination(
                route = LibraryRoute,
                labelRes = R.string.nav_library,
                selectedIcon = Icons.Filled.CollectionsBookmark,
                unselectedIcon = Icons.Outlined.CollectionsBookmark,
                testTag = "nav_library",
            ),
            TopLevelDestination(
                route = ExportsRoute,
                labelRes = R.string.nav_exports,
                selectedIcon = Icons.Filled.DownloadDone,
                unselectedIcon = Icons.Outlined.DownloadDone,
                testTag = "nav_exports",
            ),
        )
    }

    // Zero-permission Android Photo Picker for selecting videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.selectVideo(context, uri)
            }
        },
    )

    // SAF Document Picker for importing .ffx presets
    val ffxImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importFfxFromUri(context, uri) {
                    navController.navigate(StudioRoute) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    )

    // SAF CreateDocument for downloading .ffx preset file to local drive
    var pendingFfxDownload by remember { mutableStateOf<FfxPreset?>(null) }
    val ffxSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { destUri ->
            val preset = pendingFfxDownload
            pendingFfxDownload = null
            if (destUri != null && preset != null) {
                viewModel.writeFfxDescriptorToUri(context, preset, destUri)
            }
        },
    )

    // SAF CreateDocument for saving rendered MP4 copy to custom local folder
    var pendingVideoSaveCopy by remember { mutableStateOf<ExportRecord?>(null) }
    val mp4SaveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4"),
        onResult = { destUri ->
            val record = pendingVideoSaveCopy
            pendingVideoSaveCopy = null
            if (destUri != null && record != null) {
                viewModel.copyExportToDestination(context, record.localUriString, destUri)
            }
        },
    )

    Scaffold(
        containerColor = ObsidianBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!useNavigationRail) {
                NavigationBar(
                    containerColor = ObsidianSurface,
                ) {
                    destinations.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberCyan,
                                selectedTextColor = CyberCyan,
                                indicatorColor = DeepCyanContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.testTag(item.testTag),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useNavigationRail) {
                NavigationRail(containerColor = ObsidianSurface) {
                    destinations.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyberCyan,
                                selectedTextColor = CyberCyan,
                                indicatorColor = DeepCyanContainer,
                            ),
                            modifier = Modifier.testTag(item.testTag),
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = StudioRoute,
                ) {
                    composable<StudioRoute> {
                        StudioScreen(
                            selectedVideo = selectedVideo,
                            activePreset = activePreset,
                            bypassEffects = bypassEffects,
                            presetsState = presetsState,
                            exportState = exportState,
                            onPickVideo = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onImportFfx = {
                                ffxImportLauncher.launch(arrayOf("*/*"))
                            },
                            onSelectPreset = { preset ->
                                viewModel.applyPreset(preset)
                            },
                            onToggleBypass = { viewModel.toggleBypass() },
                            onUpdatePreset = { transform -> viewModel.updateWorkingPreset(transform) },
                            onResetPreset = { viewModel.resetWorkingPreset() },
                            onSavePresetToCloud = { asNew, name ->
                                viewModel.saveWorkingPresetToCloud(asNew, name)
                            },
                            onStartExport = { config ->
                                viewModel.startHighQualityExport(config)
                            },
                            onCancelExport = { viewModel.cancelExport() },
                            onResetExportState = { viewModel.resetExportState() },
                            onSaveCopyAs = { record ->
                                pendingVideoSaveCopy = record
                                mp4SaveCopyLauncher.launch(record.outputFileName)
                            },
                        )
                    }

                    composable<LibraryRoute> {
                        LibraryScreen(
                            presetsState = presetsState,
                            activePresetId = activePreset.id,
                            onImportFfxClick = {
                                ffxImportLauncher.launch(arrayOf("*/*"))
                            },
                            onApplyPreset = { preset ->
                                viewModel.applyPreset(preset)
                                navController.navigate(StudioRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onToggleFavorite = { preset -> viewModel.toggleFavorite(preset) },
                            onSavePreset = { preset, isNew -> viewModel.saveOrCreatePreset(preset, isNew) },
                            onExportFfxFile = { preset ->
                                pendingFfxDownload = preset
                                val fileName = if (preset.fileName.endsWith(".ffx", ignoreCase = true)) {
                                    preset.fileName
                                } else {
                                    "${preset.fileName}.ffx"
                                }
                                ffxSaveLauncher.launch(fileName)
                            },
                            onDeletePreset = { id -> viewModel.deletePreset(id) },
                            onSeedStarterPack = { viewModel.seedStarterPackIfEmpty() },
                        )
                    }

                    composable<ExportsRoute> {
                        ExportsScreen(
                            user = user,
                            exportsState = exportsState,
                            onSaveCopyToFolder = { record ->
                                pendingVideoSaveCopy = record
                                mp4SaveCopyLauncher.launch(record.outputFileName)
                            },
                            onDeleteExportRecord = { id -> viewModel.deleteExportRecord(id) },
                            onSignOut = onSignOut,
                        )
                    }
                }
            }
        }
    }
}
