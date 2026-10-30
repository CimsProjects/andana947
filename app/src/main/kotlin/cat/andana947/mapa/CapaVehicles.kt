package cat.andana947.mapa

import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.Forma
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
 * Els trens al mapa (docs/vehicles.md): a qualsevol zoom, un tren del color de la línia amb vora de tinta, fet de
 * cotxes separats, amb morro als dos extrems i el vidre del davant fosc, que ocupa el seu tros de via i en segueix les
 * corbes. Vist des de dalt és la planta del tren; inclinant el mapa, en 3D. La mida s'adapta al zoom perquè es vegi
 * sempre igual a la pantalla, i de molt a prop no es fa mai més petit que una mida mínima en metres. Els trens amb
 * posició *estimada* (segons l'horari) són una mica transparents; els que tenen posició real, opacs. Els busos són
 * iguals, amb un sol cos i més petits.
 */
object CapaVehicles {
    private const val FONT = "vehicles"
    private const val CAPA_REALS = "vehicles-reals"
    private const val CAPA_ESTIMATS = "vehicles-estimats"
    private const val CAPA_VORA = "vehicles-vora"
    private const val CAPA_ALIENS = "vehicles-aliens"

    /** La propietat de cada peça amb l'identificador del seu vehicle, per saber quin s'ha tocat. */
    private const val PROPIETAT_VEHICLE = "vehicle"

    /** Les capes on es pot tocar un vehicle. */
    val capesTocables: Array<String> = arrayOf(CAPA_REALS, CAPA_ESTIMATS, CAPA_ALIENS, CAPA_VORA)

    /** El vehicle d'una peça tocada. */
    fun vehicleDe(feature: Feature): String? =
        if (feature.hasProperty(PROPIETAT_VEHICLE)) feature.getStringProperty(PROPIETAT_VEHICLE) else null

    /** Els vehicles van per sota del punt del jugador. */
    private const val CAPA_DE_SOTA = "posicio-precisio"

    /**
     * Les mides de dibuix de cada mode: llarg i ample en píxels de pantalla, el llarg mínim en metres quan el mapa
     * és molt a prop, i la forma (cotxes, morro). No són les d'un model de tren concret. L'ample és sempre
     * proporcional al llarg, i l'alçada, a l'ample. Per afegir un mode (el bus, per exemple), una entrada més.
     */
    private data class Mides(val llargPx: Double, val amplePx: Double, val llargMinim: Double, val forma: Forma)

    private val mides = mapOf(
        "metro" to Mides(34.0, 7.0, 100.0, Forma(cotxes = 5)),
        "fgc" to Mides(32.0, 7.0, 100.0, Forma(cotxes = 4)),
        "rodalies" to Mides(40.0, 8.0, 130.0, Forma(cotxes = 5)),
        "regional" to Mides(36.0, 8.0, 130.0, Forma(cotxes = 4)),
        // El tramvia és articulat: els cotxes gairebé es toquen i el morro és més rodó.
        "tram" to Mides(24.0, 6.0, 40.0, Forma(cotxes = 5, separacio = 0.03, morro = 0.7, puntaMorro = 0.7)),
        // El bus, més petit que el TRAM (n'hi ha molts més i els trens han de destacar): un sol cos, de front gairebé
        // pla amb les cantonades arrodonides.
        "bus" to Mides(18.0, 5.0, 25.0, Forma(cotxes = 1, morro = 0.25, puntaMorro = 0.85)),
    )
    private val midesPerDefecte = Mides(32.0, 7.0, 100.0, Forma(cotxes = 4))
    private const val ALT_PER_AMPLE = 0.8

    /** El vidre del davant és fosc en els dos temes, i sobresurt una mica del sostre. */
    private const val COLOR_VIDRE = "#1C1F24"
    private const val BASE_VIDRE = 0.55
    private const val ALT_VIDRE = 1.04

    /** Les finestres, a mitja alçada del costat, i el sostre, un 12 % per sobre del cos. */
    private const val BASE_FINESTRES = 0.48
    private const val ALT_FINESTRES = 0.8
    private const val ALT_SOSTRE = 1.12
    private const val CLAROR_SOSTRE = 0.35

    /** Metres per píxel al zoom 0 a l'equador, amb les teselles de 512 px de MapLibre (40.075.016,686 m / 512). */
    private const val METRES_PER_PIXEL_Z0 = 78_271.516_96

    fun metresPerPixel(zoom: Double, latitud: Double): Double = METRES_PER_PIXEL_Z0 * cos(latitud * PI / 180) / 2.0.pow(zoom)

    /**
     * Els metres de cada píxel de les mides de dibuix. De lluny (tot Catalunya), els trens es fan una mica més petits a
     * la pantalla, fins a menys de la meitat al zoom 7, perquè no tapin les línies; a partir del zoom 11, la mida sencera.
     */
    fun metresPerPixelDeDibuix(zoom: Double, latitud: Double): Double =
        metresPerPixel(zoom, latitud) * (ESCALA_LLUNY + (zoom - 7) * (1 - ESCALA_LLUNY) / 4).coerceIn(ESCALA_LLUNY, 1.0)

    private const val ESCALA_LLUNY = 0.45

    private val buida = FeatureCollection.fromFeatures(emptyList())

    /** Afegeix la font i les capes a l'estil acabat de carregar. [tinta] és el color de la vora dels trens. */
    fun afegeix(estil: Style, tinta: String) {
        estil.addSource(GeoJsonSource(FONT, buida))
        val color = Expression.toColor(Expression.get("color"))
        val real = Expression.eq(Expression.get("real"), Expression.literal(true))
        val meu = Expression.eq(Expression.get("meu"), Expression.literal(true))

        fun blocs(id: String, filtre: Expression, opacitat: Float) = FillExtrusionLayer(id, FONT).withProperties(
            PropertyFactory.fillExtrusionColor(color),
            PropertyFactory.fillExtrusionHeight(Expression.get("alt")),
            PropertyFactory.fillExtrusionBase(Expression.get("base")),
            PropertyFactory.fillExtrusionOpacity(opacitat),
            // Color pla, sense degradat: el tren es veu net, com la resta de l'estil.
            PropertyFactory.fillExtrusionVerticalGradient(false),
        ).withFilter(filtre)

        // La vora: des de dalt, el contorn de la planta del tren.
        val vora = LineLayer(CAPA_VORA, FONT).withProperties(
            PropertyFactory.lineColor(tinta),
            PropertyFactory.lineWidth(Expression.switchCase(real, Expression.literal(1.5f), Expression.literal(1f))),
            PropertyFactory.lineOpacity(Expression.switchCase(meu, Expression.literal(1f), Expression.literal(OPACITAT_ALIENS))),
            PropertyFactory.lineJoin("miter"),
        )

        // Els vehicles de les línies del jugador, una mica transparents perquè no tapin els trams; els altres, molt.
        val capes = listOf(
            blocs(CAPA_ALIENS, Expression.not(meu), OPACITAT_ALIENS),
            blocs(CAPA_ESTIMATS, Expression.all(meu, Expression.not(real)), OPACITAT_ESTIMATS),
            blocs(CAPA_REALS, Expression.all(meu, real), OPACITAT_REALS),
            vora,
        )
        val sota = estil.getLayer(CAPA_DE_SOTA)
        for (capa in capes) {
            if (sota != null) estil.addLayerBelow(capa, CAPA_DE_SOTA) else estil.addLayer(capa)
        }
    }

    /** Els vehicles com a GeoJSON, amb la mida per als [metresPerPixel] de dibuix del zoom actual ([metresPerPixelDeDibuix]). */
    fun features(
        vehicles: List<VehicleAlMapa>,
        circulacions: Circulacions,
        metresPerPixel: Double,
        tinta: String,
        /** Les línies del jugador (de color); la resta, de color [gris]. */
        liniesMeves: Set<Int> = emptySet(),
        gris: String = tinta,
        /** La part de la flota de cada línia (mode:codi) que és del jugador: aquests vehicles van del seu color. */
        flota: Map<String, Double> = emptyMap(),
        colorFlota: String? = null,
    ): FeatureCollection = FeatureCollection.fromFeatures(
        vehicles.flatMap { v ->
            val linia = v.linia?.let { circulacions.horaris.linies[it] }
            val delJugador = colorFlota != null && linia != null && esDelJugador(v.id, flota["${linia.mode}:${linia.codi}"] ?: 0.0)
            val m = mides[linia?.mode] ?: midesPerDefecte
            val llarg = max(m.llargPx * metresPerPixel, m.llargMinim)
            val ample = llarg * m.amplePx / m.llargPx
            val alt = ample * ALT_PER_AMPLE
            val planta = if (v.tracat != null && v.distancia != null) {
                Silueta.sobreLaVia(circulacions.tracats[v.tracat!!], v.distancia!!, llarg, ample, m.forma)
            } else {
                Silueta.recte(v.posicio, llarg, ample, m.forma)
            }
            val meu = delJugador || v.linia?.let { it in liniesMeves } == true
            val color = when {
                delJugador -> colorFlota!!
                meu -> colorDe(linia?.color, tinta)
                else -> gris
            }
            val cos = planta.cotxes.map { peca(v.id, it, color, v.real, meu, 0.0, alt) }
            if (!meu) {
                // Els vehicles grisos, simples: n'hi ha molts i no han de pesar.
                cos + peca(v.id, planta.vidre, gris, v.real, false, alt * BASE_VIDRE, alt * ALT_VIDRE)
            } else {
                val vidres = listOfNotNull(planta.vidre, planta.vidreDarrere)
                    .map { peca(v.id, it, COLOR_VIDRE, v.real, true, alt * BASE_VIDRE, alt * ALT_VIDRE) }
                // Les finestres, als costats (es veuen inclinant el mapa), i el sostre, més clar i una mica més alt.
                val finestres = planta.finestres.map { peca(v.id, it, COLOR_VIDRE, v.real, true, alt * BASE_FINESTRES, alt * ALT_FINESTRES) }
                val sostres = planta.sostres.map { peca(v.id, it, aclareix(color, CLAROR_SOSTRE), v.real, true, alt, alt * ALT_SOSTRE) }
                cos + finestres + vidres + sostres
            }
        },
    )

    private fun peca(vehicle: String, planta: List<Punt>, color: String, real: Boolean, meu: Boolean, base: Double, alt: Double) =
        Feature.fromGeometry(Polygon.fromLngLats(listOf(planta.map { it.aPoint() }))).apply {
            addStringProperty(PROPIETAT_VEHICLE, vehicle)
            addStringProperty("color", color)
            addBooleanProperty("real", real)
            addBooleanProperty("meu", meu)
            addNumberProperty("base", base)
            addNumberProperty("alt", alt)
        }

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

    /**
     * Si un vehicle és un dels que el jugador té llogats a la línia: sempre els mateixos per a la mateixa [part] (del
     * seu identificador), i tants com la part de la flota.
     */
    fun esDelJugador(vehicle: String, part: Double): Boolean =
        // El hash es barreja perquè els vehicles d'identificadors seguits no caiguin tots junts.
        part > 0.0 && ((vehicle.hashCode() * -0x61c88647) ushr 1) % 1000 < part * 1000

    /** El color `#RRGGBB` barrejat amb blanc en una proporció [fraccio] (0 = igual, 1 = blanc). */
    fun aclareix(hex: String, fraccio: Double): String {
        val net = hex.removePrefix("#").takeIf { it.length == 6 } ?: return hex
        val canals = (0 until 3).map { net.substring(it * 2, it * 2 + 2).toIntOrNull(16) ?: return hex }
        return "#" + canals.joinToString("") { c -> "%02X".format((c + (255 - c) * fraccio.coerceIn(0.0, 1.0)).toInt()) }
    }

    /** El color dels vehicles de les línies que no són del jugador: un gris que no crida l'atenció. */
    fun gris(fosc: Boolean): String = if (fosc) "#4A4A4A" else "#BDBDBD"

    private fun Punt.aPoint() = Point.fromLngLat(lon, lat)

    private const val OPACITAT_ESTIMATS = 0.75f
    private const val OPACITAT_REALS = 0.9f

    /** Els vehicles de les línies que no són del jugador: es veuen, però no destaquen ni tapen res. */
    private const val OPACITAT_ALIENS = 0.3f
    private const val LLUMINOSITAT_MAXIMA = 0.85
}
