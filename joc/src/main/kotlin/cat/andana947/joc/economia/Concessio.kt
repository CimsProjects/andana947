package cat.andana947.joc.economia

import cat.andana947.joc.config.ConfiguracioJoc

/**
 * Vigència d'una concessió (GDD 4.2). Els dies es compten com a enters (per exemple, dies des d'una data
 * d'origen); `finsA` és l'últim dia que la concessió dona ingressos.
 */
data class Concessio(val nodeId: String, val finsA: Int) {
    fun activa(dia: Int): Boolean = dia <= finsA
}

/** Què passa quan el jugador fa check-in en un node seu que ja és concessió. */
enum class ResultatRenovacio {
    /** Encara li queda més de la finestra de renovació: no cal fer res. */
    VIGENT,

    /** Durant la segona setmana: es renova la durada sencera, sense cost. */
    RENOVADA,

    /** Havia caducat: es reactiva sense pagar-la de nou. */
    REACTIVADA,
}

class Concessions(config: ConfiguracioJoc) {
    private val c = config.concessions

    /** Una concessió nova, comprada el dia `dia`. */
    fun nova(nodeId: String, dia: Int): Concessio = Concessio(nodeId, dia + c.duradaDies - 1)

    /** Check-in el dia `dia` en una concessió que ja és del jugador. */
    fun checkin(concessio: Concessio, dia: Int): Pair<Concessio, ResultatRenovacio> = when {
        !concessio.activa(dia) -> nova(concessio.nodeId, dia) to ResultatRenovacio.REACTIVADA
        concessio.finsA - dia < c.finestraRenovacioDies -> nova(concessio.nodeId, dia) to ResultatRenovacio.RENOVADA
        else -> concessio to ResultatRenovacio.VIGENT
    }
}
