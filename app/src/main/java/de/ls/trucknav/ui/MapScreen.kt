package de.ls.trucknav.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.ls.trucknav.data.VehicleMode
import de.ls.trucknav.model.HeightRestriction
import de.ls.trucknav.model.JunctionSign
import de.ls.trucknav.model.LaneGuide
import de.ls.trucknav.model.Maneuver
import de.ls.trucknav.model.Place
import de.ls.trucknav.model.SpeedCamera
import de.ls.trucknav.model.TrafficKind
import de.ls.trucknav.model.TrafficWarning
import de.ls.trucknav.nav.NavUpdate
import de.ls.trucknav.ui.components.SpeedLimitSign
import de.ls.trucknav.ui.components.WarningTriangle
import de.ls.trucknav.ui.map.MapControls
import de.ls.trucknav.ui.map.TruckMapView
import de.ls.trucknav.ui.theme.AppBackground
import de.ls.trucknav.ui.theme.AppDanger
import de.ls.trucknav.ui.theme.AppPrimary
import de.ls.trucknav.ui.theme.AppSecondary
import de.ls.trucknav.ui.theme.AppSurface
import de.ls.trucknav.ui.theme.AppSurfaceVariant
import de.ls.trucknav.ui.theme.AppText
import de.ls.trucknav.ui.theme.AppTextMuted
import de.ls.trucknav.util.formatDistance
import de.ls.trucknav.util.formatDuration
import de.ls.trucknav.util.formatEta
import de.ls.trucknav.util.formatSpeed
import kotlin.math.roundToInt

