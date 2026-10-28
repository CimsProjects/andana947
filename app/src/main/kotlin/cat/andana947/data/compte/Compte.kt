package cat.andana947.data.compte

import kotlinx.coroutines.flow.StateFlow

// Comptes d'usuari amb Supabase Auth (docs/abast.md, secció 4). El compte és obligatori: el joc necessita el servidor
// per a qualsevol acció econòmica, i l'app només deixa jugar quan l'estat és [EstatCompte.Llest].

/** L'usuari amb sessió. El perfil de joc (nom, saldo…) arribarà amb l'esquema del servidor, a la fase 4. */
data class Compte(val usuariId: String, val correu: String?)

sealed interface EstatCompte {
    /** Encara es llegeix la sessió desada. */
    data object Carregant : EstatCompte

    /** No hi ha cap sessió iniciada. */
    data object SenseSessio : EstatCompte

    /** La sessió ha caducat i no s'ha pogut renovar, normalment perquè no hi ha connexió. */
    data object SenseConnexio : EstatCompte

    data class Llest(val compte: Compte) : EstatCompte
}

enum class ErrorCompte {
    CREDENCIALS_INCORRECTES,
    CORREU_NO_CONFIRMAT,
    CORREU_JA_REGISTRAT,
    CORREU_INVALID,
    CONTRASENYA_FEBLE,
    MASSA_INTENTS,
    SENSE_CONNEXIO,
    DESCONEGUT,
}

class ExcepcioCompte(val error: ErrorCompte, causa: Throwable? = null) : Exception(error.name, causa)

enum class ResultatRegistre {
    /** El compte s'ha creat i ja hi ha sessió. */
    FET,

    /** Cal confirmar el correu amb l'enllaç que s'hi ha enviat (quan el projecte torni a tenir «Confirm email»). */
    CAL_CONFIRMAR_CORREU,
}

/** Totes les operacions llancen [ExcepcioCompte] si fallen. */
interface ServeiCompte {
    val estat: StateFlow<EstatCompte>

    suspend fun entra(correu: String, contrasenya: String)

    suspend fun registra(correu: String, contrasenya: String): ResultatRegistre

    /** Torna a provar de renovar la sessió (per exemple, quan torna la connexió). */
    suspend fun reintenta()

    suspend fun surt()
}

/** Una contrasenya ha de tenir com a mínim aquests caràcters (el mínim per defecte de Supabase). */
const val CONTRASENYA_MINIM = 6
