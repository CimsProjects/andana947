package cat.andana947.data.compte

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El compte quan l'app no té servidor configurat (sense `supabase.url` a local.properties): per provar l'esquelet des
 * de l'Android Studio sense res més (app/LLEGEIX-ME.md). Es pot entrar amb qualsevol correu i contrasenya; no es desa
 * res i no es pot fer cap acció del joc.
 */
class ServeiCompteLocal : ServeiCompte {
    private val _estat = MutableStateFlow<EstatCompte>(EstatCompte.SenseSessio)
    override val estat: StateFlow<EstatCompte> = _estat.asStateFlow()

    override suspend fun entra(correu: String, contrasenya: String) {
        _estat.value = EstatCompte.Llest(Compte(ID_LOCAL, correu.trim()))
    }

    override suspend fun registra(correu: String, contrasenya: String): ResultatRegistre {
        entra(correu, contrasenya)
        return ResultatRegistre.FET
    }

    override suspend fun reintenta() = Unit

    override suspend fun surt() {
        _estat.value = EstatCompte.SenseSessio
    }

    companion object {
        const val ID_LOCAL = "00000000-0000-0000-0000-000000000000"
    }
}