@Composable
fun MapScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var recenterSignal by remember { mutableIntStateOf(0) }
    val mapControls = remember { MapControls() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val view = LocalView.current
    DisposableEffect(state.navActive) {
        view.keepScreenOn = state.navActive
        onDispose { view.keepScreenOn = false }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice)
        viewModel.clearNotice()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Auf grossen Bildschirmen (Tablet/Querformat) werden die schwebenden
        // Karten in der Breite begrenzt und zentriert, statt ueber die volle
        // Breite gestreckt zu werden. Auf dem Handy bleibt alles unveraendert.
        val panelWidth = if (maxWidth >= LARGE_SCREEN_MIN_WIDTH) {
            Modifier.widthIn(max = PANEL_MAX_WIDTH)
        } else {
            Modifier
        }
        // Im Querformat bzw. auf niedrigen Bildschirmen bleibt weniger Hoehe:
        // kleinere Karten-Abstaende und scrollbare Bedienkarten, damit sich die
        // Elemente nicht ueberlagern.
        val compactHeight = maxHeight < COMPACT_HEIGHT_MIN
        val mapTopPadding = if (compactHeight) 96.dp else 150.dp
        val mapBottomPadding = when {
            compactHeight -> 140.dp
            state.navActive -> 190.dp
            else -> 250.dp
        }

        TruckMapView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            recenterSignal = recenterSignal,
            controls = mapControls,
            topPadding = mapTopPadding,
            bottomPadding = mapBottomPadding,
        )

        Text(
            text = "© OpenStreetMap-Mitwirkende",
            style = MaterialTheme.typography.labelSmall,
            color = AppTextMuted.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = if (compactHeight) 52.dp else 78.dp, end = 18.dp),
        )

        if (!state.navActive) {
            TopBar(
                state = state,
                onSearch = viewModel::openSearch,
                onSettings = viewModel::openSettings,
                onToggleMode = viewModel::toggleVehicleMode,
                onClear = viewModel::clearDestination,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(panelWidth),
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(panelWidth)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .then(
                        if (compactHeight) {
                            Modifier
                                .heightIn(max = maxHeight * 0.5f)
                                .verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                    )
            ) {
                InstructionCard(state)
                val nav = state.nav
                val maneuver = nav?.nextManeuver
                val showLanes = !state.arrived && nav != null && !nav.offRoute &&
                    maneuver != null &&
                    nav.distanceToManeuver <= LANE_GUIDE_DISTANCE_M &&
                    (maneuver.lanes.size >= 2 || maneuver.sign != null)
                if (showLanes) {
                    Spacer(Modifier.height(8.dp))
                    LaneGuidancePanel(
                        maneuver = maneuver,
                        distance = nav.distanceToManeuver,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(panelWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
                .then(
                    if (compactHeight) {
                        Modifier
                            .heightIn(max = maxHeight * 0.6f)
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                )
        ) {
            state.heightWarning?.let { restriction ->
                HeightWarningBanner(
                    restriction = restriction,
                    distance = state.heightWarningDistance,
                )
                Spacer(Modifier.height(8.dp))
            }

            state.trafficWarning?.let { warning ->
                TrafficWarningBanner(
                    warning = warning,
                    distance = state.trafficWarningDistance,
                )
                Spacer(Modifier.height(8.dp))
            }

            state.cameraWarning?.let { camera ->
                CameraWarningBanner(
                    camera = camera,
                    distance = state.cameraWarningDistance,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Zentrieren ist immer erreichbar, auch ohne aktive Zielfuehrung.
            // Waehrend der Navigation sitzt der Button im NavDashboard.
            if (!state.navActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (state.location != null) {
                        SpeedBadge(state)
                    } else {
                        Spacer(Modifier.width(0.dp))
                    }
                    RoundIconButton(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Auf Position zentrieren",
                        onClick = { recenterSignal++ },
                    )
                }
                if (state.route != null || state.routing || state.destination != null) {
                    Spacer(Modifier.height(8.dp))
                }
            }

            when {
                state.navActive -> NavDashboard(
                    state = state,
                    onStop = viewModel::stopNavigation,
                    onToggleVoice = viewModel::toggleVoice,
                    onRecenter = { recenterSignal++ },
                )

                state.route != null -> RouteCard(
                    state = state,
                    onStart = viewModel::startNavigation,
                    onClear = viewModel::clearDestination,
                    onToggleFavorite = viewModel::toggleFavorite,
                )

                state.destination != null && state.routeError != null -> ErrorCard(
                    message = state.routeError ?: "",
                    onRetry = viewModel::retryRoute,
                    onCancel = viewModel::clearDestination,
                )

                state.routing || state.destination != null -> RoutingCard(state)
            }
        }

        // Zoom und Kompass am rechten Rand – immer erreichbar.
        MapControlButtons(
            controls = mapControls,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    bottom = if (compactHeight) 150.dp else 220.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
        )
        if (state.arrived && state.navActive) {
            ArrivalOverlay(
                destinationName = state.destination?.name,
                onStop = viewModel::stopNavigation,
            )
        }

        if (state.showSearch) {
            SearchSheet(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onSelect = viewModel::selectDestination,
                onToggleFavorite = viewModel::toggleFavorite,
                onClearHistory = viewModel::clearHistory,
                onDismiss = viewModel::closeSearch,
            )
        }

        if (state.showSettings) {
            SettingsSheet(
                state = state,
                onSave = { profile, options ->
                    viewModel.saveProfile(profile)
                    viewModel.saveAppSettings(
                        routingUrl = options.routingUrl,
                        vehicleMode = options.vehicleMode,
                        carFallback = options.carFallback,
                        cameraWarnings = options.cameraWarnings,
                        trafficWarnings = options.trafficWarnings,
                        truckPois = options.truckPois,
                        tankpoolPois = options.tankpoolPois,
                        heightWarnings = options.heightWarnings,
                        mapStyleMode = options.mapStyleMode,
                    )
                    if (options.voiceEnabled != state.voiceEnabled) viewModel.toggleVoice()
                    viewModel.closeSettings()
                },
                onCheckUpdates = { viewModel.checkForUpdates(manual = true) },
                onInstallUpdate = viewModel::installUpdate,
                onDismiss = viewModel::closeSettings,
            )
        }

        if (state.showUpdateDialog) {
            UpdateDialog(
                state = state,
                onInstall = viewModel::installUpdate,
                onDismiss = viewModel::dismissUpdateDialog,
            )
        }
    }
}

@Composable
private fun TopBar(
    state: UiState,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onToggleMode: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = AppSurface,
                shadowElevation = 6.dp,
            ) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalShipping,
                        contentDescription = "LKW-Profil und Einstellungen",
                        tint = AppPrimary,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = AppSurface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSearch),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = AppTextMuted,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = state.destination?.name ?: "Ziel suchen",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.destination != null) AppText else AppTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (state.destination != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = AppSurface,
                    shadowElevation = 6.dp,
                ) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Ziel entfernen",
                            tint = AppTextMuted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.speedLimitKmh?.let { limit ->
                SpeedLimitSign(limitKmh = limit, size = 32.dp)
            }
            VehicleModePill(state = state, onToggle = onToggleMode)
            InfoPill(
                text = when {
                    state.location == null -> "GPS wird gesucht …"
                    else -> "GPS ±${state.accuracy.roundToInt()} m"
                },
                warning = state.location == null,
            )
        }
    }
}

