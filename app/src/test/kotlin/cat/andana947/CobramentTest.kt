package cat.andana947

import cat.andana947.data.joc.EstatCobraments
import cat.andana947.data.joc.RepositoriCobrament
import cat.andana947.data.joc.ServeiJocLocal
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CobramentTest {
    @Test
    fun `es llegeix el cobrament tal com el torna el servidor`() {
        // Forma de la resposta de cobra_arribades (dades de prova).
        val e = Json { ignoreUnknownKeys = true }.decodeFromString(
            EstatCobraments.serializer(),
            """{"ara": "2026-10-30T19:10:35+00:00", "avui": 45.98, "saldo": 1065.98, "ingres_hora": 45.23,
               "en_servei": true, "pendent_total": 0.00, "arribades_hora": 288, "pendent_nombre": 0}""",
        )
        assertEquals(45.23, e.ingresHora, 1e-9)
        assertEquals(288, e.arribadesHora)
    }

    @Test
    fun `cada arribada es veu al saldo de seguida i el servidor mana`() {
        val repo = RepositoriCobrament(ServeiJocLocal())
        // Abans de saber res del servidor, no s'inventa cap saldo.
        repo.sumaArribada(0.12)
        assertNull(repo.estat.value)
        repo.actualitza(EstatCobraments(ara = "2026-10-30T19:00:00Z", saldo = 100.0, avui = 5.0))
        repo.sumaArribada(0.12)
        repo.sumaArribada(0.30)
        assertEquals(100.42, repo.estat.value!!.saldo, 1e-9)
        assertEquals(5.42, repo.estat.value!!.avui, 1e-9)
        // Les xifres del servidor substitueixen les previstes.
        repo.actualitza(EstatCobraments(ara = "2026-10-30T19:01:00Z", saldo = 100.40, avui = 5.40))
        assertEquals(100.40, repo.estat.value!!.saldo, 1e-9)
    }
}
