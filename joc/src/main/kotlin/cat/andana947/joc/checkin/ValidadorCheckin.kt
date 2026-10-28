package cat.andana947.joc.checkin

import cat.andana947.joc.config.ConfiguracioJoc
import cat.andana947.joc.geo.distanciaMetres
import cat.andana947.joc.model.Node

/** Una posició del GPS, tal com l'envia l'app (GDD 7.2). */
data class Posicio(
    val lat: Double,
    val lon: Double,
    /** Precisió horitzontal, en metres (radi del 68 %). */
    val precisioMetres: Double,
    /** Velocitat en el moment de la lectura, en km/h. */
    val velocitatKmh: Double,
    /** Instant de la lectura, en mil·lisegons des de l'època Unix. */
    val instantMs: Long,
)

sealed interface ResultatCheckin {
    data object Valid : ResultatCheckin

    /** El jugador és massa lluny: li falten `metres` metres. */
    data class MassaLluny(val metres: Int) : ResultatCheckin

    data object PrecisioInsuficient : ResultatCheckin

    data object EnMoviment : ResultatCheckin

    /** La distància des del check-in anterior no es pot recórrer en aquest temps. */
    data object VelocitatImpossible : ResultatCheckin

    data object JaFetAvui : ResultatCheckin
}

/**
 * Regles de validació del check-in (GDD 7.3). El servidor les torna a aplicar sempre; a l'app només serveixen
 * per avisar el jugador abans d'enviar la petició.
 */
class ValidadorCheckin(config: ConfiguracioJoc) {
    private val c = config.checkin

    fun radi(node: Node): Double = if (node.mode.esEstacio) c.radiEstacioMetres else c.radiParadaMetres

    fun valida(
        node: Node,
        posicio: Posicio,
        anterior: Posicio? = null,
        accionsAvuiEnAquestNode: Int = 0,
    ): ResultatCheckin {
        if (accionsAvuiEnAquestNode >= c.accionsPerNodeIDia) return ResultatCheckin.JaFetAvui
        if (posicio.precisioMetres > c.precisioMaximaMetres) return ResultatCheckin.PrecisioInsuficient
        if (posicio.velocitatKmh > c.velocitatMaximaKmh) return ResultatCheckin.EnMoviment
        if (anterior != null) {
            val hores = (posicio.instantMs - anterior.instantMs) / 3_600_000.0
            val km = distanciaMetres(anterior.lat, anterior.lon, posicio.lat, posicio.lon) / 1000.0
            if (hores <= 0.0 && km > 0.0) return ResultatCheckin.VelocitatImpossible
            if (hores > 0.0 && km / hores > c.velocitatEntreCheckinsKmh) return ResultatCheckin.VelocitatImpossible
        }
        val distancia = distanciaMetres(posicio.lat, posicio.lon, node.lat, node.lon)
        val sobrant = distancia - posicio.precisioMetres - radi(node)
        return if (sobrant <= 0.0) ResultatCheckin.Valid else ResultatCheckin.MassaLluny(kotlin.math.ceil(sobrant).toInt())
    }
}
