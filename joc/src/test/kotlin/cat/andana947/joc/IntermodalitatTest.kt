package cat.andana947.joc

import cat.andana947.joc.economia.Intermodalitat
import cat.andana947.joc.geo.distanciaMetres
import cat.andana947.joc.model.FontAfluencia
import cat.andana947.joc.model.Mode
import cat.andana947.joc.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntermodalitatTest {
    private val regles = Intermodalitat(Repositori.config)

    // Un grau de latitud són uns 111,2 km: 0,0005° ≈ 56 m.
    private fun node(id: String, mode: Mode, dLat: Double = 0.0) =
        Node(id, id, mode, 41.4 + dLat, 2.2, 100.0, FontAfluencia.ESTIMAT)

    @Test
    fun `la distància es calcula en metres`() {
        assertEquals(55.6, distanciaMetres(41.4, 2.2, 41.4005, 2.2), 0.2)
        // Barcelona Sants – Plaça de Catalunya, uns 2,6 km.
        assertEquals(2.6, distanciaMetres(41.3792, 2.1404, 41.3870, 2.1700) / 1000, 0.1)
    }

    @Test
    fun `una estació i una parada de bus a menys de 100 m es multipliquen`() {
        val m = regles.multiplicadors(listOf(node("estacio", Mode.METRO), node("bus", Mode.BUS, 0.0005)))
        assertEquals(mapOf("estacio" to 1.25, "bus" to 1.25), m)
    }

    @Test
    fun `sense parada a prop no hi ha bonificació`() {
        assertTrue(regles.multiplicadors(listOf(node("estacio", Mode.METRO), node("bus", Mode.BUS, 0.002))).isEmpty())
        assertTrue(regles.multiplicadors(listOf(node("bus1", Mode.BUS), node("bus2", Mode.BUS, 0.0002))).isEmpty())
        assertTrue(regles.multiplicadors(listOf(node("a", Mode.METRO), node("b", Mode.RODALIES))).isEmpty())
    }

    @Test
    fun `cada mode de més suma fins al màxim`() {
        val tres = regles.multiplicadors(
            listOf(node("metro", Mode.METRO), node("bus", Mode.BUS, 0.0003), node("tram", Mode.TRAM, -0.0003)),
        )
        assertEquals(1.35, tres.getValue("bus"), 1e-9)
        val sis = regles.multiplicadors(Mode.entries.mapIndexed { i, m -> node("n$i", m, i * 0.0001) })
        assertEquals(1.50, sis.values.max(), 1e-9)
    }

    @Test
    fun `les accions compten a partir de 5`() {
        assertTrue(!regles.comptaAccions(4) && regles.comptaAccions(5))
    }
}
