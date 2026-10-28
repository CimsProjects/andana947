package cat.andana947.data.compte

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** El compte amb Supabase Auth, amb correu i contrasenya (docs/supabase.md). */
@Singleton
class ServeiCompteSupabase @Inject constructor(private val supabase: SupabaseClient) : ServeiCompte {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _estat = MutableStateFlow<EstatCompte>(EstatCompte.Carregant)
    override val estat: StateFlow<EstatCompte> = _estat.asStateFlow()

    init {
        scope.launch {
            supabase.auth.sessionStatus.collect { _estat.value = estatDe(it) }
        }
    }

    override suspend fun entra(correu: String, contrasenya: String) = ambErrors {
        supabase.auth.signInWith(Email) {
            email = correu.trim()
            password = contrasenya
        }
    }

    override suspend fun registra(correu: String, contrasenya: String): ResultatRegistre = ambErrors {
        supabase.auth.signUpWith(Email) {
            email = correu.trim()
            password = contrasenya
        }
        if (supabase.auth.currentSessionOrNull() != null) ResultatRegistre.FET else ResultatRegistre.CAL_CONFIRMAR_CORREU
    }

    override suspend fun reintenta() = ambErrors {
        supabase.auth.refreshCurrentSession()
    }

    override suspend fun surt() {
        try {
            supabase.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sense connexió no es pot avisar el servidor, però la sessió es tanca igualment al mòbil.
            Log.w(TAG, "No s'ha pogut tancar la sessió al servidor", e)
            supabase.auth.clearSession()
        }
    }

    private inline fun <T> ambErrors(bloc: () -> T): T = try {
        bloc()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ExcepcioCompte(errorDe(e), e)
    }

    companion object {
        private const val TAG = "Compte"

        internal fun estatDe(status: SessionStatus): EstatCompte = when (status) {
            SessionStatus.Initializing -> EstatCompte.Carregant
            is SessionStatus.NotAuthenticated -> EstatCompte.SenseSessio
            is SessionStatus.RefreshFailure -> EstatCompte.SenseConnexio
            is SessionStatus.Authenticated -> {
                val usuari = status.session.user
                if (usuari == null) EstatCompte.SenseConnexio else EstatCompte.Llest(Compte(usuari.id, usuari.email))
            }
        }

        internal fun errorDe(e: Exception): ErrorCompte = when (e) {
            is ExcepcioCompte -> e.error
            is AuthRestException -> when (e.errorCode) {
                AuthErrorCode.InvalidCredentials -> ErrorCompte.CREDENCIALS_INCORRECTES
                AuthErrorCode.EmailNotConfirmed -> ErrorCompte.CORREU_NO_CONFIRMAT
                AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> ErrorCompte.CORREU_JA_REGISTRAT
                AuthErrorCode.EmailAddressInvalid -> ErrorCompte.CORREU_INVALID
                AuthErrorCode.WeakPassword -> ErrorCompte.CONTRASENYA_FEBLE
                AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> ErrorCompte.MASSA_INTENTS
                else -> ErrorCompte.DESCONEGUT
            }
            is IOException -> ErrorCompte.SENSE_CONNEXIO
            else -> ErrorCompte.DESCONEGUT
        }
    }
}
