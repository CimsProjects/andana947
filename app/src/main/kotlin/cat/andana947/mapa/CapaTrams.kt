package cat.andana947.mapa

import cat.andana947.joc.circulacio.Tracat
import cat.andana947.joc.trams.EstatTram
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Els trams al mapa (GDD 4.0): el traçat de cada tram, del color del que hi pot fer el jugador. Gris si encara no se'l
 * pot permetre (o és gran), verd si el pot comprar i blau si ja és seu. Els trens i els busos hi passen per sobre.
 */
object CapaTrams {
    private const val FONT = "trams"
    const val CAPA = "trams-linies"

    /** Els trams van per sota del punt del jugador i dels vehicles, que s'afegeixen després. */
    private const val CAPA_DE_SOTA = "posicio-precisio"

    /** Els colors de cada estat, en el tema clar i en el fosc. No són colors de cap línia de metro. */
    fun colors(fosc: Boolean): Map<EstatTram, String> = mapOf(
        EstatTram.NO_ET_LA_POTS_PERMETRE to if (fosc) "#5C6067" else "#A9ADB3",
        EstatTram.ES_POT_COMPRAR to if (fosc) "#3DD47A" else "#1E9E50",
        EstatTram.TEU to if (fosc) "#5B8CFF" else "#2457E6",
    )

    fun afegeix(estil: Style, fosc: Boolean) {
        estil.addSource(GeoJsonSource(FONT, FeatureCollection.fromFeatures(emptyList())))
        val colors = colors(fosc)
        val estat = Expression.get("estat")
        val capa = LineLayer(CAPA, FONT).withProperties(
            PropertyFactory.lineColor(
                Expression.match(
                    estat,
                    Expression.literal(colors.getValue(EstatTram.NO_ET_LA_POTS_PERMETRE)),
                    Expression.stop(EstatTram.ES_POT_COMPRAR.name, colors.getValue(EstatTram.ES_POT_COMPRAR)),
                    Expression.stop(EstatTram.TEU.name, colors.getValue(EstatTram.TEU)),
                ),
            ),
            // Més gruixuts de prop; els que es poden comprar i els teus, una mica més que els grisos.
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(7, Expression.switchCase(grisos(), Expression.literal(1f), Expression.literal(1.6f))),
                    Expression.stop(14, Expression.switchCase(grisos(), Expression.literal(3f), Expression.literal(5f))),
                ),
            ),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            // Els teus i els que pots comprar es dibuixen per sobre dels grisos.
            PropertyFactory.lineSortKey(Expression.get("ordre")),
        )
        val sota = estil.getLayer(CAPA_DE_SOTA)
        if (sota != null) estil.addLayerBelow(capa, CAPA_DE_SOTA) else estil.addLayer(capa)
    }

    private fun grisos() = Expression.eq(Expression.get("estat"), Expression.literal(EstatTram.NO_ET_LA_POTS_PERMETRE.name))

    /** Una línia per a cada traçat de cada tram, amb l'identificador del tram i el seu estat. */
    fun features(tracatsDeTram: Map<String, List<Int>>, tracats: List<Tracat>, estats: Map<String, EstatTram>): FeatureCollection =
        FeatureCollection.fromFeatures(
            tracatsDeTram.flatMap { (id, indexos) ->
                val estat = estats[id] ?: EstatTram.NO_ET_LA_POTS_PERMETRE
                indexos.map { i ->
                    Feature.fromGeometry(LineString.fromLngLats(tracats[i].punts.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                        addStringProperty("id", id)
                        addStringProperty("estat", estat.name)
                        addNumberProperty("ordre", estat.ordinal)
                    }
                }
            },
        )

    fun mostra(estil: Style, trams: FeatureCollection) {
        estil.getSourceAs<GeoJsonSource>(FONT)?.setGeoJson(trams)
    }
}
