package com.example.ui

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.example.data.model.ExportRecord
import com.example.data.model.FfxPreset
import com.example.data.repository.PresetRepository
import com.example.domain.video.UiState
import com.example.domain.video.VideoExportManager
import com.example.ui.exports.ExportsScreen
import com.example.ui.home.CreateProjectDialog
import com.example.ui.home.HomeScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.studio.StudioScreen
import com.example.ui.studio.StudioViewModel
import com.example.ui.theme.LunaMint
import com.example.ui.theme.LunaMintContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object LibraryRoute

@Serializable
data object EditorRoute

@Serializable
data object ExportsRoute

private data class LunaDestination<T : Any>(
    val route: T,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@OptIn(UnstableApi::class)
@Composable
fun LunaApp() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val useNavigationRail = configuration.screenWidthDp >= 600 ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val viewModel: StudioViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                StudioViewModel(
                    repository = PresetRepository(app),
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

    val projectTitle by viewModel.projectTitle.collectAsStateWithLifecycle()
    val aspectPreset by viewModel.aspectPreset.collectAsStateWithLifecycle()
    val projectResolution by viewModel.projectResolution.collectAsStateWithLifecycle()
    val projectFps by viewModel.projectFps.collectAsStateWithLifecycle()
    val canvasFitMode by viewModel.canvasFitMode.collectAsStateWithLifecycle()
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val selectedLayerId by viewModel.selectedLayerId.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val recentProjects by viewModel.recentProjects.collectAsStateWithLifecycle()

    var showCreateProjectDialog by remember { mutableStateOf(false) }

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
    val isInEditor = currentDestination?.hasRoute(EditorRoute::class) == true

    fun navigateTop(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val destinations = remember {
        listOf(
            LunaDestination(
                route = HomeRoute,
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_home",
            ),
            LunaDestination(
                route = LibraryRoute,
                labelRes = R.string.nav_presets,
                selectedIcon = Icons.Filled.CollectionsBookmark,
                unselectedIcon = Icons.Outlined.CollectionsBookmark,
                testTag = "nav_library",
            ),
            LunaDestination(
                route = EditorRoute,
                labelRes = R.string.nav_projects,
                selectedIcon = Icons.Filled.VideoSettings,
                unselectedIcon = Icons.Outlined.VideoSettings,
                testTag = "nav_studio",
            ),
            LunaDestination(
                route = ExportsRoute,
                labelRes = R.string.nav_exports,
                selectedIcon = Icons.Filled.DownloadDone,
                unselectedIcon = Icons.Outlined.DownloadDone,
                testTag = "nav_exports",
            ),
        )
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) viewModel.selectVideo(context, uri) },
    )

    val ffxImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importFfxFromUri(context, uri) {
                    navigateTop(EditorRoute)
                }
            }
        },
    )

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
            // Alight Motion Screenshot 2 floating bottom bar with centered mint '+' button (hidden inside Editor so timeline tools fit cleanly)
            if (!useNavigationRail && !isInEditor) {
                LunaBottomBar(
                    destinations = destinations,
                    isSelected = { dest -> currentDestination?.hasRoute(dest.route::class) == true },
                    onNavigate = { dest -> navigateTop(dest.route) },
                    onCenterCreateClick = { showCreateProjectDialog = true },
                )
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
                            onClick = { navigateTop(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes),
                                )
                            },
                            label = {
                                Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelSmall)
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = LunaMint,
                                selectedTextColor = LunaMint,
                                indicatorColor = LunaMintContainer,
                            ),
                            modifier = Modifier.testTag(item.testTag),
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                ) {
                    composable<HomeRoute> {
                        val loadedPresets = (presetsState as? UiState.Success)?.data.orEmpty()
                        HomeScreen(
                            presetsState = presetsState,
                            recentProjects = recentProjects,
                            showCreateDialog = showCreateProjectDialog,
                            onDismissCreateDialog = { showCreateProjectDialog = false },
                            onRequestCreateDialog = { showCreateProjectDialog = true },
                            onCreateProject = { title, aspect, res, fps ->
                                showCreateProjectDialog = false
                                viewModel.createNewProject(title, aspect, res, fps)
                                navigateTop(EditorRoute)
                            },
                            onOpenProject = { proj ->
                                viewModel.openProjectSummary(proj, loadedPresets)
                                navigateTop(EditorRoute)
                            },
                            onSelectTemplate = { preset ->
                                viewModel.applyPreset(preset)
                                navigateTop(EditorRoute)
                            },
                            onNavigateLibrary = { navigateTop(LibraryRoute) },
                            onImportFfxClick = { ffxImportLauncher.launch(arrayOf("*/*")) },
                        )
                    }

                    composable<EditorRoute> {
                        StudioScreen(
                            projectTitle = projectTitle,
                            aspectPreset = aspectPreset,
                            projectResolution = projectResolution,
                            projectFps = projectFps,
                            canvasFitMode = canvasFitMode,
                            selectedVideo = selectedVideo,
                            activePreset = activePreset,
                            layers = layers,
                            selectedLayerId = selectedLayerId,
                            playheadMs = playheadMs,
                            canUndo = canUndo,
                            canRedo = canRedo,
                            bypassEffects = bypassEffects,
                            presetsState = presetsState,
                            exportState = exportState,
                            onNavigateBackHome = { navigateTop(HomeRoute) },
                            onUpdateProjectSettings = { t, a, r, f ->
                                viewModel.updateProjectSettings(t, a, r, f)
                            },
                            onPickVideo = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onImportFfx = { ffxImportLauncher.launch(arrayOf("*/*")) },
                            onSelectPreset = { viewModel.applyPreset(it) },
                            onAddPresetLayer = { viewModel.addPresetLayer(it) },
                            onAddShapeLayer = { viewModel.addShapeLayer(it) },
                            onAddTextLayer = { viewModel.addTextLayer(it) },
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onToggleLayerVisibility = { viewModel.toggleLayerVisibility(it) },
                            onUpdatePlayhead = { viewModel.updatePlayhead(it) },
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onDuplicateLayer = { viewModel.duplicateSelectedLayer() },
                            onDeleteSelectedLayer = { viewModel.deleteSelectedLayer() },
                            onCycleCanvasFit = { viewModel.cycleCanvasFitMode() },
                            onTrimLeft = { viewModel.trimLeftAtPlayhead() },
                            onSplit = { viewModel.splitSelectedLayerAtPlayhead() },
                            onTrimRight = { viewModel.trimRightAtPlayhead() },
                            onToggleMute = { viewModel.toggleSelectedLayerMute() },
                            onSetSpeed = { viewModel.setSelectedLayerSpeed(it) },
                            onSetColor = { viewModel.setSelectedLayerColor(it) },
                            onToggleBorderShadow = { viewModel.toggleSelectedLayerBorderShadow() },
                            onSetBlendOpacity = { b, a -> viewModel.setSelectedLayerBlendAndOpacity(b, a) },
                            onUpdateTransform = { rot, flip -> viewModel.updateSelectedLayerTransform(rot, flip) },
                            onUpdateShapeOrText = { s, txt -> viewModel.updateSelectedLayerShapeOrText(s, txt) },
                            onToggleBypass = { viewModel.toggleBypass() },
                            onStartExport = { viewModel.startHighQualityExport(it) },
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
                            onImportFfxClick = { ffxImportLauncher.launch(arrayOf("*/*")) },
                            onApplyPreset = { preset ->
                                viewModel.applyPreset(preset)
                                navigateTop(EditorRoute)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
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
                            onDeletePreset = { viewModel.deletePreset(it) },
                            onSeedStarterPack = { viewModel.seedStarterPackIfEmpty() },
                        )
                    }

                    composable<ExportsRoute> {
                        ExportsScreen(
                            exportsState = exportsState,
                            onSaveCopyToFolder = { record ->
                                pendingVideoSaveCopy = record
                                mp4SaveCopyLauncher.launch(record.outputFileName)
                            },
                            onDeleteExportRecord = { viewModel.deleteExportRecord(it) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateProjectDialog) {
        CreateProjectDialog(
            defaultTitle = "LUNA Project ${recentProjects.size + 1}",
            onDismiss = { showCreateProjectDialog = false },
            onCreate = { title, aspect, res, fps ->
                showCreateProjectDialog = false
                viewModel.createNewProject(title, aspect, res, fps)
                navigateTop(EditorRoute)
            },
        )
    }
}

@Composable
private fun LunaBottomBar(
    destinations: List<LunaDestination<*>>,
    isSelected: (LunaDestination<*>) -> Boolean,
    onNavigate: (LunaDestination<*>) -> Unit,
    onCenterCreateClick: () -> Unit,
) {
    Surface(
        color = ObsidianSurface,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                destinations.take(2).forEach { item ->
                    BottomBarItem(item, isSelected(item)) { onNavigate(item) }
                }
                Spacer(modifier = Modifier.size(60.dp))
                destinations.drop(2).forEach { item ->
                    BottomBarItem(item, isSelected(item)) { onNavigate(item) }
                }
            }

            // Centered elevated Mint '+' Circle Button (Screenshot 2)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-10).dp)
                    .size(56.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(LunaMint)
                    .clickable(onClick = onCenterCreateClick)
                    .testTag("bottom_bar_create_plus"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Create New LUNA Project",
                    tint = ObsidianBackground,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    item: LunaDestination<*>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) LunaMint else Color(0xFFA7ACC4)
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(item.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = stringResource(item.labelRes),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
