package cat.andana947.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cat.andana947.R
import cat.andana947.data.joc.ServeiJoc
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorAvatarUiState(
    val avatar: Avatar = CatalegAvatar.inicial,
    val desant: Boolean = false,
    val error: String? = null,
    val desat: Boolean = false,
)

@HiltViewModel
class EditorAvatarViewModel @Inject constructor(private val joc: ServeiJoc) : ViewModel() {
    private val _estat = MutableStateFlow(EditorAvatarUiState())
    val estat: StateFlow<EditorAvatarUiState> = _estat.asStateFlow()

    init {
        viewModelScope.launch {
            joc.llegirPerfilJugador().onSuccess { p -> p.avatar?.let { a -> _estat.update { it.copy(avatar = a) } } }
        }
    }

    fun canvia(avatar: Avatar) = _estat.update { it.copy(avatar = avatar, error = null) }

    fun desa() {
        if (_estat.value.desant) return
        _estat.update { it.copy(desant = true, error = null) }
        viewModelScope.launch {
            joc.desaAvatar(_estat.value.avatar)
                .onSuccess { _estat.update { it.copy(desant = false, desat = true) } }
                .onFailure { err -> _estat.update { it.copy(desant = false, error = err.message ?: "") } }
        }
    }
}

/** Canviar l'avatar des del Perfil. */
@Composable
fun EditorAvatarScreen(onTorna: () -> Unit, viewModel: EditorAvatarViewModel = hiltViewModel()) {
    val estat by viewModel.estat.collectAsStateWithLifecycle()
    LaunchedEffect(estat.desat) { if (estat.desat) onTorna() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Fons)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onTorna) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.avatar_torna), tint = Colors.Text)
            }
            Text(
                stringResource(R.string.avatar_titol),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.Text,
            )
        }
        EditorAvatar(estat.avatar, viewModel::canvia, Modifier.weight(1f))
        estat.error?.let {
            Text(stringResource(R.string.avatar_error, it), color = Colors.Error, style = MaterialTheme.typography.bodySmall)
        }
        BotoPrincipal(
            text = stringResource(R.string.avatar_desa),
            onClick = viewModel::desa,
            carregant = estat.desant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}
