package cat.andana947.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private val PERMISOS_UBICACIO = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Retorna una funció que demana el permís d'ubicació (només en el moment en què cal) i després crida [onResultat]
 * amb true si s'ha concedit (encara que sigui l'aproximat).
 */
@Composable
fun rememberPermisUbicacio(onResultat: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val resultat by rememberUpdatedState(onResultat)
    val llancador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        resultat(r.values.any { it })
    }
    return {
        val te = PERMISOS_UBICACIO.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (te) resultat(true) else llancador.launch(PERMISOS_UBICACIO)
    }
}

/** Obre la configuració de l'app al sistema, per quan el permís s'ha denegat per sempre. */
@Composable
fun rememberObreConfiguracio(): () -> Unit {
    val context = LocalContext.current
    return {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
