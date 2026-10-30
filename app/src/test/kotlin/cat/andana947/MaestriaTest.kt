package cat.andana947

import cat.andana947.data.joc.EstatAndana
import cat.andana947.data.joc.EstatMaestries
import cat.andana947.data.joc.Maestria
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaestriaTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `el progrés compta dins el tram del nivell`() {
        assertEquals(0f, Maestria(xp = 150.0, nivell = 3, xpNivell = 150.0, xpSeguent = 400.0).progres, 0.0001f)
        assertEquals(0.5f, Maestria(xp = 275.0, nivell = 3, xpNivell = 150.0, xpSeguent = 400.0).progres, 0.0001f)
        // Al nivell màxim, la barra és plena.
        assertEquals(1f, Maestria(xp = 90000.0, nivell = 10, xpNivell = 80000.0, xpSeguent = null).progres, 0.0001f)
    }

    @Test
    fun `es llegeix la fitxa d'una andana tal com la torna el servidor`() {
        // Forma de la resposta d'estat_andana (dades de prova).
        val text = """
            {"id": "prova:1", "nom": "Prova", "mode": "rodalies", "regal": false, "linies": [{"id": "rodalies:R1",
             "codi": "R1", "mode": "rodalies", "color": "7DBCEC", "maestria": {"xp": 174, "nivell": 3, "xp_nivell": 150,
             "xp_seguent": 400}, "arribades_dia": 40}], "millores": [{"id": "ascensor", "bonus": 0.20, "cost": 120.5,
             "comprada": false, "nivell_minim": 5, "desbloquejada": false}], "maestria": {"xp": 100, "nivell": 2,
             "xp_nivell": 50, "xp_seguent": 150}, "municipi": "Prova", "operador": "Prova", "ingres_diari": 13.0,
             "arribades_dia": 40, "multiplicador": 1.5, "presencial_fins": null, "factor_millores": 1.05,
             "manteniment_diari": 0.65}
        """.trimIndent()
        val a = json.decodeFromString(EstatAndana.serializer(), text)
        assertEquals(2, a.maestria.nivell)
        assertEquals("R1", a.linies.single().codi)
        assertEquals(5, a.millores.single().nivellMinim)
        assertNull(a.presencialFins)
        assertEquals(1.05, a.factorMillores, 1e-9)
    }

    @Test
    fun `les maestries buides tenen una xarxa buida`() {
        val e = json.decodeFromString(EstatMaestries.serializer(), """{"linies": [], "andanes": []}""")
        assertTrue(e.linies.isEmpty())
        assertEquals(0, e.xarxaMesGran.andanes)
    }
}