@Composable
private fun InfoPill(text: String, warning: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = AppSurfaceVariant.copy(alpha = 0.94f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (warning) AppSecondary else AppTextMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Zeigt den aktiven Fahrzeugmodus und schaltet beim Antippen zwischen LKW und
 * PKW um (Kurzwahl ohne die Einstellungen zu oeffnen).
 */
@Composable
private fun VehicleModePill(state: UiState, onToggle: () -> Unit) {
    val car = state.vehicleMode == VehicleMode.CAR
    Surface(
        shape = RoundedCornerShape(50),
        color = if (car) AppSurfaceVariant.copy(alpha = 0.94f) else AppPrimary,
        modifier = Modifier.clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (car) Icons.Filled.DirectionsCar else Icons.Filled.LocalShipping,
                contentDescription = "Fahrzeugmodus umschalten",
                tint = if (car) AppTextMuted else Color.White,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (car) {
                    "PKW"
                } else {
                    String.format(
                        java.util.Locale.GERMANY,
                        "LKW %.1f m · %.0f t",
                        state.profile.height,
                        state.profile.weight,
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (car) AppTextMuted else Color.White,
            )
        }
    }
}

/** Dauerhafte Geschwindigkeitsanzeige unten links (auch ohne Navigation). */
@Composable
private fun SpeedBadge(state: UiState) {
    val over = state.speedLimitKmh != null && state.speedKmh > state.speedLimitKmh + 3
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AppSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = state.speedKmh.roundToInt().toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (over) AppDanger else AppText,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "km/h",
                style = MaterialTheme.typography.labelSmall,
                color = AppTextMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/** Zoom- und Kompass-Schaltflaechen am rechten Kartenrand. */
@Composable
private fun MapControlButtons(
    controls: MapControls,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundIconButton(
            imageVector = Icons.Filled.Explore,
            contentDescription = if (controls.northUp) {
                "In Fahrtrichtung ausrichten"
            } else {
                "Nach Norden ausrichten"
            },
            active = controls.northUp,
            size = 48.dp,
            onClick = { controls.compass() },
        )
        RoundIconButton(
            imageVector = Icons.Filled.Add,
            contentDescription = "Vergrößern",
            size = 48.dp,
            onClick = { controls.zoomIn() },
        )
        RoundIconButton(
            imageVector = Icons.Filled.Remove,
            contentDescription = "Verkleinern",
            size = 48.dp,
            onClick = { controls.zoomOut() },
        )
    }
}

/**
 * Runde Schaltflaeche in der UI-Farbe (Rot) mit weissem Symbol.
 * [active] kennzeichnet einen aktiven Zustand zusaetzlich mit weissem Rand.
 */
@Composable
private fun RoundIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 52.dp,
    active: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = AppPrimary,
        border = if (active) BorderStroke(2.dp, Color.White) else null,
        shadowElevation = 6.dp,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun InstructionCard(state: UiState) {
    val nav = state.nav
    val maneuver = nav?.nextManeuver
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = maneuverIcon(maneuver?.type ?: 0),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        state.arrived -> "Ziel erreicht"
                        nav == null -> "Navigation startet …"
                        nav.distanceToManeuver < 35.0 -> "Jetzt"
                        else -> "In ${formatDistance(nav.distanceToManeuver)}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                )
                Text(
                    text = maneuver?.instruction ?: "Route wird geladen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nav != null && !state.arrived) {
                    Text(
                        text = "Noch ${formatDistance(nav.remainingDistance)} · Ankunft ${formatEta(nav.remainingTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTextMuted,
                    )
                }
                if (state.routing || nav?.offRoute == true) {
                    Text(
                        text = if (state.routing) {
                            "Route wird neu berechnet …"
                        } else {
                            "Route verlassen – Neuberechnung läuft"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppSecondary,
                    )
                }
            }
            state.speedLimitKmh?.let { limit ->
                Spacer(Modifier.width(12.dp))
                SpeedLimitSign(limitKmh = limit, size = 54.dp)
            }
        }
    }
}

/**
 * Zeigt die zu nehmende Spur an der naechsten Verzweigung sowie eine
 * generierte Beschilderung (z. B. Autobahn-Ausfahrt).
 */
