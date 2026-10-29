package cat.andana947.data.joc

import cat.andana947.joc.geo.distanciaMetres
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.math.max

/**
 * Implementació local en memòria per provar l'app sense credencials de Supabase (app/LLEGEIX-ME.md)
 * o en proves unitàries.
 */
class ServeiJocLocal : ServeiJoc {
    private val mutex = Mutex()

    private var saldo = 200.00
    private var prestigi = 0
    private var nivell = 1
    private val concessions = mutableMapOf<String, ConcessioActiva>()
    private val accions = mutableMapOf<String, AccioEstacio>()
    private val moviments = mutableListOf(
        Moviment(1, "Capital inicial d'operador", 200.00, null, "Ara"),
    )

    // Nodes de prova a Catalunya (coordenades reals)
    private val nodesDeProva = listOf(
        NodeAProp(
            id = "tmb:metro:catalunya",
            nom = "Catalunya",
            mode = "metro",
            operador = "TMB",
            lat = 41.3870,
            lon = 2.1700,
            linies = listOf("L1", "L3"),
            viatgersFeiner = 55000.0,
            fontAfluencia = "real",
            distanciaMetres = 0.0,
            categoria = "gran_estacio",
            preu = 12.50,
            ingresDiari = 125.0,
            accionsDisponibles = 100,
        ),
        NodeAProp(
            id = "renfe:rodalies:placa-catalunya",
            nom = "Plaça de Catalunya",
            mode = "rodalies",
            operador = "Renfe",
            lat = 41.3865,
            lon = 2.1695,
            linies = listOf("R1", "R3", "R4"),
            viatgersFeiner = 42000.0,
            fontAfluencia = "real",
            distanciaMetres = 0.0,
            categoria = "gran_estacio",
            preu = 10.20,
            ingresDiari = 102.0,
            accionsDisponibles = 100,
        ),
        NodeAProp(
            id = "tmb:metro:baro-viver",
            nom = "Baró de Viver",
            mode = "metro",
            operador = "TMB",
            lat = 41.4468,
            lon = 2.2014,
            linies = listOf("L1"),
            viatgersFeiner = 1650.0,
            fontAfluencia = "real",
            distanciaMetres = 0.0,
            categoria = "parada",
            preu = 175.00,
            ingresDiari = 17.50,
        ),
        NodeAProp(
            id = "tmb:bus:1234",
            nom = "Pg de Gràcia - Aragó",
            mode = "bus",
            operador = "TMB",
            lat = 41.3920,
            lon = 2.1650,
            linies = listOf("7", "22", "24", "V15"),
            viatgersFeiner = 850.0,
            fontAfluencia = "estimat",
            distanciaMetres = 0.0,
            categoria = "parada",
            preu = 118.00,
            ingresDiari = 11.80,
        ),
        NodeAProp(
            id = "tram:t4:glories",
            nom = "Glòries",
            mode = "tram",
            operador = "TRAM",
            lat = 41.4024,
            lon = 2.1880,
            linies = listOf("T4", "T5", "T6"),
            viatgersFeiner = 3500.0,
            fontAfluencia = "real",
            distanciaMetres = 0.0,
            categoria = "parada",
            preu = 270.00,
            ingresDiari = 27.00,
        ),
    )

    override suspend fun nodesAProp(
        lat: Double,
        lon: Double,
        radiMetres: Double,
        maxim: Int,
    ): Result<List<NodeAProp>> = mutex.withLock {
        runCatching {
            nodesDeProva.map { node ->
                val d = distanciaMetres(lat, lon, node.lat, node.lon)
                val conc = concessions[node.id]
                val acc = accions[node.id]
                node.copy(
                    distanciaMetres = d,
                    teConcessio = conc != null && conc.diesRestants > 0,
                    concessioCaducada = conc != null && conc.diesRestants <= 0,
                    diesRestants = conc?.diesRestants,
                    accionsJugador = acc?.quantitat ?: 0,
                    accionsDisponibles = max(0, 100 - (acc?.quantitat ?: 0)),
                )
            }.sortedBy { it.distanciaMetres }.take(maxim)
        }
    }

    override suspend fun estatImperi(): Result<EstatImperi> = mutex.withLock {
        runCatching {
            val actives = concessions.values.filter { it.diesRestants > 0 }
            val caducades = concessions.values.filter { it.diesRestants <= 0 }.map {
                ConcessioCaducada(it.nodeId, it.nom, it.mode, it.operador, it.viatgersFeiner, it.caducaEl)
            }
            val acc = accions.values.toList()
            val ingresConcessions = actives.sumOf { it.ingresDiari }
            val ingresAccions = acc.sumOf { it.dividendDiari }

            EstatImperi(
                saldo = saldo,
                prestigi = prestigi,
                nivell = nivell,
                maximConcessions = 10 + 2 * nivell,
                concessionsActives = actives,
                concessionsCaducades = caducades,
                accions = acc,
                moviments = moviments.reversed(),
                ingresEstimatDia = ingresConcessions + ingresAccions,
            )
        }
    }

