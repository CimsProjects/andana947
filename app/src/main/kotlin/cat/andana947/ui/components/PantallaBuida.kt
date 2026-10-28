package cat.andana947.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.andana947.ui.theme.Caveat
import cat.andana947.ui.theme.Colors

/** Una pestanya que encara no té contingut: el títol, què hi haurà i una pista escrita a mà. */
@Composable
fun PantallaBuida(titol: String, text: String, pista: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(titol, style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(color = Colors.TextSecundari))
        Text(pista, fontFamily = Caveat, fontSize = 24.sp, color = Colors.FortText)
    }
}
