package cat.andana947.temporada

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cat.andana947.R

/** El tipus i l'objectiu d'una fita a partir del seu identificador (`andanes_10`, `linia_completa_1`…). */
fun tipusIObjectiu(id: String): Pair<String, Int?> = when {
    id.startsWith("linia_completa_") -> "linies_completes" to id.substringAfterLast('_').toIntOrNull()
    else -> id.substringBeforeLast('_') to id.substringAfterLast('_').toIntOrNull()
}

/** El nom d'una fita per a la pantalla. */
@Composable
fun nomFita(id: String, tipus: String = tipusIObjectiu(id).first, objectiu: Int = tipusIObjectiu(id).second ?: 0): String =
    when {
        id == "comarques_totes" -> stringResource(R.string.fita_comarques_totes)
        id == "municipis_947" -> stringResource(R.string.fita_municipis_tots)
        id == "provincies_4" -> stringResource(R.string.fita_provincies_totes)
        tipus == "andanes" -> pluralStringResource(R.plurals.fita_andanes, objectiu, objectiu)
        tipus == "municipis" -> pluralStringResource(R.plurals.fita_municipis, objectiu, objectiu)
        tipus == "comarques" -> pluralStringResource(R.plurals.fita_comarques, objectiu, objectiu)
        tipus == "provincies" -> pluralStringResource(R.plurals.fita_provincies, objectiu, objectiu)
        tipus == "linies" -> pluralStringResource(R.plurals.fita_linies, objectiu, objectiu)
        tipus == "linies_completes" -> pluralStringResource(R.plurals.fita_linies_completes, objectiu, objectiu)
        tipus == "checkins" -> pluralStringResource(R.plurals.fita_checkins, objectiu, objectiu)
        else -> id
    }

/** El nom d'una skin de les andanes. */
@Composable
fun nomSkin(id: String): String = stringResource(
    when (id) {
        "blau_nit" -> R.string.skin_blau_nit
        "violeta" -> R.string.skin_violeta
        "taronja" -> R.string.skin_taronja
        "verd_via" -> R.string.skin_verd_via
        "bronze" -> R.string.skin_bronze
        "plata" -> R.string.skin_plata
        "or" -> R.string.skin_or
        else -> R.string.skin_magenta
    },
)
