package cat.andana947.navegacio

import androidx.lifecycle.ViewModel
import cat.andana947.data.compte.EstatCompte
import cat.andana947.data.compte.ServeiCompte
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(compte: ServeiCompte) : ViewModel() {
    /** El compte és obligatori: només es juga quan és [EstatCompte.Llest]. */
    val compte: StateFlow<EstatCompte> = compte.estat
}
