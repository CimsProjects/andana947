package cat.andana947.mapa

import kotlin.math.PI
import kotlin.math.cos

/**
 * L'estil propi del mapa (docs/abast.md, secció 4): blanc i negre amb el color fort, sobre les teselles vectorials
 * d'OpenStreetMap d'OpenFreeMap, que no demanen clau. La plantilla és `assets/mapa/estil.json`, amb els colors com a
 * marques `{{nom}}` que s'omplen aquí segons el tema clar o fosc.
 */
object EstilMapa {
    const val FITXER = "mapa/estil.json"

    /** La font GeoJSON i les capes del punt del jugador, definides a la plantilla. */
    const val FONT_POSICIO = "posicio"

    /** La font GeoJSON que tapa el que queda fora de Catalunya i en dibuixa la vora (LimitsCatalunya). */
    const val FONT_CATALUNYA = "catalunya"

    private val clar = mapOf(
        "fons" to "#FFFFFF",
        "parc" to "#F4F4F4",
        "aigua" to "#E4E4E4",
        "edifici" to "#F2F2F2",
        "edifici_vora" to "#E4E4E4",
        "carrer" to "#ECECEC",
        "carrer_major" to "#D6D6D6",
        "autopista" to "#B8B8B8",
        "tinta" to "#111111",
        "text" to "#111111",
        "text_secundari" to "#6B6B6B",
        "halo" to "#FFFFFF",
        "fort" to "#FF3D7F",
        "fora" to "#F0F0F0",
    )

    private val fosc = mapOf(
        "fons" to "#0F0F0F",
        "parc" to "#151515",
        "aigua" to "#1E1E1E",
        "edifici" to "#1A1A1A",
        "edifici_vora" to "#262626",
        "carrer" to "#222222",
        "carrer_major" to "#303030",
        "autopista" to "#474747",
        "tinta" to "#F2F2F2",
        "text" to "#F2F2F2",
        "text_secundari" to "#9A9A9A",
        "halo" to "#0F0F0F",
        "fort" to "#FF3D7F",
        "fora" to "#070707",
    )

    fun paleta(fosc: Boolean): Map<String, String> = if (fosc) this.fosc else clar

    private val marca = Regex("\\{\\{(\\w+)\\}\\}")

    /** Omple la [plantilla] amb els colors del tema. Si hi queda cap marca sense color, és un error de la plantilla. */
    fun genera(plantilla: String, fosc: Boolean): String {
        val colors = paleta(fosc)
        return marca.replace(plantilla) { m ->
            colors[m.groupValues[1]] ?: error("L'estil del mapa fa servir un color que no existeix: ${m.value}")
        }
    }

    /** Metres per píxel al zoom 0 a l'equador, amb les teselles de 512 px de MapLibre (40.075.016,686 m / 512). */
    private const val METRES_PER_PIXEL_Z0 = 78_271.516_96

    /**
     * El radi de [metres] en píxels al zoom 0 i a la [latitud] donada. La capa `posicio-precisio` el dobla a cada nivell
     * de zoom, i així el cercle de precisió fa sempre els metres de veritat.
     */
    fun radiAlZoom0(metres: Double, latitud: Double): Double = metres / (METRES_PER_PIXEL_Z0 * cos(latitud * PI / 180))
}
