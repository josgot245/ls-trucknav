package de.ls.trucknav.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.ls.trucknav.data.TruckProfile
import de.ls.trucknav.ui.theme.AppPrimary
import de.ls.trucknav.ui.theme.AppSurface
import de.ls.trucknav.ui.theme.AppText
import de.ls.trucknav.ui.theme.AppTextMuted
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: UiState,
    onSave: (TruckProfile, String, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var height by remember { mutableStateOf(formatNumber(state.profile.height)) }
    var width by remember { mutableStateOf(formatNumber(state.profile.width)) }
    var length by remember { mutableStateOf(formatNumber(state.profile.length)) }
    var weight by remember { mutableStateOf(formatNumber(state.profile.weight)) }
    var axleLoad by remember { mutableStateOf(formatNumber(state.profile.axleLoad)) }
    var axles by remember { mutableStateOf(state.profile.axles.toString()) }
    var hazmat by remember { mutableStateOf(state.profile.hazmat) }
    var avoidTolls by remember { mutableStateOf(state.profile.avoidTolls) }
    var avoidHighways by remember { mutableStateOf(state.profile.avoidHighways) }
    var routingUrl by remember { mutableStateOf(state.routingUrl) }
    var carFallback by remember { mutableStateOf(state.carFallback) }
    var voice by remember { mutableStateOf(state.voiceEnabled) }
    var cameraWarnings by remember { mutableStateOf(state.cameraWarnings) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface,
        contentColor = AppText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "LKW-Profil & Einstellungen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppText,
            )
            Text(
                text = "Diese Maße und Gewichte werden bei der Routenberechnung berücksichtigt.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTextMuted,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("Fahrzeug")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Höhe (m)", height, { height = it }, Modifier.weight(1f))
                NumberField("Breite (m)", width, { width = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Länge (m)", length, { length = it }, Modifier.weight(1f))
                NumberField("Gewicht (t)", weight, { weight = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Achslast (t)", axleLoad, { axleLoad = it }, Modifier.weight(1f))
                NumberField(
                    label = "Achsen",
                    value = axles,
                    onValueChange = { axles = it.filter { c -> c.isDigit() }.take(2) },
                    modifier = Modifier.weight(1f),
                    integer = true,
                )
            }

            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = "Gefahrgut",
                subtitle = "Gefahrguttransport berücksichtigen",
                checked = hazmat,
                onCheckedChange = { hazmat = it },
            )
            SwitchRow(
                title = "Maut vermeiden",
                subtitle = "Mautpflichtige Strecken umfahren",
                checked = avoidTolls,
                onCheckedChange = { avoidTolls = it },
            )
            SwitchRow(
                title = "Autobahn vermeiden",
                subtitle = "Bevorzugt über Bundes- und Landstraßen",
                checked = avoidHighways,
                onCheckedChange = { avoidHighways = it },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = AppTextMuted.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.dp))

            SectionTitle("App")
            OutlinedTextField(
                value = routingUrl,
                onValueChange = { routingUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Routing-Server") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = {
                    Text(
                        text = "Standard: öffentlicher Valhalla-Server. Für den Dauerbetrieb eigenen Server eintragen.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )

            Spacer(Modifier.height(6.dp))
            SwitchRow(
                title = "PKW-Route als Ausweichlösung",
                subtitle = "Wenn keine LKW-Route gefunden wird",
                checked = carFallback,
                onCheckedChange = { carFallback = it },
            )
            SwitchRow(
                title = "Sprachansagen",
                subtitle = "Fahranweisungen vorlesen",
                checked = voice,
                onCheckedChange = { voice = it },
            )
            SwitchRow(
                title = "Blitzer-Warnungen",
                subtitle = "Hinweise auf stationäre Blitzer (OpenStreetMap)",
                checked = cameraWarnings,
                onCheckedChange = { cameraWarnings = it },
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    val profile = TruckProfile(
                        height = parseNumber(height, state.profile.height),
                        width = parseNumber(width, state.profile.width),
                        length = parseNumber(length, state.profile.length),
                        weight = parseNumber(weight, state.profile.weight),
                        axleLoad = parseNumber(axleLoad, state.profile.axleLoad),
                        axles = axles.toIntOrNull() ?: state.profile.axles,
                        hazmat = hazmat,
                        avoidTolls = avoidTolls,
                        avoidHighways = avoidHighways,
                    )
                    onSave(profile, routingUrl, carFallback, voice, cameraWarnings)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Speichern", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Testversion 0.2.0 · Firmeninterne Nutzung",
                style = MaterialTheme.typography.labelSmall,
                color = AppPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Karten- und Routendaten aus OpenStreetMap (© OpenStreetMap-Mitwirkende, ODbL). " +
                    "Angaben ohne Gewähr – Beschilderung, Verkehrsregeln und tatsächliche Durchfahrtshöhen " +
                    "haben immer Vorrang. Blitzerdaten sind unvollständig und ohne Gewähr.",
                style = MaterialTheme.typography.labelSmall,
                color = AppTextMuted,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = AppPrimary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = if (integer) {
                input.filter { it.isDigit() }.take(2)
            } else {
                input.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
            }
            onValueChange(filtered)
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
        ),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = AppText,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppTextMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun parseNumber(text: String, fallback: Double): Double =
    text.replace(',', '.').trim().toDoubleOrNull() ?: fallback

private fun formatNumber(value: Double): String {
    val formatted = String.format(Locale.GERMANY, "%.2f", value)
    return formatted.trimEnd('0').trimEnd(',')
}
