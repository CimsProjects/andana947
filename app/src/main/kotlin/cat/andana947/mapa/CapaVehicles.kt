package cat.andana947.mapa

import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.Punt
import cat.andana947.joc.circulacio.Silueta
import cat.andana947.joc.circulacio.VehicleAlMapa
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow

/**
 * Els trens al mapa (docs/vehicles.md): a qualsevol zoom, un bloc del color de la línia amb vora de tinta, que ocupa
 * el seu tros de via i en segueix les corbes. Vist des de dalt és la planta del tren; inclinant el mapa, un bloc en
 * 3D. La mida s'adapta al zoom perquè es vegi sempre igual a la pantalla, i de molt a prop no es fa mai més petit
 * que una mida mínima en metres. Els trens amb posició *estimada* (segons l'horari) són una mica transparents; els
 * que tenen posició real, opacs.
 */
object CapaVehicles {
    private const val FONT = "vehicles"
    private const val CAPA_REALS = "vehicles-reals"
    private const val CAPA_ESTIMATS = "vehicles-estimats"
    private const val CAPA_VORA = "vehicles-vora"

    /** Els vehicles van per sota del punt del jugador. */
    private const val CAPA_DE_SOTA = "posicio-precisio"

    /**
     * Les mides de dibuix de cada mode: llarg i ample en píxels de pantalla, i el llarg mínim en metres quan el mapa
     * és molt a prop. No són les d'un model de tren concret. L'ample és sempre proporcional al llarg, i l'alçada del
     * bloc, a l'ample.
     */
    private data class Mides(val llargPx: Double, val amplePx: Double, val llargMinim: Double)

    private val mides = mapOf(
        "metro" to Mides(28.0, 7.0, 100.0),
        "fgc" to Mides(28.0, 7.0, 100.0),
        "rodalies" to Mides(34.0, 8.0, 130.0),
        "regional" to Mides(34.0, 8.0, 130.0),
        "tram" to Mides(20.0, 6.0, 40.0),
    )
    private val midesPerDefecte = Mides(28.0, 7.0, 100.0)
    private const val ALT_PER_AMPLE = 0.8

    /** Metres per píxel al zoom 0 a l'equador, amb les teselles de 512 px de MapLibre (40.075.016,686 m / 512). */
    private const val METRES_PER_PIXEL_Z0 = 78_271.516_96

    fun metresPerPixel(zoom: Double, latitud: Double): Double = METRES_PER_PIXEL_Z0 * cos(latitud * PI / 180) / 2.0.pow(zoom)

    private val buida = FeatureCollection.fromFeatures(emptyList())

    /** Afegeix la font i les capes a l'estil acabat de carregar. [tinta] és el color de la vora dels trens. */
    fun afegeix(estil: Style, tinta: String) {
        estil.addSource(GeoJsonSource(FONT, buida))
        val color = Expression.toColor(Expression.get("color"))
        val real = Expression.eq(Expression.get("real"), Expression.literal(true))

        fun blocs(id: String, filtre: Expression, opacitat: Float) = FillExtrusionLayer(id, FONT).withProperties(
            PropertyFactory.fillExtrusionColor(color),
            PropertyFactory.fillExtrusionHeight(Expression.get("alt")),
            PropertyFactory.fillExtrusionBase(0f),
            PropertyFactory.fillExtrusionOpacity(opacitat),
            // Color pla, sense degradat: el tren es veu net, com la resta de l'estil.
            PropertyFactory.fillExtrusionVerticalGradient(false),
        ).withFilter(filtre)

        // La vora: des de dalt, el contorn de la planta del tren.
        val vora = LineLayer(CAPA_VORA, FONT).withProperties(
            PropertyFactory.lineColor(tinta),
            PropertyFactory.lineWidth(Expression.switchCase(real, Expression.literal(1.5f), Expression.literal(1f))),
            PropertyFactory.lineJoin("miter"),
        )

        val sota = estil.getLayer(CAPA_DE_SOTA)
        for (capa in listOf(blocs(CAPA_ESTIMATS, Expression.not(real), OPACITAT_ESTIMATS), blocs(CAPA_REALS, real, 1f), vora)) {
            if (sota != null) estil.addLayerBelow(capa, CAPA_DE_SOTA) else estil.addLayer(capa)
        }
    }

    /** Els vehicles com a GeoJSON, amb la mida per als [metresPerPixel] del zoom actual. */
    fun features(
        vehicles: List<VehicleAlMapa>,
        circulacions: Circulacions,
        metresPerPixel: Double,
        tinta: String,
    ): FeatureCollection = FeatureCollection.fromFeatures(
        vehicles.map { v ->
            val linia = v.linia?.let { circulacions.horaris.linies[it] }
            val m = mides[linia?.mode] ?: midesPerDefecte
            val llarg = max(m.llargPx * metresPerPixel, m.llargMinim)
            val ample = llarg * m.amplePx / m.llargPx
            val planta = if (v.tracat != null && v.distancia != null) {
                Silueta.sobreLaVia(circulacions.tracats[v.tracat!!], v.distancia!!, llarg, ample)
            } else {
                Silueta.rectangle(v.posicio, llarg, ample)
            }
            Feature.fromGeometry(Polygon.fromLngLats(listOf(planta.map { it.aPoint() }))).apply {
                addStringProperty("color", colorDe(linia?.color, tinta))
                addBooleanProperty("real", v.real)
                addNumberProperty("alt", ample * ALT_PER_AMPLE)
            }
        },
    )

    fun mostra(estil: Style, vehicles: FeatureCollection) {
        estil.getSourceAs<GeoJsonSource>(FONT)?.setGeoJson(vehicles)
    }

    /**
     * El color de la línia en format `#RRGGBB`. Sense color, o si és tan clar que no es veuria sobre el mapa blanc (els
     * regionals de Renfe són gairebé blancs al GTFS), la tinta.
     */
    fun colorDe(hex: String?, tinta: String): String {
        val net = hex?.trim()?.removePrefix("#")?.takeIf { it.length == 6 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
            ?: return tinta
        val r = net.substring(0, 2).toInt(16)
        val g = net.substring(2, 4).toInt(16)
        val b = net.substring(4, 6).toInt(16)
        val lluminositat = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return if (lluminositat > LLUMINOSITAT_MAXIMA) tinta else "#${net.uppercase()}"
    }

    private fun Punt.aPoint() = Point.fromLngLat(lon, lat)

    private const val OPACITAT_ESTIMATS = 0.6f
    private const val LLUMINOSITAT_MAXIMA = 0.85
}
