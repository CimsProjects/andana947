package cat.andana947.joc.economia

import cat.andana947.joc.config.ConfiguracioJoc
import cat.andana947.joc.model.Categoria
import cat.andana947.joc.model.Node
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/** Tipus de dia per a l'afluència (GDD 4.4). */
enum class TipusDia { FEINER, NO_FEINER }

/**
 * Les fórmules de l'economia (GDD, secció 4). Totes en catalins. Són les mateixes que farà servir el servidor:
 * el client no calcula mai diners que s'hagin de desar (CLAUDE.md), però sí que els pot mostrar.
 */
class Economia(private val config: ConfiguracioJoc) {
    private val e = config.economia
    private val cal = config.calendari

    /** Ingrés d'una concessió sencera en un dia amb `viatgers` viatgers (GDD 4.1): `t × q × P_ref × (P / P_ref)^α`. */
    fun ingresDiari(viatgers: Double): Double {
        if (viatgers <= 0.0) return 0.0
        val relatiu = viatgers / e.afluenciaReferencia
        return e.tarifaBase * e.quotaConcessio * e.afluenciaReferencia * relatiu.pow(e.exponentAfluencia)
    }

    /** Viatgers d'un dia feiner quan la font només dona el total anual (GDD 4.4). */
    fun viatgersFeinerDesDeAnual(anual: Double): Double = anual / (cal.diesFeiners + cal.factorNoFeiner * cal.diesNoFeiners)

    /** Viatgers d'un dia concret: el feiner, el factor del tipus de dia i el multiplicador del caos. */
    fun viatgersDelDia(viatgersFeiner: Double, dia: TipusDia, multiplicadorCaos: Double = 1.0): Double {
        val factor = if (dia == TipusDia.FEINER) 1.0 else cal.factorNoFeiner
        return viatgersFeiner * factor * multiplicadorCaos
    }

    fun categoria(viatgersFeiner: Double): Categoria =
        if (viatgersFeiner >= config.accionariat.llindarViatgersFeiner) Categoria.GRAN_ESTACIO else Categoria.PARADA

    fun categoria(node: Node): Categoria = categoria(node.viatgersFeiner)

    /** Preu d'una concessió (GDD 4.2): es recupera en `diesRetorn` dies feiners. */
    fun preuConcessio(viatgersFeiner: Double): Double =
        max(e.preuMinimConcessio, e.diesRetorn * ingresDiari(viatgersFeiner))

    fun preuAccio(viatgersFeiner: Double): Double =
        max(e.preuMinimAccio, preuConcessio(viatgersFeiner) / config.accionariat.accionsPerEstacio)

    /** Dividend d'un dia per a `accions` accions d'una gran estació (GDD 4.3). */
    fun dividend(viatgersDelDia: Double, accions: Int): Double =
        ingresDiari(viatgersDelDia) * accions / config.accionariat.accionsPerEstacio

    /** Si la concessió rep el Bo de Cohesió Territorial (GDD 4.6). */
    fun teBoCohesio(node: Node): Boolean {
        val c = config.cohesio
        val densitatBaixa = node.densitatMunicipi?.let { it < c.densitatMaxima } ?: false
        val pirineu = node.comarca != null && node.comarca in c.comarquesAltPirineuIAran
        return densitatBaixa || pirineu
    }

    /** Ingrés d'una concessió en un dia, amb el bo de cohesió i un multiplicador (intermodalitat). */
    fun ingresConcessio(node: Node, viatgersDelDia: Double, multiplicador: Double = 1.0): Double {
        val base = ingresDiari(viatgersDelDia) * multiplicador
        return if (teBoCohesio(node)) max(base, config.cohesio.ingresMinimDiari) else base
    }

    companion object {
        /** El servidor desa els imports amb dos decimals. */
        fun arrodoneix(catalins: Double): Double = (catalins * 100).roundToLong() / 100.0
    }
}
