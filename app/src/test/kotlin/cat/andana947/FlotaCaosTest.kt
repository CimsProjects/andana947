package cat.andana947

import cat.andana947.data.joc.Esdeveniment
import cat.andana947.data.joc.LiniaFlota
import cat.andana947.mapa.CapaVehicles
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlotaCaosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `la part de la flota és la dels vehicles llogats`() {
        assertEquals(0.5, LiniaFlota(linia = "bus:V15", codi = "V15", mode = "bus", vehiclesPunta = 4, vehicles = 2).part, 1e-9)
        assertEquals(0.0, LiniaFlota(linia = "bus:V15", codi = "V15", mode = "bus", vehiclesPunta = 0, vehicles = 2).part, 1e-9)
    }

    @Test
    fun `al mapa, tants vehicles del color del jugador com la seva part de la flota`() {
        val ids = (1..2000).map { "vehicle-$it" }
        assertTrue(ids.none { CapaVehicles.esDelJugador(it, 0.0) })
        assertTrue(ids.all { CapaVehicles.esDelJugador(it, 1.0) })
        val meitat = ids.count { CapaVehicles.esDelJugador(it, 0.5) }
        assertTrue("$meitat de 2000", meitat in 850..1150)
        // Sempre els mateixos: si en té més, els d'abans continuen sent seus.
        assertTrue(ids.filter { CapaVehicles.esDelJugador(it, 0.25) }.all { CapaVehicles.esDelJugador(it, 0.5) })
        assertFalse(CapaVehicles.esDelJugador("x", -1.0))
    }

    @Test
    fun `es llegeixen la flota i els esdeveniments tal com els torna el servidor`() {
        // Forma de les respostes d'estat_flota i esdeveniments_avui (dades de prova).
        val flota = json.decodeFromString(
            ListSerializer(LiniaFlota.serializer()),
            """[{"linia": "metro:L1", "codi": "L1", "mode": "metro", "color": "CE1126", "andanes": 3, "nodes": 30,
                "vehicles_punta": 32, "maxim": 4, "vehicles": 1, "fianca": 391.0, "model": "Metro", "preu_model": 6380952,
                "font_model": "TMB", "ingres_dia": 29.24, "cost_dia": 13.04, "net_dia": 16.2, "fianca_vehicle": 391.2,
                "factor_caos": 1}]""",
        )
        assertEquals(4, flota.single().maxim)
        val esdeveniments = json.decodeFromString(
            ListSerializer(Esdeveniment.serializer()),
            """[{"id": 7, "tipus": "temporal", "origen": "aleatori", "nom": "Maresme", "inici": "2026-11-01T23:00:00+00:00",
                "fi": "2026-11-03T23:00:00+00:00", "multiplicador": 0.6, "linies": [], "municipis": [], "modes": [],
                "a_prop_de": null, "andanes": ["renfe:79600"]}]""",
        )
        assertEquals(listOf("renfe:79600"), esdeveniments.single().andanes)
    }
}
