package app.lawnchair.omni.experience

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.experienceDataStore by preferencesDataStore("omni_experience")

/** Created on demand. No startup inventory, polling service, or main-thread file reads. */
class ExperienceStore private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val key = stringPreferencesKey("state_v1")
    private fun decode(content: String?): ExperienceState = try {
        content?.let { json.decodeFromString<ExperienceState>(it) } ?: ExperienceState()
    } catch (error: kotlinx.serialization.SerializationException) {
        android.util.Log.e("OmniExperience", "Stored experience data is invalid", error)
        ExperienceState()
    }
    val state = context.experienceDataStore.data.map { prefs -> decode(prefs[key]) }
        .catch { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.e("OmniExperience", "Experience storage unavailable", error)
            emit(ExperienceState())
        }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun read(): ExperienceState {
        val prefs = context.experienceDataStore.data.first()
        return decode(prefs[key])
    }

    suspend fun update(transform: (ExperienceState) -> ExperienceState) {
        context.experienceDataStore.edit { prefs ->
            val old = decode(prefs[key])
            val next = transform(old)
            require(next.aliases.size <= 1000 && next.aliases.values.all { it.length <= 200 })
            require(next.cards.size <= 40 && next.cards.all { it.title.length <= 160 && it.text.length <= 4000 })
            require(next.rules.size <= 100 && next.scenes.size <= 20 && next.stacks.size <= 20)
            require(next.scenes.all { it.startMinute in 0..1439 && it.endMinute in 0..1439 && it.apps.size <= 12 })
            require(next.stacks.all { it.widgetIds.size <= 8 && it.widgetIds.distinct().size == it.widgetIds.size })
            prefs[key] = json.encodeToString(next)
        }
    }

    suspend fun removeCard(id: String) {
        val removed = read().cards.firstOrNull { it.id == id }
        update { state -> state.copy(cards = state.cards.filterNot { it.id == id }) }
        val uri = removed?.uri
        if (uri != null && read().cards.none { it.uri == uri }) {
            runCatching { context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    suspend fun putCard(card: ShelfCard) = update { state ->
        state.copy(cards = (listOf(card) + state.cards.filterNot { it.id == card.id }).take(40))
    }

    companion object {
        @Volatile private var instance: ExperienceStore? = null
        fun get(context: Context): ExperienceStore = instance ?: synchronized(this) {
            instance ?: ExperienceStore(context.applicationContext).also { instance = it }
        }
        fun newId(): String = UUID.randomUUID().toString()
    }
}
