package cat.andana947.mapa

import cat.andana947.data.joc.XarxaJugador
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * El que és del jugador al mapa: els seus trams (el tros de via entre dues andanes seves seguides, amb el color de la
 * línia) i les seves andanes (un punt del color fort; les del Director, amb un anell). La resta del mapa és gris.
 */
object CapaXarxa {
    private const val FONT_TRAMS = "xarxa-trams"
    private const val FONT_ANDANES = "xarxa-andanes"
    private const val CAPA_TRAMS_VORA = "xarxa-trams-vora"
    private const val CAPA_TRAMS = "xarxa-trams"
    private const val CAPA_REGALS = "xarxa-regals"
    private const val CAPA_ANDANES = "xarxa-andanes"
    private const val CAPA_NOMS = "xarxa-noms"
    private const val CAPA_DE_SOTA = "posicio-precisio"

    private val buida = FeatureCollection.fromFeatures(emptyList())

    /** Les capes dels trams, sota els vehicles: s'han d'afegir abans que les dels vehicles. */
    fun afegeixTrams(estil: Style, tinta: String) {
        estil.addSource(GeoJsonSource(FONT_TRAMS, buida))
        val amplada = Expression.interpolate(
            Expression.linear(), Expression.zoom(),
            Expression.stop(8, 2.5f), Expression.stop(12, 5f), Expression.stop(16, 9f),
        )
        val vora = LineLayer(CAPA_TRAMS_VORA, FONT_TRAMS).withProperties(
            PropertyFactory.lineColor(tinta),
            PropertyFactory.lineWidth(Expression.sum(amplada, Expression.literal(3f))),
            PropertyFactory.lineOpacity(OPACITAT_TRAMS * 0.8f),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
        )
        val color = LineLayer(CAPA_TRAMS, FONT_TRAMS).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineWidth(amplada),
            // Una mica transparents: els vehicles que hi passen per sobre s'han de veure.
            PropertyFactory.lineOpacity(OPACITAT_TRAMS),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
        )
        afegeixSota(estil, vora, color)
    }

    /** Les capes de les andanes, damunt dels vehicles: s'han d'afegir després que les dels vehicles. */
    fun afegeixAndanes(estil: Style, tinta: String, fort: String, halo: String) {
        estil.addSource(GeoJsonSource(FONT_ANDANES, buida))
        val radi = Expression.interpolate(
            Expression.linear(), Expression.zoom(),
            Expression.stop(8, 3f), Expression.stop(12, 6f), Expression.stop(16, 10f),
        )
        val regals = CircleLayer(CAPA_REGALS, FONT_ANDANES).withProperties(
            PropertyFactory.circleRadius(Expression.product(radi, Expression.literal(1.8f))),
            PropertyFactory.circleColor("rgba(0,0,0,0)"),
            PropertyFactory.circleStrokeColor(fort),
            PropertyFactory.circleStrokeWidth(2.5f),
        ).withFilter(Expression.eq(Expression.get("regal"), Expression.literal(true)))
        val andanes = CircleLayer(CAPA_ANDANES, FONT_ANDANES).withProperties(
            PropertyFactory.circleRadius(radi),
            // El color de la skin del jugador; amb concessió física (×1,5), la vora més gruixuda.
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor(tinta),
            PropertyFactory.circleStrokeWidth(
                Expression.switchCase(Expression.eq(Expression.get("presencial"), Expression.literal(true)), Expression.literal(3.5f), Expression.literal(1.5f)),
            ),
        )
        val noms = SymbolLayer(CAPA_NOMS, FONT_ANDANES).withProperties(
            PropertyFactory.textField(Expression.get("nom")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor(tinta),
            PropertyFactory.textHaloColor(halo),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textAnchor("top"),
            PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
            PropertyFactory.textOptional(true),
        ).apply { minZoom = ZOOM_NOMS }
        afegeixSota(estil, regals, andanes, noms)
    }

    /** Dibuixa la xarxa del jugador (o res, si encara no se sap). */
    fun mostra(estil: Style, xarxa: XarxaJugador?, tinta: String) {
        val arribades = xarxa?.arribades
        val trams = if (arribades == null) buida else FeatureCollection.fromFeatures(
            arribades.trams.map { tram ->
                val linia = arribades.linia(tram.linia)
                Feature.fromGeometry(LineString.fromLngLats(tram.punts.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                    addStringProperty("color", CapaVehicles.colorDe(linia?.color, tinta))
                }
            },
        )
        val andanes = FeatureCollection.fromFeatures(
            xarxa?.andanes.orEmpty().map { a ->
                Feature.fromGeometry(Point.fromLngLat(a.lon, a.lat)).apply {
                    addStringProperty("nom", a.nom)
                    addBooleanProperty("regal", a.regal)
                    addBooleanProperty("presencial", a.presencial)
                    addStringProperty("color", a.color)
                }
            },
        )
        estil.getSourceAs<GeoJsonSource>(FONT_TRAMS)?.setGeoJson(trams)
        estil.getSourceAs<GeoJsonSource>(FONT_ANDANES)?.setGeoJson(andanes)
    }

    private fun afegeixSota(estil: Style, vararg capes: Layer) {
        val sota = estil.getLayer(CAPA_DE_SOTA)
        for (capa in capes) if (sota != null) estil.addLayerBelow(capa, CAPA_DE_SOTA) else estil.addLayer(capa)
    }

    private const val OPACITAT_TRAMS = 0.7f

    /** Des d'aquest zoom es veu el nom de les andanes del jugador. */
    private const val ZOOM_NOMS = 12f
}
