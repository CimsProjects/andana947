package cat.andana947

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

@HiltAndroidApp
class AndanaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre s'ha d'inicialitzar abans de crear cap mapa. Les teselles d'OpenFreeMap no demanen cap clau.
        MapLibre.getInstance(this)
    }
}
