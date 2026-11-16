package de.ls.trucknav.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.Place
import de.ls.trucknav.ui.theme.AppPrimary
import de.ls.trucknav.ui.theme.AppSurface
import de.ls.trucknav.ui.theme.AppText
import de.ls.trucknav.ui.theme.AppTextMuted
import de.ls.trucknav.util.distanceMeters
import de.ls.trucknav.util.formatDistance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSelect: (Place) -> Unit,
    onToggleFavorite: (Place) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Vor der eigentlichen Suche zeigen wir Favoriten und Verlauf an.
    val showSuggestions = state.query.trim().length < 3

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
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Ziel suchen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppText,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Straße, Ort oder Firma") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Eingabe löschen")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )

            Spacer(Modifier.height(10.dp))

            if (state.searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

            if (showSuggestions) {
                SuggestionList(
                    state = state,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    onClearHistory = onClearHistory,
                )
            } else if (state.results.isEmpty()) {
                Text(
                    text = if (!state.searching) "Keine Treffer gefunden." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(state.results, key = { "res-${placeKey(it)}" }) { place ->
                        PlaceRow(
                            place = place,
                            from = state.location,
                            leadingIcon = Icons.Filled.LocationOn,
                            leadingTint = AppTextMuted,
                            favorite = state.favorites.any { it == place },
                            onToggleFavorite = onToggleFavorite,
                            onClick = { onSelect(place) },
                        )
                        HorizontalDivider(color = AppTextMuted.copy(alpha = 0.12f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Start: aktueller Standort",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                )
            }
        }
    }
}

/** Favoriten und Verlauf, sichtbar solange noch nicht gesucht wird. */
@Composable
private fun SuggestionList(
    state: UiState,
    onSelect: (Place) -> Unit,
    onToggleFavorite: (Place) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (state.favorites.isEmpty() && state.history.isEmpty()) {
        Text(
            text = "Mindestens 3 Zeichen eingeben.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTextMuted,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
    ) {
        if (state.favorites.isNotEmpty()) {
            item(key = "header-favorites") { SectionHeader("Favoriten") }
            items(state.favorites, key = { "fav-${placeKey(it)}" }) { place ->
                PlaceRow(
                    place = place,
                    from = state.location,
                    leadingIcon = Icons.Filled.Star,
                    leadingTint = AppPrimary,
                    favorite = true,
                    onToggleFavorite = onToggleFavorite,
                    onClick = { onSelect(place) },
                )
                HorizontalDivider(color = AppTextMuted.copy(alpha = 0.12f))
            }
        }
        if (state.history.isNotEmpty()) {
            item(key = "header-history") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Zuletzt", modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearHistory) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Leeren")
                    }
                }
            }
            items(state.history, key = { "his-${placeKey(it)}" }) { place ->
                PlaceRow(
                    place = place,
                    from = state.location,
                    leadingIcon = Icons.Filled.History,
                    leadingTint = AppTextMuted,
                    favorite = state.favorites.any { it == place },
                    onToggleFavorite = onToggleFavorite,
                    onClick = { onSelect(place) },
                )
                HorizontalDivider(color = AppTextMuted.copy(alpha = 0.12f))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = AppTextMuted,
        modifier = modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun PlaceRow(
    place: Place,
    from: GeoPoint?,
    leadingIcon: ImageVector,
    leadingTint: Color,
    favorite: Boolean,
    onToggleFavorite: (Place) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = leadingTint,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (place.subtitle.isNotBlank()) {
                Text(
                    text = place.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (from != null) {
            Text(
                text = formatDistance(distanceMeters(from, place.point)),
                style = MaterialTheme.typography.labelSmall,
                color = AppTextMuted,
            )
        }
        IconButton(onClick = { onToggleFavorite(place) }) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (favorite) "Favorit entfernen" else "Als Favorit speichern",
                tint = if (favorite) AppPrimary else AppTextMuted,
            )
        }
    }
}

private fun placeKey(place: Place): String =
    "${place.name}|${place.point.lat}|${place.point.lon}"
