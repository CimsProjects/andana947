package cat.andana947.aprop

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cat.andana947.R
import cat.andana947.ui.components.PantallaBuida

/** «A prop» (GDD, secció 7.1): la llista de parades i estacions per distància. Arriba amb les dades de la fase 2. */
@Composable
fun APropScreen() {
    PantallaBuida(
        titol = stringResource(R.string.a_prop_titol),
        text = stringResource(R.string.a_prop_buit),
        pista = stringResource(R.string.a_prop_pista),
    )
}
