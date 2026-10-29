package cat.andana947

import cat.andana947.data.preferencies.Camera
import cat.andana947.mapa.EstilMapa
import cat.andana947.mapa.LimitsCatalunya
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LimitsCatalunyaTest {
    private val contorn = Json.parseToJsonElement(File("src/main/assets/${LimitsCatalunya.FITXER_CONTORN}").readText())
        .jsonObject["features"]!!.jsonArray.single().jsonObject

    private val plantilla = File("src/main/assets/${EstilMapa.FITXER}").readText()

    @Test
    fun `la capsa del codi és la del contorn generat`() {
        val capsa = contorn["properties"]!!.jsonObject["capsa"]!!.jsonArray.map { it.jsonPrimitive.double }
        assertEquals(listOf(LimitsCatalunya.OEST, LimitsCatalunya.SUD, LimitsCatalunya.EST, LimitsCatalunya.NORD), capsa)
    }

    @Test
    fun `el contorn és el món amb Catalunya com a forats`() {
        val geometria = contorn["geometry"]!!.jsonObject
        assertEquals("Polygon", geometria["type"]!!.jsonPrimitive.content)
        val anells = geometria["coordinates"]!!.jsonArray
        assertTrue(anells.size >= 2, "Hi ha d'haver el món i almenys un forat")
        for (anell in anells.drop(1)) {
            for (punt in anell.jsonArray) {
                val (lon, lat) = punt.jsonArray.map { it.jsonPrimitive.double }
                assertTrue(lon in LimitsCatalunya.OEST..LimitsCatalunya.EST && lat in LimitsCatalunya.SUD..LimitsCatalunya.NORD)
            }
        }
    }

    @Test
    fun `la càmera inicial és dins de Catalunya i es veu sencera`() {
        assertEquals(Camera.CATALUNYA, LimitsCatalunya.limita(Camera.CATALUNYA))
        assertTrue(Camera.CATALUNYA.zoom >= LimitsCatalunya.ZOOM_MINIM)
    }

    @Test
    fun `una càmera de fora torna a Catalunya i una massa allunyada s'hi apropa`() {
        assertEquals(Camera.CATALUNYA, LimitsCatalunya.limita(Camera(48.8566, 2.3522, 12.0)))
        assertEquals(Camera(41.3851, 2.1734, LimitsCatalunya.ZOOM_MINIM), LimitsCatalunya.limita(Camera(41.3851, 2.1734, 3.0)))
        assertEquals(Camera(41.3851, 2.1734, 15.0), LimitsCatalunya.limita(Camera(41.3851, 2.1734, 15.0)))
    }

    @Test
    fun `l'estil carrega el contorn i el dibuixa per sota del jugador`() {
        val estil = Json.parseToJsonElement(EstilMapa.genera(plantilla, false)).jsonObject
        val font = estil["sources"]!!.jsonObject[EstilMapa.FONT_CATALUNYA]!!.jsonObject
        assertEquals("asset://${LimitsCatalunya.FITXER_CONTORN}", font["data"]!!.jsonPrimitive.content)
        val capes = estil["layers"]!!.jsonArray.map { it.jsonObject }
        val ids = capes.map { it["id"]!!.jsonPrimitive.content }
        val delContorn = capes.filter { it["source"]?.jsonPrimitive?.content == EstilMapa.FONT_CATALUNYA }
            .map { ids.indexOf(it["id"]!!.jsonPrimitive.content) }
        assertEquals(2, delContorn.size)
        // Per sobre de tot el mapa (també dels noms de fora) i per sota del punt del jugador.
        assertTrue(delContorn.all { it > ids.indexOf("nom-ciutat") && it < ids.indexOf("posicio-precisio") })
    }
}