@Composable
private fun LaneGuidancePanel(maneuver: Maneuver, distance: Double) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (distance < 35.0) "Jetzt" else "In ${formatDistance(distance)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppSecondary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = maneuver.instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            maneuver.sign?.let { sign ->
                Spacer(Modifier.height(10.dp))
                JunctionSignView(sign)
            }
            if (maneuver.lanes.size >= 2) {
                Spacer(Modifier.height(10.dp))
                LaneRow(maneuver.lanes)
            }
        }
    }
}

/** Reihe von Spur-Pfeilen; die aktive Spur ist rot hervorgehoben. */
@Composable
private fun LaneRow(lanes: List<LaneGuide>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lanes.forEach { lane ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (lane.active) AppPrimary else AppSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = laneIcon(lane.directions),
                        contentDescription = null,
                        tint = if (lane.active) Color.White else AppTextMuted,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

/**
 * Generiertes Schild: Autobahnen blau/weiss, Bundes-, Land- und Kreisstrassen
 * gelb/schwarz (StVO-Beschilderung).
 */
@Composable
private fun JunctionSignView(sign: JunctionSign) {
    val background = if (sign.highway) AutobahnBlue else BundesYellow
    val borderColor = if (sign.highway) Color.White else Color.Black
    val textColor = if (sign.highway) Color.White else Color.Black
    val chipTextColor = if (sign.highway) AutobahnBlue else Color.Black
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        border = BorderStroke(2.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sign.exitNumber?.let { number ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                ) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Column {
                if (sign.branch.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sign.branch.forEach { number ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White,
                            ) {
                                Text(
                                    text = number,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = chipTextColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                val targets = sign.toward.ifEmpty { listOfNotNull(sign.name) }
                if (targets.isEmpty()) {
                    Text(
                        text = "Ausfahrt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                    )
                } else {
                    targets.forEach { target ->
                        Text(
                            text = target,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Fahrtrichtung (Bitmaske) -> passendes Material-Symbol. */
private fun laneIcon(directions: Int): ImageVector =
    when (LaneGuide.primaryDirection(directions)) {
        LaneGuide.U_TURN -> Icons.Filled.UTurnLeft
        LaneGuide.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
        LaneGuide.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
        LaneGuide.LEFT -> Icons.Filled.TurnLeft
        LaneGuide.RIGHT -> Icons.Filled.TurnRight
        LaneGuide.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
        LaneGuide.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
        LaneGuide.MERGE_LEFT -> Icons.Filled.ForkLeft
        LaneGuide.MERGE_RIGHT -> Icons.Filled.ForkRight
        else -> Icons.Filled.Straight
    }

@Composable
private fun RouteCard(
    state: UiState,
    onStart: () -> Unit,
    onClear: () -> Unit,
    onToggleFavorite: (Place) -> Unit,
) {
    val route = state.route ?: return
    val destination = state.destination
    val isFavorite = destination != null && state.favorites.any { it == destination }
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = AppSurface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.destination?.name ?: "Ziel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!state.destination?.subtitle.isNullOrBlank()) {
                        Text(
                            text = state.destination?.subtitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!route.truckUsed) {
                    WarningPill("PKW-Route")
                }
                if (destination != null) {
                    IconButton(onClick = { onToggleFavorite(destination) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isFavorite) {
                                "Favorit entfernen"
                            } else {
                                "Als Favorit speichern"
                            },
                            tint = if (isFavorite) AppPrimary else AppTextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDistance(route.distance),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = formatDuration(route.time),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppText,
                    )
                    Text(
                        text = "Ankunft ${formatEta(route.time)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextMuted,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.vehicleMode == VehicleMode.CAR) {
                    "PKW-Modus"
                } else {
                    "LKW-Profil: ${state.profile.summary()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppTextMuted,
            )

            if (state.routing) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Navigation starten", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Verwerfen")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Karten- und Routendaten: OpenStreetMap. Beschilderung hat Vorrang.",
                style = MaterialTheme.typography.labelSmall,
                color = AppTextMuted,
            )
        }
    }
}

@Composable
private fun NavDashboard(
    state: UiState,
    onStop: () -> Unit,
    onToggleVoice: () -> Unit,
    onRecenter: () -> Unit,
) {
    val nav = state.nav
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = AppSurface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    value = nav?.let { formatDistance(it.remainingDistance) } ?: "–",
                    label = "verbleibend",
                    modifier = Modifier.weight(1f),
                )
                StatItem(
                    value = nav?.let { formatDuration(it.remainingTime) } ?: "–",
                    label = "Fahrzeit",
                    modifier = Modifier.weight(1f),
                )
                StatItem(
                    value = nav?.let { formatEta(it.remainingTime) } ?: "–",
                    label = "Ankunft",
                    modifier = Modifier.weight(1f),
                )
                StatItem(
                    value = formatSpeed(state.speedKmh),
                    label = "aktuell",
                    modifier = Modifier.weight(1f),
                    valueColor = if (state.speedLimitKmh != null &&
                        state.speedKmh > state.speedLimitKmh + 3
                    ) {
                        AppDanger
                    } else {
                        AppText
                    },
                )
            }

            if (nav?.offRoute == true) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WarningTriangle(size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Route wird neu berechnet …",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppSecondary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Navigation beenden")
                }
                RoundIconButton(
                    imageVector = if (state.voiceEnabled) {
                        Icons.Filled.VolumeUp
                    } else {
                        Icons.Filled.VolumeOff
                    },
                    contentDescription = if (state.voiceEnabled) "Sprachansage aus" else "Sprachansage an",
                    onClick = onToggleVoice,
                )
                RoundIconButton(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "Auf Position zentrieren",
                    onClick = onRecenter,
                )
            }
        }
    }
}

@Composable
private fun CameraWarningBanner(camera: SpeedCamera, distance: Double) {    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarningTriangle(size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Blitzer in ${formatDistance(distance)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                    maxLines = 1,
                )
                Text(
                    text = camera.maxSpeedKmh?.let { "erlaubt $it km/h · OpenStreetMap" }
                        ?: "Geschwindigkeitskontrolle · OpenStreetMap",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TrafficWarningBanner(warning: TrafficWarning, distance: Double) {
    val label = when {
        warning.blocked -> "Sperrung"
        warning.kind == TrafficKind.CLOSURE -> "Sperrung"
        warning.jam -> "Stau"
        warning.kind == TrafficKind.ROADWORKS -> "Baustelle"
        else -> "Verkehrsmeldung"
    }
    val detail = buildString {
        append(warning.subtitle ?: warning.title)
        if (warning.jam && warning.averageSpeedKmh != null) {
            append(" · Ø ${warning.averageSpeedKmh} km/h")
        }
        if (warning.delayMinutes != null && warning.delayMinutes > 0) {
            append(" · +${warning.delayMinutes} min")
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarningTriangle(size = 30.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$label in ${formatDistance(distance)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                    maxLines = 1,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeightWarningBanner(restriction: HeightRestriction, distance: Double) {
    val height = String.format(java.util.Locale.GERMANY, "%.2f", restriction.maxHeightM)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarningTriangle(size = 30.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Durchfahrtshöhe $height m in ${formatDistance(distance)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                    maxLines = 1,
                )
                Text(
                    text = restriction.name ?: "Niedrigere Durchfahrt als Fahrzeughöhe · OpenStreetMap",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppText,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppTextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoutingCard(state: UiState) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AppSurface,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = AppPrimary,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Route wird berechnet …",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppText,
                )
                Text(
                    text = state.destination?.name ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AppSurface,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WarningTriangle(size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Keine Route",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AppTextMuted,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Erneut versuchen")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Abbrechen")
                }
            }
        }
    }
}

@Composable
private fun WarningPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = AppSecondary.copy(alpha = 0.18f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = AppSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ArrivalOverlay(destinationName: String?, onStop: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AppSurface,
            shadowElevation = 16.dp,
            modifier = Modifier
                .padding(28.dp)
                .widthIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ziel erreicht",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppText,
                )
                if (!destinationName.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = destinationName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Navigation beenden")
                }
            }
        }
    }
}

/** Ab dieser Breite gelten schwebende Karten als Grossbild (Tablet/Querformat). */
private val LARGE_SCREEN_MIN_WIDTH = 600.dp

/** Maximale Breite der schwebenden Karten auf grossen Bildschirmen. */
private val PANEL_MAX_WIDTH = 460.dp

/**
 * Unter dieser Hoehe (typisch Querformat auf dem Handy) wird das Layout
 * kompakter: kleinere Karten-Abstaende und scrollbare Bedienkarten.
 */
private val COMPACT_HEIGHT_MIN = 520.dp

/** Ab dieser Entfernung zum Manoever wird die Spurführung eingeblendet. */
private const val LANE_GUIDE_DISTANCE_M = 800.0

/** Farbe des generierten Autobahn-Schilds. */
private val AutobahnBlue = Color(0xFF1A4FA0)

/** Farbe des generierten Bundes-/Land-/Kreisstrassen-Schilds (StVO-Gelb). */
private val BundesYellow = Color(0xFFF2C200)