    override suspend fun ferCheckin(
        nodeId: String,
        lat: Double,
        lon: Double,
        precisio: Double,
        velocitat: Double,
    ): Result<ResultatCheckinRpc> = mutex.withLock {
        runCatching {
            val node = nodesDeProva.find { it.id == nodeId }
                ?: return@runCatching ResultatCheckinRpc("error", "Node no trobat.")

            if (precisio > 50.0) {
                return@runCatching ResultatCheckinRpc("precisio_insuficient", "Precisió del GPS insuficient (> 50 m).")
            }
            if (velocitat > 10.0) {
                return@runCatching ResultatCheckinRpc("en_moviment", "Vas massa ràpid (> 10 km/h).")
            }

            val d = distanciaMetres(lat, lon, node.lat, node.lon)
            val radi = if (node.mode in listOf("metro", "rodalies", "regional", "fgc")) 150.0 else 60.0
            val sobrant = d - precisio - radi
            if (sobrant > 0) {
                val metres = ceil(sobrant).toInt()
                return@runCatching ResultatCheckinRpc(
                    status = "massa_lluny",
                    missatge = "Ets massa lluny: et falten $metres m.",
                    metresFalten = metres,
                )
            }

            if (node.categoria == "gran_estacio") {
                val actuals = accions[node.id]?.quantitat ?: 0
                if (actuals >= 20) {
                    return@runCatching ResultatCheckinRpc(
                        status = "valid",
                        tipusAccio = "visita_estacio",
                        missatge = "Visita registrada a ${node.nom}!",
                        saldo = saldo,
                        prestigi = prestigi,
                        nivell = nivell,
                    )
                }
                val comprar = minOf(5, 20 - actuals)
                val cost = comprar * node.preu
                if (saldo < cost) {
                    return@runCatching ResultatCheckinRpc("sense_saldo", "No tens prou catalins ($cost € necessaris).")
                }
                saldo -= cost
                prestigi += comprar * 2
                val novaAccio = AccioEstacio(
                    nodeId = node.id,
                    nom = node.nom,
                    mode = node.mode,
                    operador = node.operador,
                    quantitat = actuals + comprar,
                    dividendDiari = (node.ingresDiari / 100.0) * (actuals + comprar),
                    darreraVisitaEl = "Avui",
                )
                accions[node.id] = novaAccio
                moviments.add(Moviment(moviments.size + 1L, "Compra de $comprar accions a ${node.nom}", -cost, node.id, "Ara"))

                ResultatCheckinRpc(
                    status = "valid",
                    tipusAccio = "accions",
                    missatge = "+$comprar accions a ${node.nom}!",
                    saldo = saldo,
                    prestigi = prestigi,
                    nivell = nivell,
                    accionsComprades = comprar,
                    accionsTotals = actuals + comprar,
                )
            } else {
                val existent = concessions[node.id]
                if (existent != null) {
                    // Renovar
                    concessions[node.id] = existent.copy(diesRestants = 14)
                    prestigi += 5
                    moviments.add(Moviment(moviments.size + 1L, "Renovació de concessió: ${node.nom}", 0.0, node.id, "Ara"))
                    ResultatCheckinRpc(
                        status = "valid",
                        tipusAccio = "renovacio",
                        missatge = "Renovada! Concessió de ${node.nom} vigent 14 dies més.",
                        saldo = saldo,
                        prestigi = prestigi,
                        nivell = nivell,
                        diesRestants = 14,
                    )
                } else {
                    val maxConcessions = 10 + 2 * nivell
                    if (concessions.size >= maxConcessions) {
                        return@runCatching ResultatCheckinRpc("limit_llicencia", "Límit de llicència assolit ($maxConcessions).")
                    }
                    if (saldo < node.preu) {
                        return@runCatching ResultatCheckinRpc("sense_saldo", "No tens prou catalins (${node.preu} € necessaris).")
                    }
                    saldo -= node.preu
                    prestigi += 30 + 20 // Conquesta + primer checkin
                    val nova = ConcessioActiva(
                        nodeId = node.id,
                        nom = node.nom,
                        mode = node.mode,
                        operador = node.operador,
                        viatgersFeiner = node.viatgersFeiner,
                        ingresDiari = node.ingresDiari,
                        caducaEl = "D'aquí a 14 dies",
                        diesRestants = 14,
                    )
                    concessions[node.id] = nova
                    moviments.add(Moviment(moviments.size + 1L, "Concessió: ${node.nom}", -node.preu, node.id, "Ara"))

                    ResultatCheckinRpc(
                        status = "valid",
                        tipusAccio = "conquesta",
                        missatge = "Teva! Concessió de ${node.nom} adquirida per 14 dies.",
                        saldo = saldo,
                        prestigi = prestigi,
                        nivell = nivell,
                        cost = node.preu,
                        diesRestants = 14,
                    )
                }
            }
        }
    }
}
