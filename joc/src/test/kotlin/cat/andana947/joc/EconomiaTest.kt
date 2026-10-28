package cat.andana947.joc

import cat.andana947.joc.economia.Economia
import cat.andana947.joc.economia.TipusDia
import cat.andana947.joc.model.Categoria
import cat.andana947.joc.model.FontAfluencia
import cat.andana947.joc.model.Mode
import cat.andana947.joc.model.Node
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EconomiaTest {
    private val economia = Economia(Repositori.config)

    @Test
    fun `l'ingrés segueix la taula del GDD`() {
        // GDD 4.1: viatgers/dia → ingrés/dia i preu de la concessió.
        val taula = listOf(
            Triple(10.0, 0.82, 10),
            Triple(100.0, 3.27, 33),
            Triple(1_000.0, 13.00, 130),
            Triple(10_000.0, 51.75, 518),
            Triple(100_000.0, 206.04, 2060),
        )
        for ((viatgers, ingres, preu) in taula) {
            assertEquals(ingres, Economia.arrodoneix(economia.ingresDiari(viatgers)), "Ingrés amb $viatgers viatgers")
            assertEquals(preu, economia.preuConcessio(viatgers).roundToInt(), "Preu amb $viatgers viatgers")
        }
    }

    @Test
    fun `l'ordre de les parades es manté però la distància s'escurça`() {
        val gran = economia.ingresDiari(54_794.0)
        val petita = economia.ingresDiari(147.0)
        assertTrue(gran > petita)
        // Lineal serien 370 vegades; amb la compressió, unes 35 (GDD 4.1).
        assertEquals(35, (gran / petita).roundToInt())
    }

    @Test
    fun `un any de TMB es converteix en viatgers d'un feiner`() {
        // GDD 8: Casa de l'Aigua (46.884 entrades el 2024) ≈ 147 al dia; Catalunya (17.501.208) ≈ 54.794.
        assertEquals(147, economia.viatgersFeinerDesDeAnual(46_884.0).roundToInt())
        assertEquals(54_794, economia.viatgersFeinerDesDeAnual(17_501_208.0).roundToInt())
    }

    @Test
    fun `un dia no feiner té menys viatgers`() {
        assertEquals(600.0, economia.viatgersDelDia(1000.0, TipusDia.NO_FEINER), 1e-9)
        assertEquals(500.0, economia.viatgersDelDia(1000.0, TipusDia.FEINER, multiplicadorCaos = 0.5), 1e-9)
    }

    @Test
    fun `les estacions de més de 20000 viatgers van per accions`() {
        assertEquals(Categoria.PARADA, economia.categoria(19_999.0))
        assertEquals(Categoria.GRAN_ESTACIO, economia.categoria(20_000.0))
        // GDD 8: Sants (Rodalies, 101.006 per feiner) costa 20,73 € l'acció.
        assertEquals(20.73, Economia.arrodoneix(economia.preuAccio(101_006.0)))
        assertEquals(1.0, economia.preuAccio(10.0))
    }

    @Test
    fun `el dividend és la centèsima part de l'ingrés per acció`() {
        assertEquals(economia.ingresDiari(50_000.0) * 5 / 100, economia.dividend(50_000.0, 5), 1e-9)
    }

    @Test
    fun `el bo de cohesió garanteix un mínim al Pirineu i als municipis poc densos`() {
        val pallars = node(viatgers = 10.0, comarca = "26", densitat = 3.0)
        assertTrue(economia.teBoCohesio(pallars))
        assertEquals(3.0, economia.ingresConcessio(pallars, 10.0))
        // Una parada de 87 viatgers a l'àrea metropolitana dona el mateix (GDD 4.6).
        val metropolitana = node(viatgers = 87.0, comarca = "13", densitat = 16_000.0)
        assertFalse(economia.teBoCohesio(metropolitana))
        assertEquals(3.0, Economia.arrodoneix(economia.ingresConcessio(metropolitana, 87.0)), 0.01)
        // Una zona poc densa fora del Pirineu també el rep.
        assertTrue(economia.teBoCohesio(node(viatgers = 5.0, comarca = "29", densitat = 12.0)))
        // Si l'ingrés ja és més alt, no hi fa res.
        val gran = node(viatgers = 5_000.0, comarca = "39", densitat = 15.0)
        assertEquals(economia.ingresDiari(5_000.0), economia.ingresConcessio(gran, 5_000.0), 1e-9)
    }

    @Test
    fun `arrodoneix a cèntims`() {
        assertEquals(0.82, Economia.arrodoneix(0.8236))
        assertEquals(13.0, Economia.arrodoneix(12.999))
    }

    private fun node(viatgers: Double, comarca: String, densitat: Double) = Node(
        id = "prova",
        nom = "Prova",
        mode = Mode.BUS,
        lat = 42.0,
        lon = 1.0,
        viatgersFeiner = viatgers,
        font = FontAfluencia.ESTIMAT,
        comarca = comarca,
        densitatMunicipi = densitat,
    )
}
