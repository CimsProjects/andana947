package cat.andana947

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cat.andana947.navegacio.AppNavegacio
import cat.andana947.ui.theme.AndanaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Les barres del sistema segueixen el tema clar o fosc del mòbil, com l'app.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AndanaTheme {
                AppNavegacio()
            }
        }
    }
}
