package cat.andana947.compte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.andana947.R
import cat.andana947.data.compte.CONTRASENYA_MINIM
import cat.andana947.data.compte.ErrorCompte
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.components.Marca
import cat.andana947.ui.theme.Colors

/** Entrar o crear un compte amb el correu. */
@Composable
fun CompteScreen(viewModel: CompteViewModel = hiltViewModel()) {
    val estat by viewModel.estat.collectAsStateWithLifecycle()
    val registra = estat.mode == ModeCompte.REGISTRA
    PantallaCompte(
        titol = stringResource(if (registra) R.string.compte_titol_registra else R.string.compte_titol_entra),
        text = stringResource(R.string.compte_text),
        error = estat.error,
    ) {
        Camp(
            valor = estat.correu,
            onCanvi = viewModel::canviaCorreu,
            etiqueta = stringResource(R.string.compte_correu),
            tipus = KeyboardType.Email,
            accio = ImeAction.Next,
            onAccio = {},
        )
        Camp(
            valor = estat.contrasenya,
            onCanvi = viewModel::canviaContrasenya,
            etiqueta = stringResource(R.string.compte_contrasenya),
            tipus = KeyboardType.Password,
            accio = if (registra) ImeAction.Next else ImeAction.Done,
            onAccio = { if (!registra) viewModel.envia() },
            ajuda = if (registra) stringResource(R.string.compte_contrasenya_ajuda, CONTRASENYA_MINIM) else null,
        )
        if (registra) {
            Camp(
                valor = estat.repeticio,
                onCanvi = viewModel::canviaRepeticio,
                etiqueta = stringResource(R.string.compte_repeteix_contrasenya),
                tipus = KeyboardType.Password,
                accio = ImeAction.Done,
                onAccio = viewModel::envia,
                ajuda = if (estat.contrasenyesDiferents) stringResource(R.string.compte_contrasenyes_diferents) else null,
                esError = estat.contrasenyesDiferents,
            )
        }
        BotoPrincipal(
            text = stringResource(if (registra) R.string.compte_registra else R.string.compte_entra),
            onClick = viewModel::envia,
            enabled = estat.potEnviar,
            carregant = estat.treballant,
        )
        if (registra) {
            Enllac(stringResource(R.string.compte_ja_tinc_compte)) { viewModel.canviaMode(ModeCompte.ENTRA) }
        } else {
            Enllac(stringResource(R.string.compte_vull_registrar)) { viewModel.canviaMode(ModeCompte.REGISTRA) }
        }
    }

    if (estat.calConfirmar) {
        AlertDialog(
            onDismissRequest = viewModel::tancaAvis,
            containerColor = Colors.Superficie,
            title = { Text(stringResource(R.string.compte_mira_correu)) },
            text = { Text(stringResource(R.string.compte_avis_confirma, estat.correu.trim()), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = viewModel::tancaAvis) { Text(stringResource(R.string.entesos)) } },
        )
    }
}

/** Hi ha sessió, però ha caducat i no s'ha pogut renovar sense connexió. */
@Composable
fun SenseConnexioScreen(viewModel: CompteViewModel = hiltViewModel()) {
    val estat by viewModel.estat.collectAsStateWithLifecycle()
    PantallaCompte(
        titol = stringResource(R.string.compte_titol_sense_connexio),
        text = stringResource(R.string.compte_text_sense_connexio),
        error = estat.error,
    ) {
        BotoPrincipal(stringResource(R.string.torna_a_provar), viewModel::reintenta, carregant = estat.treballant)
        BotoSecundari(stringResource(R.string.compte_surt), viewModel::surt, enabled = !estat.treballant)
    }
}

@Composable
private fun PantallaCompte(
    titol: String,
    text: String,
    error: ErrorCompte?,
    contingut: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Marca()
        Text(titol, style = MaterialTheme.typography.displayLarge, modifier = Modifier.semantics { heading() })
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(color = Colors.TextSecundari))
        if (error != null) {
            Text(textError(error), style = MaterialTheme.typography.bodyMedium.copy(color = Colors.Error))
        }
        contingut()
    }
}

@Composable
private fun Camp(
    valor: String,
    onCanvi: (String) -> Unit,
    etiqueta: String,
    tipus: KeyboardType,
    accio: ImeAction,
    onAccio: () -> Unit,
    ajuda: String? = null,
    esError: Boolean = false,
) {
    val esContrasenya = tipus == KeyboardType.Password
    OutlinedTextField(
        value = valor,
        onValueChange = onCanvi,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(etiqueta) },
        supportingText = ajuda?.let { { Text(it) } },
        isError = esError,
        singleLine = true,
        visualTransformation = if (esContrasenya) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = tipus,
            imeAction = accio,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onAccio() }),
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Colors.FortText,
            unfocusedBorderColor = Colors.Linia,
            focusedContainerColor = Colors.Superficie,
            unfocusedContainerColor = Colors.Superficie,
            focusedLabelColor = Colors.FortText,
            cursorColor = Colors.FortText,
        ),
    )
}

@Composable
private fun Enllac(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(color = Colors.Text))
    }
}

@Composable
private fun textError(error: ErrorCompte): String = stringResource(
    when (error) {
        ErrorCompte.CREDENCIALS_INCORRECTES -> R.string.compte_error_credencials
        ErrorCompte.CORREU_NO_CONFIRMAT -> R.string.compte_error_no_confirmat
        ErrorCompte.CORREU_JA_REGISTRAT -> R.string.compte_error_ja_registrat
        ErrorCompte.CORREU_INVALID -> R.string.compte_error_correu
        ErrorCompte.CONTRASENYA_FEBLE -> R.string.compte_error_contrasenya_feble
        ErrorCompte.MASSA_INTENTS -> R.string.compte_error_massa_intents
        ErrorCompte.SENSE_CONNEXIO -> R.string.compte_error_sense_connexio
        ErrorCompte.DESCONEGUT -> R.string.compte_error_desconegut
    },
)
