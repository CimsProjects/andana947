package cat.andana947.mapa

import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cat.andana947.data.preferencies.Camera
import cat.andana947.data.ubicacio.Posicio
import cat.andana947.joc.circulacio.Flota
import cat.andana947.joc.circulacio.VehicleAlMapa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.time.LocalDateTime
import java.time.ZoneId

/** El zoom quan el mapa va a la posició del jugador: s'hi veuen els carrers i les parades del voltant. */
private const val ZOOM_CARRER = 16.0

/**
 * El mapa de MapLibre dins de Compose.
 *
 * @param estil l'estil ja omplert amb els colors del tema (EstilMapa.genera). Si canvia, es torna a carregar.
 * @param centra cada vegada que canvia, la càmera va a [posicio].
 * @param animacions si la càmera s'hi mou volant o de cop («Reduir animacions»).
 * @param flota els trens que es mouen pel mapa (docs/vehicles.md), quan s'han carregat els horaris.
 * @param fosc si el tema és fosc: la vora dels trens és del color de la tinta.
 */
@Composable
fun VistaMapa(
    estil: String,
    cameraInicial: Camera,
    posicio: Posicio?,
    centra: Int,
    animacions: Boolean,
    flota: Flota?,
    fosc: Boolean,
    onCameraQuieta: (Camera) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val densitat = LocalDensity.current
    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    var estilCarregat by remember { mutableStateOf<Style?>(null) }
    val quieta by rememberUpdatedState(onCameraQuieta)

    // El cicle de vida del MapView segueix el de la pantalla, i en sortir-ne es destrueix.
    val cicle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(cicle, mapView) {
        val observador = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        cicle.addObserver(observador)
        onDispose {
            cicle.removeObserver(observador)
            val estat = cicle.currentState
            if (estat.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (estat.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(cameraInicial.latitud, cameraInicial.longitud))
                .zoom(cameraInicial.zoom)
                .build()
            m.uiSettings.apply {
                // El logotip de MapLibre no cal (llicència BSD); l'atribució d'OpenStreetMap sí (ODbL), i es queda.
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.START
                val marge = with(densitat) { 8.dp.roundToPx() }
                setAttributionMargins(marge, marge, marge, marge)
                isRotateGesturesEnabled = true
                // Inclinar el mapa amb dos dits fa veure els trens en 3D.
                isTiltGesturesEnabled = true
            }
            m.setMinZoomPreference(5.0)
            m.addOnCameraIdleListener {
                val c = m.cameraPosition
                val objectiu = c.target ?: return@addOnCameraIdleListener
                quieta(Camera(objectiu.latitude, objectiu.longitude, c.zoom))
            }
            mapa = m
        }
    }

    val tinta = EstilMapa.paleta(fosc).getValue("tinta")

    // L'estil, cada vegada que canvia el tema, amb les capes dels trens.
    LaunchedEffect(mapa, estil) {
        val m = mapa ?: return@LaunchedEffect
        estilCarregat = null
        m.setStyle(Style.Builder().fromJson(estil)) {
            CapaVehicles.afegeix(it, tinta)
            estilCarregat = it
        }
    }

    // Els trens: es calculen fora del fil principal i es dibuixen sovint de prop (perquè es vegin avançar) i cada
    // segon de lluny. Només els de la part del mapa que es veu.
    LaunchedEffect(mapa, estilCarregat, flota) {
        val m = mapa ?: return@LaunchedEffect
        val carregat = estilCarregat ?: return@LaunchedEffect
        val f = flota ?: return@LaunchedEffect
        while (isActive) {
            val de3d = m.cameraPosition.zoom >= CapaVehicles.ZOOM_3D
            val limits = m.projection.visibleRegion.latLngBounds
            val (punts, blocs) = withContext(Dispatchers.Default) {
                val vehicles = synchronized(f) { f.vehicles(LocalDateTime.now(ZONA), System.currentTimeMillis()) }
                CapaVehicles.features(vehicles.filter { it.dins(limits) }, f.circulacions, de3d, tinta)
            }
            if (!carregat.isFullyLoaded) break
            CapaVehicles.mostra(carregat, punts, blocs)
            delay(if (de3d) INTERVAL_3D_MS else INTERVAL_LLUNY_MS)
        }
    }

    // El punt del jugador i el seu cercle de precisió.
    LaunchedEffect(estilCarregat, posicio) {
        val font = estilCarregat?.getSourceAs<GeoJsonSource>(EstilMapa.FONT_POSICIO) ?: return@LaunchedEffect
        if (posicio == null) {
            font.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
        } else {
            val punt = Feature.fromGeometry(Point.fromLngLat(posicio.longitud, posicio.latitud))
            val precisio = if (posicio.precisio.isFinite() && posicio.precisio < MAX_PRECISIO_DIBUIXADA) posicio.precisio else 0.0
            punt.addNumberProperty("radi0", EstilMapa.radiAlZoom0(precisio, posicio.latitud))
            font.setGeoJson(punt)
        }
    }

    LaunchedEffect(mapa, centra) {
        val m = mapa ?: return@LaunchedEffect
        val p = posicio ?: return@LaunchedEffect
        if (centra == 0) return@LaunchedEffect
        val actualitzacio = CameraUpdateFactory.newLatLngZoom(LatLng(p.latitud, p.longitud), maxOf(m.cameraPosition.zoom, ZOOM_CARRER))
        if (animacions) m.animateCamera(actualitzacio, DURADA_VOL_MS) else m.moveCamera(actualitzacio)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val DURADA_VOL_MS = 900

/** Els horaris són en hora local de Catalunya, sigui on sigui el mòbil. */
private val ZONA: ZoneId = ZoneId.of("Europe/Madrid")
private const val INTERVAL_3D_MS = 150L
private const val INTERVAL_LLUNY_MS = 1_000L

/** Si el vehicle és dins dels [limits] de la pantalla, amb un marge perquè no apareguin de cop a la vora. */
private fun VehicleAlMapa.dins(limits: LatLngBounds): Boolean {
    val margeLat = (limits.latitudeNorth - limits.latitudeSouth) * 0.25
    val margeLon = (limits.longitudeEast - limits.longitudeWest) * 0.25
    return posicio.lat in limits.latitudeSouth - margeLat..limits.latitudeNorth + margeLat &&
        posicio.lon in limits.longitudeWest - margeLon..limits.longitudeEast + margeLon
}

/** Amb una precisió pitjor que aquesta, el cercle taparia el mapa i no diria res: només es dibuixa el punt. */
private const val MAX_PRECISIO_DIBUIXADA = 2_000.0
