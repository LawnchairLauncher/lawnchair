package app.lawnchair.widget.worldclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class WorldClockConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Backing out of the first configuration must not leave an unconfigured widget behind
        setResult(Activity.RESULT_CANCELED, resultIntent())

        val store = WorldClockStore(this)
        val initial = store.load(widgetId)
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                WorldClockConfigScreen(
                    initial = initial,
                    onSave = { config ->
                        store.save(widgetId, config)
                        WorldClockWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId)
                        setResult(Activity.RESULT_OK, resultIntent())
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val places: List<Place>) : SearchState
    data class Failed(val message: String) : SearchState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldClockConfigScreen(
    initial: WorldClockConfig,
    onSave: (WorldClockConfig) -> Unit,
    onCancel: () -> Unit,
) {
    val cities = remember { mutableStateListOf<City>().apply { addAll(initial.cities) } }
    var use24h by remember { mutableStateOf(initial.use24h) }
    var locked by remember { mutableStateOf(initial.locked) }
    var query by remember { mutableStateOf("") }
    var search by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var renaming by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            search = SearchState.Idle
            return@LaunchedEffect
        }
        delay(400)
        search = SearchState.Loading
        search = try {
            SearchState.Done(CitySearch.search(q))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SearchState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
    // Offline fallback and direct zone search, e.g. "Kolkata" or "UTC"
    val zoneMatches = remember(query) {
        val q = query.trim()
        if (q.length < 2) {
            emptyList()
        } else {
            WorldClockStore.zones.filter {
                WorldClockStore.labelFor(it).contains(q, ignoreCase = true) || it.contains(q, ignoreCase = true)
            }.take(30)
        }
    }

    fun add(city: City) {
        if (cities.none { it.zone == city.zone && it.label == city.label }) cities.add(city)
        query = ""
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.world_clock_widget_label)) }) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
                Button(
                    enabled = cities.isNotEmpty(),
                    onClick = { onSave(WorldClockConfig(cities.toList(), use24h, locked)) },
                ) { Text(stringResource(R.string.world_clock_save)) }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                SwitchRow(stringResource(R.string.world_clock_24h), null, use24h) { use24h = it }
                SwitchRow(
                    stringResource(R.string.world_clock_lock),
                    stringResource(R.string.world_clock_lock_description),
                    locked,
                ) { locked = it }
                HorizontalDivider()
                SectionTitle(stringResource(R.string.world_clock_cities))
            }
            itemsIndexed(cities) { index, city ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(city.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(city.region, "${zoneAbbreviation(city.zone)} · ${WorldClockStore.offsetLabel(city.zone)}")
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { renaming = index }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.world_clock_rename))
                    }
                    IconButton(enabled = index > 0, onClick = { cities.add(index - 1, cities.removeAt(index)) }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.world_clock_move_up))
                    }
                    IconButton(onClick = { cities.removeAt(index) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.world_clock_remove))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.world_clock_add_city)) },
                    supportingText = { Text(stringResource(R.string.world_clock_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                when (val state = search) {
                    SearchState.Loading -> Row(Modifier.padding(16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    is SearchState.Failed -> HintText(stringResource(R.string.world_clock_search_failed, state.message))
                    is SearchState.Done -> if (state.places.isEmpty() && zoneMatches.isEmpty()) {
                        HintText(stringResource(R.string.world_clock_no_results))
                    }
                    SearchState.Idle -> Unit
                }
            }
            val places = (search as? SearchState.Done)?.places.orEmpty()
            if (places.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.world_clock_places)) }
                items(places) { place ->
                    ResultRow(
                        title = place.name,
                        subtitle = listOf(place.region, place.zone, zoneAbbreviation(place.zone))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = { add(City(place.zone, place.name, place.region)) },
                    )
                }
                item { HintText(stringResource(R.string.world_clock_attribution)) }
            }
            if (zoneMatches.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.world_clock_time_zones)) }
                items(zoneMatches, key = { "zone:$it" }) { zone ->
                    ResultRow(
                        title = WorldClockStore.labelFor(zone),
                        subtitle = "$zone · ${zoneAbbreviation(zone)} · ${WorldClockStore.offsetLabel(zone)}",
                        onClick = { add(City(zone, WorldClockStore.labelFor(zone))) },
                    )
                }
            }
            item { Spacer(Modifier.padding(8.dp)) }
        }
    }

    renaming?.let { index ->
        val city = cities.getOrNull(index) ?: return@let
        var label by remember(index) { mutableStateOf(city.label) }
        var region by remember(index) { mutableStateOf(city.region) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.world_clock_rename)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true)
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.world_clock_region)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cities[index] = city.copy(
                        label = label.trim().ifBlank { WorldClockStore.labelFor(city.zone) },
                        region = region.trim(),
                    )
                    renaming = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
