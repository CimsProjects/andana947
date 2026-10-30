package cat.andana947.joc

import cat.andana947.joc.config.ConfiguracioJoc
import cat.andana947.joc.config.ConfiguracioJoc.Origen
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ConfiguracioJocTest {
    private val config = Repositori.config
    private val arbre = Json.parseToJsonElement(Repositori.textConfiguracio).jsonObject

    @Test
    fun `els valors són els del GDD`() {
        with(config.economia) {
            assertEquals(200.0, capitalInicial)
            assertEquals(1.30, tarifaBase)
            assertEquals(0.01, quotaConcessio)
            assertEquals(1000.0, afluenciaReferencia)
            assertEquals(0.6, exponentAfluencia)
            assertEquals(10, diesRetorn)
            assertEquals(4, periodeCobramentHores)
            assertEquals(0, 24 % periodeCobramentHores)
        }
        assertEquals(14, config.concessions.duradaDies)
        with(config.regal) {
            assertEquals(5, opcionsPerProvincia)
            assertEquals(2, tramsPerProvincia)
            assertEquals(2, maximAndanesPerProvincia)
            assertTrue(tramsPerProvincia in 0..2 && tramsPerProvincia <= opcionsPerProvincia)
            assertTrue(viatgersMinims < viatgersMaxims)
            // Les andanes de regal no poden ser grans estacions, que van per accions.
            assertTrue(viatgersMaxims <= config.accionariat.llindarViatgersFeiner)
        }
        assertEquals(20000.0, config.accionariat.llindarViatgersFeiner)
        assertEquals(100.0, config.intermodalitat.radiMetres)
        assertEquals(60.0, config.checkin.radiParadaMetres)
        assertEquals(150.0, config.checkin.radiEstacioMetres)
    }

    @Test
    fun `la tarifa base és la T-casual d'una zona dividida en deu viatges`() {
        assertEquals(13.00 / 10, config.economia.tarifaBase, 1e-9)
    }

    @Test
    fun `el calendari del 2026 té 365 dies`() {
        assertEquals(365, config.calendari.diesFeiners + config.calendari.diesNoFeiners)
    }

    @Test
    fun `cada bloc diu d'on surten els valors`() {
        for ((nom, bloc) in config.blocs) {
            val camps = arbre.getValue(nom).jsonObject.keys - setOf("origen", "fonts")
            assertTrue(ConfiguracioJoc.DEFECTE in bloc.origen, "Al bloc $nom li falta l'origen per defecte")
            for (clau in bloc.origen.keys - ConfiguracioJoc.DEFECTE) {
                assertTrue(clau in camps, "L'origen del bloc $nom parla d'un camp que no existeix: $clau")
            }
            for (clau in bloc.fonts.keys - ConfiguracioJoc.DEFECTE) {
                assertTrue(clau in camps, "Les fonts del bloc $nom parlen d'un camp que no existeix: $clau")
            }
        }
    }

    @Test
    fun `tot valor real té la font escrita`() {
        for ((nom, bloc) in config.blocs) {
            val camps = arbre.getValue(nom).jsonObject.keys - setOf("origen", "fonts")
            for (camp in camps.filter { bloc.origenDe(it) == Origen.REAL }) {
                assertTrue(
                    camp in bloc.fonts || ConfiguracioJoc.DEFECTE in bloc.fonts,
                    "El valor real $nom.$camp no diu d'on surt",
                )
            }
        }
    }

    @Test
    fun `les comarques de l'Alt Pirineu i Aran existeixen`() {
        val comarques = Json.parseToJsonElement(File(Repositori.arrel, "dades/territori/comarques.json").readText())
            .jsonArray.associate { it.jsonObject.getValue("codi").jsonPrimitive.content to it.jsonObject.getValue("nom").jsonPrimitive.content }
        val noms = config.cohesio.comarquesAltPirineuIAran.map { comarques.getValue(it) }.toSet()
        assertEquals(
            setOf("Alt Urgell", "Alta Ribagorça", "Cerdanya", "Pallars Jussà", "Pallars Sobirà", "Val d'Aran"),
            noms,
        )
    }

    @Test
    fun `els multiplicadors d'intermodalitat són coherents`() {
        with(config.intermodalitat) {
            assertTrue(multiplicador > 1.0)
            assertTrue(maxim >= multiplicador)
            assertTrue(perModeAddicional > 0.0)
        }
    }

    @Test
    fun `la configuració no té camps desconeguts`() {
        // El lector és estricte: un camp mal escrit al JSON fa fallar la lectura.
        assertTrue(arbre.keys.all { it == "versio" || it in config.blocs }, "Bloc desconegut: ${arbre.keys}")
        assertTrue(arbre.values.drop(1).all { it is JsonObject })
    }
}
