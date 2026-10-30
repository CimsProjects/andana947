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
// comprar o millorar; el saldo, el preu i el cost que manen són els que torna Supabase
// (supabase/migrations/…_trams_i_monedes.sql).

/** Un tram del jugador: quantes andanes (parades) en té. */
@Serializable
data class TramJugador(val id: String, val andanes: Int)

/** La partida del jugador a la temporada actual: les monedes (catalins) i els trams que té. */
@Serializable
data class EstatPartida(
    val temporada: Int,
    val saldo: Long,
    /** Les monedes que ha guanyat des de l'última vegada, amb les arribades a andana. */
    val guanyat: Long = 0,
    val trams: List<TramJugador> = emptyList(),
) {
    fun andanes(tramId: String): Int? = trams.firstOrNull { it.id == tramId }?.andanes
}

/** El que respon el servidor a una compra o a una millora. */
enum class Resultat(val clau: String) {
    COMPRAT("comprat"),
    MILLORAT("millorat"),
    NO_EXISTEIX("no_existeix"),
    GRAN("gran"),
    JA_ES_TEU("ja_es_teu"),
    NO_ES_TEU("no_es_teu"),
    MAXIM("maxim"),
    SENSE_MONEDES("sense_monedes"),
    SENSE_CONNEXIO("sense_connexio"),
    ;

    companion object {
        fun de(clau: String): Resultat = entries.firstOrNull { it.clau == clau } ?: SENSE_CONNEXIO
    }
}

@Serializable
data class Resposta(val resultat: String, val saldo: Long? = null, val andanes: Int? = null)

interface ServeiJoc {
    /** L'estat de la partida (la comença si cal i cobra les arribades pendents). Null sense connexió. */
    suspend fun estat(): EstatPartida?

    /** Compra un tram, que ve amb una andana. */
    suspend fun compra(tramId: String): Resposta

    /** Afegeix una andana a un tram propi. */
    suspend fun millora(tramId: String): Resposta
}

/** Les funcions `estat_partida`, `compra_tram` i `millora_tram` de Supabase. */
@Singleton
class ServeiJocSupabase @Inject constructor(private val supabase: SupabaseClient) : ServeiJoc {
    override suspend fun estat(): EstatPartida? = ambConnexio(null) {
        supabase.postgrest.rpc("estat_partida").decodeAs<EstatPartida>()
    }

    override suspend fun compra(tramId: String): Resposta = crida("compra_tram", tramId)

    override suspend fun millora(tramId: String): Resposta = crida("millora_tram", tramId)

    private suspend fun crida(funcio: String, tramId: String): Resposta = ambConnexio(Resposta(Resultat.SENSE_CONNEXIO.clau)) {
        supabase.postgrest.rpc(funcio, buildJsonObject { put("tram_id", tramId) }).decodeAs<Resposta>()
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
 * Només serveix per provar les pantalles des de l'Android Studio. Les millores costen el que diu el catàleg.
 */
class ServeiJocLocal(private val cataleg: FontTrams) : ServeiJoc {
    private val mutex = Mutex()
    private var estat: EstatPartida? = null

    override suspend fun estat(): EstatPartida = mutex.withLock { actual() }

    override suspend fun compra(tramId: String): Resposta = mutex.withLock {
        val partida = actual()
        val tram = cataleg.cataleg().tram(tramId)
        val preu = tram?.preu
        val resultat = when {
            tram == null -> Resultat.NO_EXISTEIX
            tram.midaTram == Mida.GRAN || preu == null -> Resultat.GRAN
            partida.andanes(tramId) != null -> Resultat.JA_ES_TEU
            partida.saldo < preu -> Resultat.SENSE_MONEDES
            else -> {
                estat = partida.copy(saldo = partida.saldo - preu, trams = partida.trams + TramJugador(tramId, 1))
                Resultat.COMPRAT
            }
        }
        Resposta(resultat.clau, estat?.saldo, estat?.andanes(tramId))
    }

    override suspend fun millora(tramId: String): Resposta = mutex.withLock {
        val partida = actual()
        val c = cataleg.cataleg()
        val tram = c.tram(tramId)
        val andanes = partida.andanes(tramId)
        val cost = if (tram != null && andanes != null) c.costMillora(tram, andanes) else null
        val resultat = when {
            tram == null || andanes == null -> Resultat.NO_ES_TEU
            cost == null -> Resultat.MAXIM
            partida.saldo < cost -> Resultat.SENSE_MONEDES
            else -> {
                estat = partida.copy(
                    saldo = partida.saldo - cost,
                    trams = partida.trams.map { if (it.id == tramId) it.copy(andanes = it.andanes + 1) else it },
                )
                Resultat.MILLORAT
            }
        }
        Resposta(resultat.clau, estat?.saldo, estat?.andanes(tramId))
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
