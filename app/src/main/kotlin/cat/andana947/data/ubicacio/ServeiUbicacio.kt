package cat.andana947.data.ubicacio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * On és el jugador.
 *
 * @param precisio el radi de precisió en metres (GDD, secció 7.3: la condició del check-in és `distància − precisió ≤ radi`).
 * @param velocitat en m/s, si el GPS la dona.
 * @param hora en mil·lisegons des de l'època (UTC).
 */
data class Posicio(
    val latitud: Double,
    val longitud: Double,
    val precisio: Double,
    val velocitat: Double?,
    val hora: Long,
)

/** La ubicació de l'usuari. Només en primer pla, mentre es mira el mapa (mai en segon pla). */
interface ServeiUbicacio {
    fun tePermis(): Boolean

    /** La posició cada cop que canvia, mentre es recull el flux. Buit si no hi ha permís. */
    fun seguiment(): Flow<Posicio>
}

class ServeiUbicacioFused @Inject constructor(
    @ApplicationContext private val context: Context,
) : ServeiUbicacio {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun tePermis(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun seguiment(): Flow<Posicio> {
        if (!tePermis()) return emptyFlow()
        return callbackFlow {
            // Alta precisió: el jugador camina pel carrer buscant parades, i el radi d'una parada és de 60 m.
            val peticio = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(INTERVAL_MINIM_MS)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) {
                    r.lastLocation?.let { trySend(it.posicio()) }
                }
            }
            // La darrera posició coneguda, perquè el mapa no comenci buit mentre arriba la primera de veritat.
            try {
                client.lastLocation.await()?.let { trySend(it.posicio()) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            try {
                client.requestLocationUpdates(peticio, callback, Looper.getMainLooper()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                close()
            }
            awaitClose { client.removeLocationUpdates(callback) }
        }
    }

    private fun Location.posicio() = Posicio(
        latitud = latitude,
        longitud = longitude,
        precisio = if (hasAccuracy()) accuracy.toDouble() else Double.MAX_VALUE,
        velocitat = if (hasSpeed()) speed.toDouble() else null,
        hora = time,
    )

    private companion object {
        const val INTERVAL_MS = 5_000L
        const val INTERVAL_MINIM_MS = 2_000L
    }
}
