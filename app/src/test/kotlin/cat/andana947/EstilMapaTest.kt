package cat.andana947

import cat.andana947.mapa.EstilMapa
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EstilMapaTest {
    private val plantilla = File("src/main/assets/${EstilMapa.FITXER}").readText()

    private fun estil(fosc: Boolean): JsonObject = Json.parseToJsonElement(EstilMapa.genera(plantilla, fosc)).jsonObject

    private fun capes(fosc: Boolean) = estil(fosc)["layers"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun `els dos temes omplen totes les marques i donen un JSON vàlid`() {
        for (fosc in listOf(false, true)) {
            val json = EstilMapa.genera(plantilla, fosc)
            assertFalse("{{" in json, "Queda alguna marca sense omplir (fosc = $fosc)")
            assertEquals(8, Json.parseToJsonElement(json).jsonObject["version"]!!.jsonPrimitive.content.toInt())
        }
    }

    @Test
    fun `els dos temes tenen els mateixos colors`() {
        assertEquals(EstilMapa.paleta(false).keys, EstilMapa.paleta(true).keys)
    }

    @Test
    fun `totes les marques de la plantilla tenen color i tots els colors es fan servir`() {
        val marques = Regex("\\{\\{(\\w+)\\}\\}").findAll(plantilla).map { it.groupValues[1] }.toSet()
        assertEquals(EstilMapa.paleta(false).keys, marques)
    }

    @Test
    fun `una marca desconeguda és un error`() {
        val resultat = runCatching { EstilMapa.genera("""{"a":"{{no_existeix}}"}""", false) }
        assertTrue(resultat.isFailure)
    }

    @Test
    fun `el color fort és el magenta de l'abast`() {
        assertEquals("#FF3D7F", EstilMapa.paleta(false)["fort"])
        assertEquals("#FF3D7F", EstilMapa.paleta(true)["fort"])
    }

    @Test
    fun `les teselles són d'OpenFreeMap, sense clau`() {
        val fonts = estil(false)["sources"]!!.jsonObject
        val url = fonts["openmaptiles"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertEquals("https://tiles.openfreemap.org/planet", url)
        assertFalse("key" in plantilla.lowercase(), "L'estil no ha de dur cap clau")
    }

    @Test
    fun `els identificadors de les capes no es repeteixen`() {
        val ids = capes(false).map { it["id"]!!.jsonPrimitive.content }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `el punt del jugador es dibuixa a sobre de tot`() {
        val capes = capes(false)
        val font = estil(false)["sources"]!!.jsonObject
        assertTrue(EstilMapa.FONT_POSICIO in font)
        val darreres = capes.takeLast(2).map { it["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("posicio-precisio", "posicio"), darreres)
        assertTrue(capes.takeLast(2).all { it["source"]!!.jsonPrimitive.content == EstilMapa.FONT_POSICIO })
    }

    @Test
    fun `el radi de precisió es converteix bé a píxels`() {
        // A l'equador, al zoom 0, un píxel són 40.075.016,686 m / 512.
        assertTrue(abs(EstilMapa.radiAlZoom0(40_075_016.686 / 512, 0.0) - 1.0) < 1e-6)
        // A 60° de latitud, els metres per píxel es redueixen a la meitat: el mateix radi fa el doble de píxels.
        val equador = EstilMapa.radiAlZoom0(50.0, 0.0)
        assertTrue(abs(EstilMapa.radiAlZoom0(50.0, 60.0) - 2 * equador) < 1e-9)
    }
}
