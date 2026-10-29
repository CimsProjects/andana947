package cat.andana947.data.joc

import android.content.Context
import android.util.Log
import cat.andana947.joc.trams.CatalegTrams
import cat.andana947.joc.trams.Mida
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

// Els trams i les monedes (GDD 4.0). L'economia és del servidor: l'app només demana l'estat i diu quin tram vol
// comprar; el saldo i el preu que manen són els que torna Supabase (supabase/migrations/…_trams_i_monedes.sql).

/** La partida del jugador a la temporada actual: les monedes (catalins) i els trams que té. */
@Serializable
data class EstatPartida(
    val temporada: Int,
    val saldo: Long,
    /** Les monedes que ha guanyat des de l'última vegada, amb les arribades a andana. */
    val guanyat: Long = 0,
    val trams: List<String> = emptyList(),
)

/** El que respon el servidor a una compra. */
enum class ResultatCompra(val clau: String) {
    COMPRAT("comprat"),
    NO_EXISTEIX("no_existeix"),
    GRAN("gran"),
    JA_ES_TEU("ja_es_teu"),
    SENSE_MONEDES("sense_monedes"),
    SENSE_CONNEXIO("sense_connexio"),
    ;

    companion object {
        fun de(clau: String): ResultatCompra = entries.firstOrNull { it.clau == clau } ?: SENSE_CONNEXIO
    }
}

@Serializable
data class RespostaCompra(val resultat: String, val saldo: Long? = null)

interface ServeiJoc {
    /** L'estat de la partida (la comença si cal i cobra les arribades pendents). Null sense connexió. */
    suspend fun estat(): EstatPartida?

    suspend fun compra(tramId: String): RespostaCompra
}

/** Les funcions `estat_partida` i `compra_tram` de Supabase. */
@Singleton
class ServeiJocSupabase @Inject constructor(private val supabase: SupabaseClient) : ServeiJoc {
    override suspend fun estat(): EstatPartida? = ambConnexio(null) {
        supabase.postgrest.rpc("estat_partida").decodeAs<EstatPartida>()
    }

    override suspend fun compra(tramId: String): RespostaCompra = ambConnexio(RespostaCompra(ResultatCompra.SENSE_CONNEXIO.clau)) {
        supabase.postgrest.rpc("compra_tram", buildJsonObject { put("tram_id", tramId) }).decodeAs<RespostaCompra>()
    }

    private inline fun <T> ambConnexio(perDefecte: T, bloc: () -> T): T = try {
        bloc()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Joc", "No s'ha pogut parlar amb el servidor", e)
        perDefecte
    }
}

/**
 * Sense servidor configurat (app/LLEGEIX-ME.md): una partida de prova al mòbil, que no es desa i no guanya monedes.
 * Només serveix per provar el mapa i les compres des de l'Android Studio.
 */
class ServeiJocLocal(private val cataleg: FontTrams) : ServeiJoc {
    private val mutex = Mutex()
    private var estat: EstatPartida? = null

    override suspend fun estat(): EstatPartida = mutex.withLock { actual() }

    override suspend fun compra(tramId: String): RespostaCompra = mutex.withLock {
        val partida = actual()
        val tram = cataleg.cataleg().tram(tramId)
        val preu = tram?.preu
        val resultat = when {
            tram == null -> ResultatCompra.NO_EXISTEIX
            tram.midaTram == Mida.GRAN || preu == null -> ResultatCompra.GRAN
            tramId in partida.trams -> ResultatCompra.JA_ES_TEU
            partida.saldo < preu -> ResultatCompra.SENSE_MONEDES
            else -> {
                estat = partida.copy(saldo = partida.saldo - preu, trams = partida.trams + tramId)
                ResultatCompra.COMPRAT
            }
        }
        RespostaCompra(resultat.clau, estat?.saldo)
    }

    private suspend fun actual(): EstatPartida =
        estat ?: EstatPartida(temporada = 1, saldo = cataleg.cataleg().capitalInicial.toLong()).also { estat = it }
}

/** `trams.json`, generat per `scripts/genera_trams.py` i copiat als assets en compilar. */
interface FontTrams {
    suspend fun cataleg(): CatalegTrams
}

@Singleton
class FontTramsAssets @Inject constructor(@ApplicationContext private val context: Context) : FontTrams {
    private val mutex = Mutex()
    private var cataleg: CatalegTrams? = null

    override suspend fun cataleg(): CatalegTrams = mutex.withLock {
        cataleg ?: withContext(Dispatchers.IO) {
            CatalegTrams.llegeix(context.assets.open(FITXER).bufferedReader().use { it.readText() })
        }.also { cataleg = it }
    }

    private companion object {
        const val FITXER = "trams.json"
    }
}
