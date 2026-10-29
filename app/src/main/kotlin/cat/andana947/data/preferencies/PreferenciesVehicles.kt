package cat.andana947.data.preferencies

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import cat.andana947.joc.circulacio.TipusVehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Quins vehicles vol veure el jugador al mapa (els trens, els busos, tots dos o cap). Per defecte, tots. */
interface PreferenciesVehicles {
    fun visibles(): Flow<Set<TipusVehicle>>

    suspend fun desaVisibles(visibles: Set<TipusVehicle>)
}

@Singleton
class PreferenciesVehiclesDataStore @Inject constructor(private val dataStore: DataStore<Preferences>) : PreferenciesVehicles {
    override fun visibles(): Flow<Set<TipusVehicle>> = dataStore.data.map { p ->
        TipusVehicle.entries.filterTo(mutableSetOf()) { p[clau(it)] ?: true }
    }

    override suspend fun desaVisibles(visibles: Set<TipusVehicle>) {
        dataStore.edit { p -> TipusVehicle.entries.forEach { p[clau(it)] = it in visibles } }
    }

    private fun clau(tipus: TipusVehicle) = booleanPreferencesKey("mapa_mostra_${tipus.name.lowercase()}")
}
