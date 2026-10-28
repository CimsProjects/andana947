package cat.andana947.joc.simulacio

import cat.andana947.joc.config.ConfiguracioJoc
import cat.andana947.joc.economia.Concessio
import cat.andana947.joc.economia.Concessions
import cat.andana947.joc.economia.Economia
import cat.andana947.joc.economia.Intermodalitat
import cat.andana947.joc.economia.ResultatRenovacio
import cat.andana947.joc.economia.TipusDia
import cat.andana947.joc.model.Categoria
import cat.andana947.joc.model.Node
import cat.andana947.joc.progressio.Prestigi
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Un dia de la rutina del jugador: on fa check-in. */
@Serializable
data class Visita(
    val node: String,
    /** Dies de la setmana (1 = dilluns, 7 = diumenge). */
    val diesSetmana: List<Int> = emptyList(),
    /** Dies concrets de la simulació (1 = el primer dia). */
    val diesSimulacio: List<Int> = emptyList(),
) {
    fun toca(dia: Int, data: LocalDate): Boolean = dia in diesSimulacio || data.dayOfWeek.value in diesSetmana
}

/** Un jugador, la seva rutina i els nodes que hi surten, amb l'afluència real o estimada. */
@Serializable
data class Escenari(
    val nom: String,
    val descripcio: String = "",
    /** Data del primer dia, AAAA-MM-DD. */
    val inici: String,
    val dies: Int,
    val nodes: List<Node>,
    /** En l'ordre en què el jugador hi passa cada dia. */
    val visites: List<Visita>,
    /** Dies festius dins del període (AAAA-MM-DD), que compten com a no feiners. */
    val festius: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun llegeix(text: String): Escenari = json.decodeFromString(serializer(), text)
    }
}

/** El resultat d'un dia: el que ha fet el jugador i la liquidació del tic de les 04:00. */
data class DiaSimulat(
    val dia: Int,
    val data: LocalDate,
    val ingres: Double,
    val ingresConcessions: Double,
    val dividends: Double,
    val inversio: Double,
    val saldo: Double,
    val concessionsActives: Int,
    val accions: Int,
    val prestigi: Int,
    val nivell: Int,
    val fets: List<String>,
)

data class ResultatSimulacio(
    val escenari: Escenari,
    val dies: List<DiaSimulat>,
    /** Accions finals per node. */
    val accions: Map<String, Int>,
    val concessions: Map<String, Concessio>,
    val renovacions: Map<String, Int>,
    /** Nodes que han tingut bonificació d'intermodalitat algun dia, amb el multiplicador màxim. */
    val intermodalitat: Map<String, Double>,
) {
    val ingressos: Double get() = dies.sumOf { it.ingres }
    val inversio: Double get() = dies.sumOf { it.inversio }
    val ingressosConcessions: Double get() = dies.sumOf { it.ingresConcessions }
    val dividends: Double get() = dies.sumOf { it.dividends }
}

/**
 * Simula un jugador sol (GDD, secció 8): fa check-in on diu la rutina i, cada dia, el tic liquida ingressos i
 * dividends. Hipòtesis: no hi ha caos ni altres accionistes que li esgotin les accions; compra tot el que pot.
 * Els imports no s'arrodoneixen (el servidor sí que ho farà, a cada apunt).
 */
class Simulacio(private val config: ConfiguracioJoc) {
    private val economia = Economia(config)
    private val concessionsRegles = Concessions(config)
    private val intermodalitatRegles = Intermodalitat(config)
    private val prestigiRegles = Prestigi(config)
    private val p = config.prestigi
    private val acc = config.accionariat

