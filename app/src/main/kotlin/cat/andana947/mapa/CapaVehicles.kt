package cat.andana947.mapa

import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.Punt
import cat.andana947.joc.circulacio.Silueta
import cat.andana947.joc.circulacio.VehicleAlMapa
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Els trens al mapa (docs/vehicles.md). De lluny, un punt del color de la línia; de prop (a partir de [ZOOM_3D]), un
 * bloc en 3D que ocupa el seu tros de via i se'n segueix les corbes. Els trens amb posició *estimada* (segons l'horari)
 * són una mica transparents; els que tenen posició real, opacs.
 */
object CapaVehicles {
    const val ZOOM_3D = 13.0

    private const val FONT_PUNTS = "vehicles-punts"
    private const val FONT_BLOCS = "vehicles-blocs"
    private const val CAPA_PUNTS = "vehicles-punts"
    private const val CAPA_BLOCS_REALS = "vehicles-blocs-reals"
    private const val CAPA_BLOCS_ESTIMATS = "vehicles-blocs-estimats"

    /** Els vehicles van per sota del punt del jugador. */
    private const val CAPA_DE_SOTA = "posicio-precisio"

    /**
     * Les mides de dibuix de cada mode, en metres: llarg, ample i alt. No són les d'un model concret: són prou grans
     * perquè es vegin bé des del carrer i des del zoom 13 (un tren de veritat fa uns 3 m d'ample).
     */
    private data class Mides(val llarg: Double, val ample: Double, val alt: Double)

    private val mides = mapOf(
        "metro" to Mides(160.0, 22.0, 16.0),
        "fgc" to Mides(150.0, 22.0, 16.0),
        "rodalies" to Mides(200.0, 24.0, 18.0),
        "regional" to Mides(200.0, 24.0, 18.0),
        "tram" to Mides(80.0, 20.0, 14.0),
    )
    private val midesPerDefecte = Mides(150.0, 22.0, 16.0)

    private val buida = FeatureCollection.fromFeatures(emptyList())

    /** Afegeix les fonts i les capes a l'estil acabat de carregar. [tinta] és el color de la vora dels punts. */
    fun afegeix(estil: Style, tinta: String) {
        estil.addSource(GeoJsonSource(FONT_PUNTS, buida))
        estil.addSource(GeoJsonSource(FONT_BLOCS, buida))
        val color = Expression.toColor(Expression.get("color"))
        val real = Expression.eq(Expression.get("real"), Expression.literal(true))

        val punts = CircleLayer(CAPA_PUNTS, FONT_PUNTS).withProperties(
            PropertyFactory.circleColor(color),
            PropertyFactory.circleRadius(
                Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(7, 3.5f), Expression.stop(ZOOM_3D, 8f)),
            ),
            PropertyFactory.circleStrokeColor(tinta),
            PropertyFactory.circleStrokeWidth(Expression.switchCase(real, Expression.literal(1.5f), Expression.literal(0.5f))),
            PropertyFactory.circleOpacity(Expression.switchCase(real, Expression.literal(1f), Expression.literal(OPACITAT_ESTIMATS))),
        )
        punts.maxZoom = ZOOM_3D.toFloat()

        fun blocs(id: String, filtre: Expression, opacitat: Float) = FillExtrusionLayer(id, FONT_BLOCS).withProperties(
            PropertyFactory.fillExtrusionColor(color),
            PropertyFactory.fillExtrusionHeight(Expression.get("alt")),
            PropertyFactory.fillExtrusionBase(0f),
            PropertyFactory.fillExtrusionOpacity(opacitat),
        ).withFilter(filtre).also { it.minZoom = ZOOM_3D.toFloat() }

        val sota = estil.getLayer(CAPA_DE_SOTA)
        for (capa in listOf(blocs(CAPA_BLOCS_ESTIMATS, Expression.not(real), OPACITAT_ESTIMATS), blocs(CAPA_BLOCS_REALS, real, 1f), punts)) {
            if (sota != null) estil.addLayerBelow(capa, CAPA_DE_SOTA) else estil.addLayer(capa)
        }
    }

    /** Els vehicles com a GeoJSON per a cada font: blocs de prop, punts de lluny. */
    fun features(
        vehicles: List<VehicleAlMapa>,
        circulacions: Circulacions,
        de3d: Boolean,
        tinta: String,
    ): Pair<FeatureCollection, FeatureCollection> {
        val features = vehicles.map { v ->
            val linia = v.linia?.let { circulacions.horaris.linies[it] }
            val m = mides[linia?.mode] ?: midesPerDefecte
            val geometria = if (!de3d) {
                Point.fromLngLat(v.posicio.lon, v.posicio.lat)
            } else {
                val planta = if (v.tracat != null && v.distancia != null) {
                    Silueta.sobreLaVia(circulacions.tracats[v.tracat!!], v.distancia!!, m.llarg, m.ample)
                } else {
                    Silueta.rectangle(v.posicio, m.llarg, m.ample)
                }
                Polygon.fromLngLats(listOf(planta.map { it.aPoint() }))
            }
            Feature.fromGeometry(geometria).apply {
                addStringProperty("color", colorDe(linia?.color, tinta))
                addBooleanProperty("real", v.real)
                addNumberProperty("alt", m.alt)
            }
        }
        val col = FeatureCollection.fromFeatures(features)
        return if (de3d) buida to col else col to buida
    }

    fun mostra(estil: Style, punts: FeatureCollection, blocs: FeatureCollection) {
        estil.getSourceAs<GeoJsonSource>(FONT_PUNTS)?.setGeoJson(punts)
        estil.getSourceAs<GeoJsonSource>(FONT_BLOCS)?.setGeoJson(blocs)
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
