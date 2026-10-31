package cat.andana947

import cat.andana947.data.joc.EstatCooperatives
import cat.andana947.data.joc.EstatJunta
import cat.andana947.data.joc.OfertaAccions
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `es llegeixen el mercat, la junta i les cooperatives tal com els torna el servidor`() {
        // Forma de les respostes de mercat_accions, estat_junta i estat_cooperatives (dades de prova).
        val mercat = json.decodeFromString(
            ListSerializer(OfertaAccions.serializer()),
            """[{"id": 1, "node_id": "prova:gran", "nom": "Gran", "venedor": "Operador ABCD", "meva": false, "quantitat": 5,
                "preu": 9.5, "preu_referencia": 9.5, "dividend_accio": 0.95, "caduca_el": "2026-11-07T22:00:00+00:00"}]""",
        )
        assertEquals(5, mercat.single().quantitat)
        val junta = json.decodeFromString(
            EstatJunta.serializer(),
            """{"node_id": "prova:gran", "nom": "Gran", "venudes": 30, "meves": 17, "preu_referencia": 9.5,
                "dividend_accio": 1.05, "factor_millores": 1.10,
                "accionistes": [{"nom": "Jo", "quantitat": 17, "jo": true, "president": true}],
                "millores": [{"id": "pantalles", "bonus": 0.10, "cost": 47.5, "feta": true, "proposta": null}],
                "propostes": [{"id": 3, "millora": "ascensor", "cost": 114.0, "proposada_per": "Jo",
                    "caduca_el": "2026-11-03T22:00:00+00:00", "a_favor": 17, "en_contra": 0, "el_meu_vot": true}]}""",
        )
        assertTrue(junta.accionistes.single().president)
        assertNull(junta.millores.single().proposta)
        assertEquals(true, junta.propostes.single().elMeuVot)
        val coops = json.decodeFromString(
            EstatCooperatives.serializer(),
            """{"meva": null, "ranking": [{"id": 1, "nom": "Els de Prova", "comarca": "Barcelonès", "membres": 2, "punts": 340}],
                "a_prop": [], "comarques": [{"codi": "13", "nom": "Barcelonès"}], "maxim_membres": 20}""",
        )
        assertNull(coops.meva)
        assertEquals("Barcelonès", coops.comarques.single().nom)
    }
}
