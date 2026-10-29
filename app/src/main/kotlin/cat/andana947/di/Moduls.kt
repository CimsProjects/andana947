package cat.andana947.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import cat.andana947.BuildConfig
import cat.andana947.data.compte.ServeiCompte
import cat.andana947.data.compte.ServeiCompteLocal
import cat.andana947.data.compte.ServeiCompteSupabase
import cat.andana947.data.joc.FontTrams
import cat.andana947.data.joc.FontTramsAssets
import cat.andana947.data.joc.ServeiJoc
import cat.andana947.data.joc.ServeiJocLocal
import cat.andana947.data.joc.ServeiJocSupabase
import cat.andana947.data.preferencies.PreferenciesMapa
import cat.andana947.data.preferencies.PreferenciesMapaDataStore
import cat.andana947.data.preferencies.PreferenciesVehicles
import cat.andana947.data.preferencies.PreferenciesVehiclesDataStore
import cat.andana947.data.ubicacio.ServeiUbicacio
import cat.andana947.data.ubicacio.ServeiUbicacioFused
import cat.andana947.data.vehicles.FontHoraris
import cat.andana947.data.vehicles.FontHorarisAssets
import cat.andana947.data.vehicles.ServeiVehiclesReals
import cat.andana947.data.vehicles.ServeiVehiclesRealsSupabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

private val Context.preferenciesDataStore: DataStore<Preferences> by preferencesDataStore("preferencies")

@Module
@InstallIn(SingletonComponent::class)
object ModulDades {
    @Provides
    @Singleton
    fun preferencies(@ApplicationContext context: Context): DataStore<Preferences> = context.preferenciesDataStore

    /**
     * El client de Supabase (docs/supabase.md), amb la URL i la clau pública de local.properties: l'autenticació i
     * les funcions del joc (Postgrest).
     */
    @Provides
    @Singleton
    fun supabase(): SupabaseClient = createSupabaseClient(
        // Sense servidor configurat, una adreça que no existeix: les crides fallen com si no hi hagués connexió.
        if (BuildConfig.SENSE_SERVIDOR) "https://sense-servidor.invalid" else BuildConfig.SUPABASE_URL,
        if (BuildConfig.SENSE_SERVIDOR) "sense-servidor" else BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            flowType = FlowType.PKCE
        }
        install(Postgrest)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModulServeis {
    @Binds
    @Singleton
    abstract fun ubicacio(s: ServeiUbicacioFused): ServeiUbicacio

    @Binds
    @Singleton
    abstract fun preferenciesMapa(p: PreferenciesMapaDataStore): PreferenciesMapa

    @Binds
    @Singleton
    abstract fun preferenciesVehicles(p: PreferenciesVehiclesDataStore): PreferenciesVehicles

    @Binds
    @Singleton
    abstract fun horaris(f: FontHorarisAssets): FontHoraris

    @Binds
    @Singleton
    abstract fun vehiclesReals(s: ServeiVehiclesRealsSupabase): ServeiVehiclesReals

    @Binds
    @Singleton
    abstract fun trams(f: FontTramsAssets): FontTrams
}

/** A part, perquè els tests el puguin substituir per un compte fals. */
@Module
@InstallIn(SingletonComponent::class)
object ModulCompte {
    /** Sense servidor configurat, un compte local per provar l'app igualment (app/LLEGEIX-ME.md). */
    @Provides
    @Singleton
    fun compte(supabase: dagger.Lazy<ServeiCompteSupabase>): ServeiCompte =
        if (BuildConfig.SENSE_SERVIDOR) ServeiCompteLocal() else supabase.get()

    /** Sense servidor, una partida de prova al mòbil, que no es desa. */
    @Provides
    @Singleton
    fun joc(supabase: dagger.Lazy<ServeiJocSupabase>, trams: FontTrams): ServeiJoc =
        if (BuildConfig.SENSE_SERVIDOR) ServeiJocLocal(trams) else supabase.get()
}
