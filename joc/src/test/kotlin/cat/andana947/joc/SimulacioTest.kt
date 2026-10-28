package cat.andana947.joc

import cat.andana947.joc.economia.Economia
import cat.andana947.joc.model.FontAfluencia
import cat.andana947.joc.model.Mode
import cat.andana947.joc.model.Node
import cat.andana947.joc.simulacio.Escenari
import cat.andana947.joc.simulacio.Informe
import cat.andana947.joc.simulacio.Simulacio
import cat.andana947.joc.simulacio.Visita
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulacioTest {
    private val config = Repositori.config
    private val economia = Economia(config)

    /**
     * L'escenari de la fase 1 (GDD, secció 8): només estacions, amb les xifres reals que cita el GDD (TMB 2024,
     * entrades anuals; Renfe 2018, usuaris d'un feiner).
     */
    private fun escenariFase1(): Escenari {
        fun anual(id: String, mode: Mode, entrades: Double) =
            Node(id, id, mode, 41.4, 2.17, economia.viatgersFeinerDesDeAnual(entrades), FontAfluencia.REAL)
        fun feiner(id: String, viatgers: Double) = Node(id, id, Mode.RODALIES, 41.4, 2.17, viatgers, FontAfluencia.REAL)
        // Coordenades iguals a propòsit: sense parades de bus no hi pot haver intermodalitat.
        val nodes = listOf(
            anual("Baró de Viver", Mode.METRO, 477_756.0),
            anual("Casa de l'Aigua", Mode.METRO, 46_884.0),
            anual("Catalunya", Mode.METRO, 17_501_208.0),
            anual("Diagonal", Mode.METRO, 16_207_348.0),
            anual("Espanya", Mode.METRO, 15_135_060.0),
            feiner("Sants", 101_006.0),
            feiner("Plaça de Catalunya", 67_866.0),
            feiner("Passeig de Gràcia", 38_791.0),
            feiner("Clot-Aragó", 27_284.0),
            feiner("Arc de Triomf", 26_539.0),
        )
        val feiners = (1..5).toList()
        return Escenari(
            nom = "Fase 1",
            inici = "2026-11-02",
            dies = 30,
            nodes = nodes,
            visites = listOf(
                Visita("Baró de Viver", diesSetmana = feiners),
                Visita("Clot-Aragó", diesSetmana = feiners),
                Visita("Passeig de Gràcia", diesSetmana = feiners),
                Visita("Diagonal", diesSetmana = feiners),
                Visita("Arc de Triomf", diesSetmana = listOf(2)),
                Visita("Sants", diesSetmana = listOf(3)),
                Visita("Plaça de Catalunya", diesSetmana = listOf(5)),
                Visita("Catalunya", diesSetmana = listOf(5)),
                Visita("Espanya", diesSetmana = listOf(6)),
                Visita("Casa de l'Aigua", diesSimulacio = listOf(12, 24)),
            ),
        )
    }

    @Test
    fun `reprodueix la simulació de la fase 1`() {
        // La taula de la secció 8 del GDD, feta amb scripts/simulacio_primer_mes.py (fase 1).
        val r = Simulacio(config).executa(escenariFase1())
        val esperat = mapOf(1 to (19.39 to 25.51), 7 to (23.16 to 50.29), 14 to (42.22 to 94.74), 21 to (76.20 to 188.23), 30 to (150.60 to 830.83))
        for ((dia, valors) in esperat) {
            val d = r.dies[dia - 1]
            assertEquals(valors.first, Economia.arrodoneix(d.ingres), "Ingrés del dia $dia")
            assertEquals(valors.second, Economia.arrodoneix(d.saldo), "Saldo del dia $dia")
        }
        assertEquals(2136.82, Economia.arrodoneix(r.ingressos))
        assertEquals(1506.0, Economia.arrodoneix(r.inversio), 0.5)
        assertEquals(104, r.dies.last().accions)
        assertEquals(2, r.dies.last().concessionsActives)
        // El GDD de la fase 1 deia 3 renovacions, però l'script en feia 4 (dies 8, 15, 22 i 29).
        assertEquals(4, r.renovacions["Baró de Viver"])
        assertEquals(20, r.accions["Diagonal"])
        assertTrue(r.intermodalitat.isEmpty())
    }

    @Test
    fun `l'informe es pot escriure`() {
        val text = Informe(config).escriu(Simulacio(config).executa(escenariFase1()))
        assertTrue("830,83 €" in text, text)
    }

    @Test
    fun `l'escenari generat, si hi és, es pot simular`() {
        val fitxer = File(Repositori.arrel, "dades/generades/escenari_primer_mes.json")
        if (!fitxer.exists()) return
        val escenari = Escenari.llegeix(fitxer.readText())
        val r = Simulacio(config).executa(escenari)
        assertEquals(escenari.dies, r.dies.size)
        assertTrue(r.dies.last().saldo > 0)
    }
}
