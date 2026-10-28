package cat.andana947.data.preferencies

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** On mirava el mapa. */
data class Camera(val latitud: Double, val longitud: Double, val zoom: Double) {
    companion object {
        /**
         * La primera vegada, tot Catalunya, centrada a Pinós (Solsonès), que es considera el centre geogràfic de
         * Catalunya (41° 49′ 43″ N, 1° 32′ 35″ E, segons la Viquipèdia). Quan arriba la posició del jugador, el mapa hi va.
         */
        val CATALUNYA = Camera(latitud = 41.8286, longitud = 1.5431, zoom = 6.8)
    }
}

/** El que l'app recorda del mapa entre sessions, perquè s'obri on es va deixar. */
interface PreferenciesMapa {
    suspend fun darreraCamera(): Camera?

    suspend fun desaCamera(camera: Camera)
}

@Singleton
class PreferenciesMapaDataStore @Inject constructor(private val dataStore: DataStore<Preferences>) : PreferenciesMapa {
    override suspend fun darreraCamera(): Camera? {
        val p = dataStore.data.first()
        val latitud = p[LATITUD] ?: return null
        val longitud = p[LONGITUD] ?: return null
        val zoom = p[ZOOM] ?: return null
        return Camera(latitud, longitud, zoom)
    }

    override suspend fun desaCamera(camera: Camera) {
        dataStore.edit {
            it[LATITUD] = camera.latitud
            it[LONGITUD] = camera.longitud
            it[ZOOM] = camera.zoom
        }
    }

    private companion object {
        val LATITUD = doublePreferencesKey("mapa_latitud")
        val LONGITUD = doublePreferencesKey("mapa_longitud")
        val ZOOM = doublePreferencesKey("mapa_zoom")
    }
}
