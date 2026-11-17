package de.ls.trucknav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.ls.trucknav.R
import de.ls.trucknav.model.TruckPoi
import de.ls.trucknav.ui.theme.AppPrimary
import de.ls.trucknav.ui.theme.AppSurface
import de.ls.trucknav.ui.theme.AppSurfaceVariant
import de.ls.trucknav.ui.theme.AppText
import de.ls.trucknav.ui.theme.AppTextMuted
import de.ls.trucknav.util.formatDistance

/**
 * Fenster mit den Tankpool24-Stationen: ohne aktive Zielfuehrung im Umkreis um
 * die Position (100 km), mit aktiver Zielfuehrung entlang der Route (15 km).
 * Tippen auf eine Station plant eine Route dorthin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankpoolListSheet(
    state: UiState,
    onSelect: (TruckPoi) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alongRoute = state.navActive && state.route != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface,
        contentColor = AppText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TankpoolLogo(size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tankpool24",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppText,
                    )
                    Text(
                        text = if (alongRoute) {
                            "Stationen entlang der Route (15 km)"
                        } else {
                            "Stationen im Umkreis von 100 km"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextMuted,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                state.tankpoolLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AppPrimary)
                }

                state.tankpoolEntries.isEmpty() -> Text(
                    text = if (alongRoute) {
                        "Keine Tankpool24-Station im Umkreis der Route gefunden."
                    } else {
                        "Keine Tankpool24-Station im Umkreis von 100 km gefunden."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTextMuted,
                    modifier = Modifier.padding(vertical = 20.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                ) {
                    items(state.tankpoolEntries, key = { "tp-${it.poi.id}" }) { entry ->
                        TankpoolRow(
                            entry = entry,
                            alongRoute = alongRoute,
                            onClick = { onSelect(entry.poi) },
                        )
                        HorizontalDivider(color = AppTextMuted.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}

/** Eine Zeile der Tankpool24-Liste mit Name, Anschrift und Entfernung. */
@Composable
private fun TankpoolRow(
    entry: TankpoolEntry,
    alongRoute: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TankpoolLogo(size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.poi.name ?: "Tankpool24-Station",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.poi.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatDistance(entry.distanceMeters),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = AppPrimary,
            )
            if (alongRoute) {
                Text(
                    text = "an der Route",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTextMuted,
                )
            }
        }
    }
}

/** Tankpool24-Logo in einem weissen Kreis mit rotem Rand. */
@Composable
private fun TankpoolLogo(size: Dp) {
    Surface(
        shape = CircleShape,
        color = AppSurfaceVariant,
        border = BorderStroke(2.dp, AppPrimary),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.tankpool24_logo),
                contentDescription = null,
                modifier = Modifier.size(size * 0.68f),
            )
        }
    }
}