    fun executa(escenari: Escenari): ResultatSimulacio {
        val nodes = escenari.nodes.associateBy { it.id }
        for (v in escenari.visites) require(v.node in nodes) { "La visita ${v.node} no és a la llista de nodes" }
        val inici = LocalDate.parse(escenari.inici)
        val festius = escenari.festius.map(LocalDate::parse).toSet()

        var saldo = config.economia.capitalInicial
        var prestigi = 0
        val concessions = mutableMapOf<String, Concessio>()
        val accions = mutableMapOf<String, Int>()
        val darreraVisita = mutableMapOf<String, Int>()
        val renovacions = mutableMapOf<String, Int>()
        val visitats = mutableSetOf<String>()
        val municipis = mutableSetOf<String>()
        val comarques = mutableSetOf<String>()
        val bonificats = mutableMapOf<String, Double>()
        val dies = mutableListOf<DiaSimulat>()

        for (dia in 1..escenari.dies) {
            val data = inici.plusDays((dia - 1).toLong())
            val fets = mutableListOf<String>()
            var inversio = 0.0
            for (visita in escenari.visites.filter { it.toca(dia, data) }) {
                val node = nodes.getValue(visita.node)
                darreraVisita[node.id] = dia
                if (visitats.add(node.id)) prestigi += p.primerCheckinNode
                node.municipi?.let { if (municipis.add(it)) prestigi += p.municipiNou }
                node.comarca?.let { if (comarques.add(it)) prestigi += p.comarcaNova }
                when (economia.categoria(node)) {
                    Categoria.GRAN_ESTACIO -> {
                        val preu = economia.preuAccio(node.viatgersFeiner)
                        val tinc = accions[node.id] ?: 0
                        val quantes = minOf(acc.maximPerCheckin, acc.maximPerJugador - tinc, (saldo / preu).toInt())
                        if (quantes > 0) {
                            saldo -= quantes * preu
                            inversio += quantes * preu
                            accions[node.id] = tinc + quantes
                            prestigi += quantes * p.perAccio
                            fets += "+$quantes acc. ${node.nom}"
                        }
                    }
                    Categoria.PARADA -> {
                        val actual = concessions[node.id]
                        if (actual != null) {
                            val (nova, resultat) = concessionsRegles.checkin(actual, dia)
                            if (resultat == ResultatRenovacio.REACTIVADA && actives(concessions, dia) >= maxim(prestigi)) {
                                continue
                            }
                            concessions[node.id] = nova
                            if (resultat != ResultatRenovacio.VIGENT) {
                                renovacions.merge(node.id, 1, Int::plus)
                                prestigi += p.renovacio
                                fets += (if (resultat == ResultatRenovacio.RENOVADA) "renova " else "reactiva ") + node.nom
                            }
                        } else {
                            val preu = economia.preuConcessio(node.viatgersFeiner)
                            if (saldo >= preu && actives(concessions, dia) < maxim(prestigi)) {
                                saldo -= preu
                                inversio += preu
                                concessions[node.id] = concessionsRegles.nova(node.id, dia)
                                prestigi += p.conquesta
                                fets += "conquereix ${node.nom}"
                            }
                        }
                    }
                }
            }

            // Tic econòmic de les 04:00: liquida el dia.
            val tipus = if (data.dayOfWeek in CAP_DE_SETMANA || data in festius) TipusDia.NO_FEINER else TipusDia.FEINER
            for ((id, c) in concessions) if (c.finsA == dia - 1) fets += "caduca ${nodes.getValue(id).nom}"
            val propis = concessions.values.filter { it.activa(dia) }.map { nodes.getValue(it.nodeId) } +
                accions.filter { intermodalitatRegles.comptaAccions(it.value) }.keys.map(nodes::getValue)
            val multiplicadors = intermodalitatRegles.multiplicadors(propis)
            multiplicadors.forEach { (id, m) -> bonificats.merge(id, m, ::maxOf) }
            var ingresConcessions = 0.0
            for (c in concessions.values.filter { it.activa(dia) }) {
                val node = nodes.getValue(c.nodeId)
                val viatgers = economia.viatgersDelDia(node.viatgersFeiner, tipus)
                ingresConcessions += economia.ingresConcessio(node, viatgers, multiplicadors[node.id] ?: 1.0)
            }
            var dividends = 0.0
            for ((id, n) in accions) {
                if (dia - (darreraVisita[id] ?: dia) > acc.diesSenseVisitaSenseDividend) continue
                val node = nodes.getValue(id)
                val viatgers = economia.viatgersDelDia(node.viatgersFeiner, tipus)
                dividends += economia.dividend(viatgers, n) * (multiplicadors[id] ?: 1.0)
            }
            saldo += ingresConcessions + dividends
            dies += DiaSimulat(
                dia = dia,
                data = data,
                ingres = ingresConcessions + dividends,
                ingresConcessions = ingresConcessions,
                dividends = dividends,
                inversio = inversio,
                saldo = saldo,
                concessionsActives = actives(concessions, dia),
                accions = accions.values.sum(),
                prestigi = prestigi,
                nivell = prestigiRegles.nivell(prestigi),
                fets = fets,
            )
        }
        return ResultatSimulacio(escenari, dies, accions, concessions, renovacions, bonificats)
    }

    private fun actives(concessions: Map<String, Concessio>, dia: Int) = concessions.values.count { it.activa(dia) }

    private fun maxim(prestigi: Int) = prestigiRegles.maximConcessions(prestigiRegles.nivell(prestigi))

    companion object {
        private val CAP_DE_SETMANA = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
