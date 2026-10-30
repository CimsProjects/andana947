package cat.andana947.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.andana947.R
import cat.andana947.data.joc.ResultatCheckinRpc
import cat.andana947.imperi.euros
import cat.andana947.temporada.nomFita
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors

/** El resultat d'un check-in o d'una compra, amb les fites que s'hi han aconseguit. */
@Composable
fun DialegResultat(resultat: ResultatCheckinRpc, onTanca: () -> Unit) {
    val valid = resultat.status == "valid"
    AlertDialog(
        onDismissRequest = onTanca,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (valid) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (valid) Colors.Fort else Colors.Error,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (valid) R.string.resultat_fet else R.string.resultat_atencio),
                    fontFamily = Archivo,
                    fontWeight = FontWeight.Black,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(resultat.missatge, style = MaterialTheme.typography.bodyLarge, color = Colors.Text)
                resultat.saldo?.let {
                    Text(
                        stringResource(R.string.benvinguda_saldo, euros(it)),
                        style = MaterialTheme.typography.labelLarge,
                        color = Colors.TextSecundari,
                    )
                }
                for (fita in resultat.fites) {
                    Row(
                        Modifier.background(Colors.FortSuau).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Colors.Text, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.resultat_fita, nomFita(fita)),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Colors.Text,
                        )
                    }
                }
            }
        },
        confirmButton = { BotoPrincipal(text = stringResource(R.string.entesos), onClick = onTanca) },
        containerColor = Colors.Superficie,
        shape = MaterialTheme.shapes.medium,
    )
}
