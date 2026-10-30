package cat.andana947.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cat.andana947.R
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Colors

/** Les pestanyes de l'editor: els dos colors i les ranures de peces del catàleg. */
private sealed interface Pestanya {
    val nom: Int

    data object Pell : Pestanya {
        override val nom = R.string.avatar_ranura_pell
    }

    data object ColorCabell : Pestanya {
        override val nom = R.string.avatar_ranura_color_cabell
    }

    data class Peces(val ranura: Ranura) : Pestanya {
        override val nom = ranura.nom
    }
}

private fun pestanyes(): List<Pestanya> {
    val peces = CatalegAvatar.ranures.map { Pestanya.Peces(it) }
    // Pell, Cabell, Color del cabell i la resta.
    return listOf(Pestanya.Pell, peces.first(), Pestanya.ColorCabell) + peces.drop(1)
}

/**
 * L'editor de l'avatar: el bust a dalt, el cos (noi o noia), les pestanyes i una graella amb cada opció dibuixada
 * sobre el mateix avatar. No desa res: avisa de cada canvi amb [onCanvi].
 */
@Composable
fun EditorAvatar(avatar: Avatar, onCanvi: (Avatar) -> Unit, modifier: Modifier = Modifier) {
    val totes = pestanyes()
    var triada by rememberSaveable { mutableIntStateOf(0) }
    val pestanya = totes[triada.coerceIn(totes.indices)]

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(176.dp)
                .ombraDura()
                .background(Colors.Superficie)
                .border(2.dp, Colors.Text),
        ) {
            val descripcio = stringResource(R.string.avatar_descripcio)
            DibuixAvatar(avatar, Modifier.fillMaxSize().padding(8.dp).semantics { contentDescription = descripcio })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Xip(stringResource(R.string.avatar_noi), avatar.cos == "noi", Modifier.weight(1f)) { onCanvi(avatar.copy(cos = "noi")) }
            Xip(stringResource(R.string.avatar_noia), avatar.cos == "noia", Modifier.weight(1f)) { onCanvi(avatar.copy(cos = "noia")) }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            totes.forEachIndexed { i, p -> Xip(stringResource(p.nom), i == triada) { triada = i } }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (pestanya) {
                Pestanya.Pell -> items(CatalegAvatar.tonsPell, key = { it.id }) { c ->
                    OpcioColor(c, avatar.pell == c.id) { onCanvi(avatar.copy(pell = c.id)) }
                }
                Pestanya.ColorCabell -> items(CatalegAvatar.colorsCabell, key = { it.id }) { c ->
                    OpcioColor(c, avatar.colorCabell == c.id) { onCanvi(avatar.copy(colorCabell = c.id)) }
                }
                is Pestanya.Peces -> {
                    val ranura = pestanya.ranura
                    if (!ranura.obligatoria) {
                        item(key = "res") {
                            OpcioPeca(avatar.amb(ranura.id, null), stringResource(R.string.avatar_res), avatar.a(ranura.id) == null) {
                                onCanvi(avatar.amb(ranura.id, null))
                            }
                        }
                    }
                    items(CatalegAvatar.opcions(ranura.id), key = { it.id }) { p ->
                        OpcioPeca(avatar.amb(ranura.id, p.id), p.nom?.let { stringResource(it) }.orEmpty(), avatar.a(ranura.id) == p.id) {
                            onCanvi(avatar.amb(ranura.id, p.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Xip(text: String, triat: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (triat) Colors.FortSuau else Colors.Fons)
            .border(if (triat) 2.dp else 1.5.dp, if (triat) Colors.Text else Colors.Linia)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = triat }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (triat) FontWeight.Bold else FontWeight.Medium),
            color = Colors.Text,
        )
    }
}

@Composable
private fun Casella(nom: String, triat: Boolean, onClick: () -> Unit, contingut: @Composable () -> Unit) {
    Column(
        Modifier
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = triat
                contentDescription = nom
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(if (triat) Colors.FortSuau else Colors.Superficie)
                .border(if (triat) 3.dp else 1.5.dp, if (triat) Colors.Fort else Colors.Linia),
            contentAlignment = Alignment.Center,
        ) { contingut() }
        Text(
            nom,
            style = MaterialTheme.typography.labelSmall,
            color = if (triat) Colors.Text else Colors.TextSecundari,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun OpcioPeca(avatar: Avatar, nom: String, triat: Boolean, onClick: () -> Unit) {
    Casella(nom, triat, onClick) { DibuixAvatar(avatar, Modifier.fillMaxSize().padding(4.dp)) }
}

@Composable
private fun OpcioColor(color: ColorAvatar, triat: Boolean, onClick: () -> Unit) {
    Casella(stringResource(color.nom), triat, onClick) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.color)
                .border(2.dp, Colors.Text, CircleShape),
        )
    }
}
