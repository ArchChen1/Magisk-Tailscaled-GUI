package top.cenmin.tailcontrol.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import top.cenmin.tailcontrol.core.model.NetcheckReport
import top.cenmin.tailcontrol.core.model.TailscaleJson
import javax.inject.Inject
import javax.inject.Singleton

private val Context.netcheckStore by preferencesDataStore(name = "netcheck_history")
private val NETCHECK_KEY = stringPreferencesKey("history_json")
private const val MAX_HISTORY = 10

@Singleton
class NetcheckHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store: DataStore<Preferences> = context.netcheckStore
    private val serializer = ListSerializer(NetcheckReport.serializer())

    val history: Flow<List<NetcheckReport>> = store.data.map { p ->
        val raw = p[NETCHECK_KEY] ?: return@map emptyList()
        runCatching { TailscaleJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    suspend fun add(report: NetcheckReport) {
        store.edit { p ->
            val existing = p[NETCHECK_KEY]?.let {
                runCatching { TailscaleJson.decodeFromString(serializer, it) }.getOrNull()
            } ?: emptyList()
            val merged = (listOf(report) + existing).take(MAX_HISTORY)
            p[NETCHECK_KEY] = TailscaleJson.encodeToString(serializer, merged)
        }
    }

    suspend fun clear() {
        store.edit { it.remove(NETCHECK_KEY) }
    }
}
