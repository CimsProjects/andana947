package cat.andana947.data.vehicles

import android.content.Context
import android.util.Log
import cat.andana947.BuildConfig
import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.HorarisTrens
import cat.andana947.joc.circulacio.VehicleReal
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Els horaris i els traçats dels trens (docs/vehicles.md), que l'app porta a dins. */
interface FontHoraris {
    suspend fun circulacions(): Circulacions
}

/** `circulacions.json`, generat per `scripts/genera_circulacions.py` i copiat als assets en compilar. */
@Singleton
class FontHorarisAssets @Inject constructor(@ApplicationContext private val context: Context) : FontHoraris {
    private val mutex = Mutex()
    private var circulacions: Circulacions? = null

    override suspend fun circulacions(): Circulacions = mutex.withLock {
        circulacions ?: withContext(Dispatchers.IO) {
            val text = context.assets.open(FITXER).bufferedReader().use { it.readText() }
            Circulacions(HorarisTrens.llegeix(text))
        }.also { circulacions = it }
    }

    private companion object {
        const val FITXER = "circulacions.json"
    }
}

/** La resposta de la funció `vehicles` de Supabase (`supabase/functions/vehicles`). */
@Serializable
data class RespostaVehicles(
    val generat: Long,
    /** Per a cada font, si ha respost bé. */
    val fonts: Map<String, Boolean>,
    val vehicles: List<VehicleReal>,
) {
    val fontsOk: Set<String> get() = fonts.filterValues { it }.keys

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun llegeix(text: String): RespostaVehicles = json.decodeFromString(serializer(), text)
    }
}

/** Les posicions reals dels trens. Null si no s'han pogut obtenir: aleshores tot va segons l'horari. */
interface ServeiVehiclesReals {
    suspend fun consulta(): RespostaVehicles?
}

@Singleton
class ServeiVehiclesRealsSupabase @Inject constructor() : ServeiVehiclesReals {
    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = TEMPS_MAXIM_MS }
        }
    }

    override suspend fun consulta(): RespostaVehicles? {
        // Sense servidor (app/LLEGEIX-ME.md), no hi ha posicions reals.
        if (BuildConfig.SENSE_SERVIDOR) return null
        return try {
            val resposta = client.get("${BuildConfig.SUPABASE_URL}/functions/v1/vehicles") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            }
            if (!resposta.status.isSuccess()) return null
            RespostaVehicles.llegeix(resposta.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Vehicles", "No s'han pogut llegir les posicions reals", e)
            null
        }
    }

    private companion object {
        const val TEMPS_MAXIM_MS = 10_000L
    }
}
