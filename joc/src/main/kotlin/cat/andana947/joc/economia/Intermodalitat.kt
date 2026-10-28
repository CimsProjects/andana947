package cat.andana947.joc.economia

import cat.andana947.joc.config.ConfiguracioJoc
import cat.andana947.joc.geo.distanciaMetres
import cat.andana947.joc.model.Node

/**
 * Intermodalitat Perfecta (GDD 4.5). Una estació ferroviària pròpia (metro, Rodalies, regional o FGC; concessió
 * o prou accions) amb almenys una parada de bus o de TRAM pròpia a menys del radi: tots aquests nodes reben el
 * multiplicador. Cada mode diferent de més dins del radi hi suma `perModeAddicional`, fins al màxim.
 */
class Intermodalitat(config: ConfiguracioJoc) {
    private val im = config.intermodalitat

    /**
     * Multiplicador de cada node propi. `propis` són els nodes que compten com a del jugador aquell dia
     * (concessions actives i grans estacions amb prou accions). Els nodes sense bonificació no hi surten.
     */
    fun multiplicadors(propis: List<Node>): Map<String, Double> {
        val resultat = mutableMapOf<String, Double>()
        for (estacio in propis.filter { it.mode.esEstacio }) {
            val grup = propis.filter {
                it.id == estacio.id || distanciaMetres(estacio.lat, estacio.lon, it.lat, it.lon) <= im.radiMetres
            }
            if (grup.none { !it.mode.esEstacio }) continue
            val modes = grup.map { it.mode }.toSet()
            val multiplicador = minOf(im.maxim, im.multiplicador + im.perModeAddicional * (modes.size - 2))
            for (node in grup) {
                resultat[node.id] = maxOf(resultat[node.id] ?: 1.0, multiplicador)
            }
        }
        return resultat
    }

    /** Si una gran estació compta com a pròpia per a la intermodalitat. */
    fun comptaAccions(accions: Int): Boolean = accions >= im.minimAccions
}
