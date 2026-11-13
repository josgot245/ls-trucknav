package de.ls.trucknav.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.ls.trucknav.model.Place
import de.ls.trucknav.model.SpeedCamera
import de.ls.trucknav.nav.NavUpdate
import de.ls.trucknav.ui.components.SpeedLimitSign
import de.ls.trucknav.ui.components.WarningTriangle
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        TruckMapView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            recenterSignal = recenterSignal,
        )

        Text(
            text = "© OpenStreetMap-Mitwirkende",
            style = MaterialTheme.typography.labelSmall,
            color = AppTextMuted.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 78.dp, end = 18.dp),
        )

        if (!state.navActive) {
            TopBar(
                state = state,
                onSearch = viewModel::openSearch,
                onSettings = viewModel::openSettings,
                onClear = viewModel::clearDestination,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                InstructionCard(state)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            state.cameraWarning?.let { camera ->
                CameraWarningBanner(
                    camera = camera,
                    distance = state.cameraWarningDistance,
                )
                Spacer(Modifier.height(8.dp))
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
                )

                state.destination != null && state.routeError != null -> ErrorCard(
                    message = state.routeError ?: "",
                    onRetry = viewModel::retryRoute,
                    onCancel = viewModel::clearDestination,
                )

                state.routing || state.destination != null -> RoutingCard(state)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 220.dp, start = 16.dp, end = 16.dp),
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
                onDismiss = viewModel::closeSearch,
            )
        }

        if (state.showSettings) {
            SettingsSheet(
                state = state,
                onSave = { profile, url, fallback, voice, cameras ->
                    viewModel.saveProfile(profile)
                    viewModel.saveAppSettings(url, fallback, cameras)
                    if (voice != state.voiceEnabled) viewModel.toggleVoice()
                    viewModel.closeSettings()
                },
                onDismiss = viewModel::closeSettings,
            )
        }
    }
}

@Composable
private fun TopBar(
    state: UiState,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
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
            InfoPill(
                text = String.format(
                    java.util.Locale.GERMANY,
                    "LKW %.1f m · %.0f t",
                    state.profile.height,
                    state.profile.weight,
                )
            )
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

@Composable
private fun RouteCard(
    state: UiState,
    onStart: () -> Unit,
    onClear: () -> Unit,
) {
    val route = state.route ?: return
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
                text = "LKW-Profil: ${state.profile.summary()}",
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
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = AppSecondary,
                        modifier = Modifier.size(18.dp),
                    )
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
                FilledTonalIconButton(
                    onClick = onToggleVoice,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = if (state.voiceEnabled) {
                            Icons.Filled.VolumeUp
                        } else {
                            Icons.Filled.VolumeOff
                        },
                        contentDescription = if (state.voiceEnabled) "Sprachansage aus" else "Sprachansage an",
                    )
                }
                FilledTonalIconButton(
                    onClick = onRecenter,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Auf Position zentrieren")
                }
            }
        }
    }
}

@Composable
private fun CameraWarningBanner(camera: SpeedCamera, distance: Double) {
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
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AppDanger,
                )
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
            modifier = Modifier.padding(28.dp),
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
