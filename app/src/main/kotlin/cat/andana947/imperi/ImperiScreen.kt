package cat.andana947.imperi

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cat.andana947.R
import cat.andana947.ui.components.PantallaBuida

/** «Imperi» (GDD, secció 7.1): saldo, concessions i moviments. Les xifres arriben del servidor a la fase 4. */
@Composable
fun ImperiScreen() {
    PantallaBuida(
        titol = stringResource(R.string.imperi_titol),
        text = stringResource(R.string.imperi_buit),
        pista = stringResource(R.string.imperi_pista),
    )
}
