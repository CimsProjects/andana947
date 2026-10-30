package cat.andana947

import cat.andana947.compte.CompteEstat
import cat.andana947.compte.CompteViewModel
import cat.andana947.compte.ModeCompte
import cat.andana947.data.compte.Compte
import cat.andana947.data.compte.ErrorCompte
import cat.andana947.data.compte.EstatCompte
import cat.andana947.data.compte.ExcepcioCompte
import cat.andana947.data.compte.ResultatRegistre
import cat.andana947.data.compte.ServeiCompte
import cat.andana947.data.compte.ServeiCompteLocal
import cat.andana947.data.compte.ServeiCompteSupabase
import cat.andana947.data.preferencies.Camera
import cat.andana947.data.preferencies.PreferenciesMapa
import cat.andana947.data.ubicacio.Posicio
import cat.andana947.data.ubicacio.ServeiUbicacio
import cat.andana947.mapa.EstatPermis
import cat.andana947.mapa.MapaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Un compte en memòria. Si [error] no és null, totes les operacions fallen amb aquest error. */
class CompteEnMemoria : ServeiCompte {
    override val estat = MutableStateFlow<EstatCompte>(EstatCompte.SenseSessio)
    var error: ErrorCompte? = null
    var calConfirmar = false
    val crides = mutableListOf<String>()

    private fun crida(nom: String) {
        crides += nom
        error?.let { throw ExcepcioCompte(it) }
    }

    override suspend fun entra(correu: String, contrasenya: String) {
        crida("entra $correu")
        estat.value = EstatCompte.Llest(Compte("u1", correu))
    }

    override suspend fun registra(correu: String, contrasenya: String): ResultatRegistre {
        crida("registra $correu")
        return if (calConfirmar) ResultatRegistre.CAL_CONFIRMAR_CORREU else ResultatRegistre.FET
    }

    override suspend fun reintenta() = crida("reintenta")

    override suspend fun surt() {
        crida("surt")
        estat.value = EstatCompte.SenseSessio
    }
}

class UbicacioFalsa(var permis: Boolean) : ServeiUbicacio {
    val posicions = MutableSharedFlow<Posicio>()

    override fun tePermis() = permis

    override fun seguiment(): Flow<Posicio> = posicions
}

class PreferenciesEnMemoria(var camera: Camera? = null) : PreferenciesMapa {
    override suspend fun darreraCamera() = camera

    override suspend fun desaCamera(camera: Camera) {
        this.camera = camera
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
    @Before
    fun prepara() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun neteja() = Dispatchers.resetMain()

    @Test
    fun `el correu ha de tenir forma de correu`() {
        assertTrue(CompteEstat.esCorreu(" pol@exemple.cat "))
        assertFalse(CompteEstat.esCorreu("pol@exemple"))
        assertFalse(CompteEstat.esCorreu("pol exemple.cat"))
    }

    @Test
    fun `per crear el compte cal una contrasenya prou llarga i repetida`() {
        val base = CompteEstat(mode = ModeCompte.REGISTRA, correu = "a@b.cat")
        assertFalse(base.copy(contrasenya = "12345", repeticio = "12345").potEnviar)
        assertFalse(base.copy(contrasenya = "123456", repeticio = "123457").potEnviar)
        assertTrue(base.copy(contrasenya = "123456", repeticio = "123456").potEnviar)
        assertTrue(base.copy(contrasenya = "123456", repeticio = "1").contrasenyesDiferents)
    }

    @Test
    fun `entrar canvia l'estat del compte`() {
        val compte = CompteEnMemoria()
        val vm = CompteViewModel(compte)
        vm.canviaCorreu("pol@exemple.cat")
        vm.canviaContrasenya("secret")
        vm.envia()
        assertEquals(listOf("entra pol@exemple.cat"), compte.crides)
        assertTrue(compte.estat.value is EstatCompte.Llest)
        assertFalse(vm.estat.value.treballant)
    }

    @Test
    fun `un error es mostra i s'esborra en tornar a escriure`() {
        val compte = CompteEnMemoria().apply { error = ErrorCompte.CREDENCIALS_INCORRECTES }
        val vm = CompteViewModel(compte)
        vm.canviaCorreu("pol@exemple.cat")
        vm.canviaContrasenya("dolenta")
        vm.envia()
        assertEquals(ErrorCompte.CREDENCIALS_INCORRECTES, vm.estat.value.error)
        vm.canviaContrasenya("bona")
        assertNull(vm.estat.value.error)
    }

    @Test
    fun `si cal confirmar el correu, s'avisa`() {
        val compte = CompteEnMemoria().apply { calConfirmar = true }
        val vm = CompteViewModel(compte)
        vm.canviaMode(ModeCompte.REGISTRA)
        vm.canviaCorreu("pol@exemple.cat")
        vm.canviaContrasenya("123456")
        vm.canviaRepeticio("123456")
        vm.envia()
        assertTrue(vm.estat.value.calConfirmar)
        vm.tancaAvis()
        assertFalse(vm.estat.value.calConfirmar)
    }

    @Test
    fun `sense connexió, l'error és de connexió`() {
        assertEquals(ErrorCompte.SENSE_CONNEXIO, ServeiCompteSupabase.errorDe(IOException("sense xarxa")))
        assertEquals(ErrorCompte.DESCONEGUT, ServeiCompteSupabase.errorDe(IllegalStateException()))
    }

    @Test
    fun `el compte local entra i surt`() = runTest {
        val compte = ServeiCompteLocal()
        assertEquals(EstatCompte.SenseSessio, compte.estat.value)
        compte.entra(" prova@exemple.cat ", "x")
        assertEquals(EstatCompte.Llest(Compte(ServeiCompteLocal.ID_LOCAL, "prova@exemple.cat")), compte.estat.value)
        compte.surt()
        assertEquals(EstatCompte.SenseSessio, compte.estat.value)
    }

    @Test
    fun `el mapa s'obre a Catalunya la primera vegada i on es va deixar després`() {
        assertEquals(Camera.CATALUNYA, MapaViewModel(UbicacioFalsa(true), PreferenciesEnMemoria()).cameraInicial.value)
        val desada = Camera(41.3851, 2.1734, 15.0)
        assertEquals(desada, MapaViewModel(UbicacioFalsa(true), PreferenciesEnMemoria(desada)).cameraInicial.value)
        // Una càmera desada fora de Catalunya (d'abans del límit) torna a la de tot Catalunya.
        val madrid = Camera(40.4168, -3.7038, 12.0)
        assertEquals(Camera.CATALUNYA, MapaViewModel(UbicacioFalsa(true), PreferenciesEnMemoria(madrid)).cameraInicial.value)
    }

    @Test
    fun `el mapa va al jugador amb la primera posició i quan ho demana`() = runTest {
        val ubicacio = UbicacioFalsa(true)
        val vm = MapaViewModel(ubicacio, PreferenciesEnMemoria())
        val recollida = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.posicio.collect {} }
        assertEquals(0, vm.centra.value)
        vm.centraEnMi()
        assertEquals(0, vm.centra.value, "Sense posició no es pot centrar")
        val p = Posicio(41.3809, 2.1228, 12.0, 0.0, 0L)
        ubicacio.posicions.emit(p)
        assertEquals(p, vm.posicio.value)
        assertEquals(1, vm.centra.value)
        ubicacio.posicions.emit(p.copy(latitud = 41.3810))
        assertEquals(1, vm.centra.value, "Només es centra sol una vegada")
        vm.centraEnMi()
        assertEquals(2, vm.centra.value)
        recollida.cancel()
    }

    @Test
    fun `sense permís no hi ha posició i es pot tornar a revisar`() {
        val ubicacio = UbicacioFalsa(false)
        val vm = MapaViewModel(ubicacio, PreferenciesEnMemoria())
        assertEquals(EstatPermis.PENDENT, vm.permis.value)
        vm.resultatPermis(false)
        assertEquals(EstatPermis.DENEGAT, vm.permis.value)
        vm.revisaPermis()
        assertEquals(EstatPermis.DENEGAT, vm.permis.value)
        ubicacio.permis = true
        vm.revisaPermis()
        assertEquals(EstatPermis.CONCEDIT, vm.permis.value)
    }

    @Test
    fun `la càmera es desa`() {
        val preferencies = PreferenciesEnMemoria()
        val vm = MapaViewModel(UbicacioFalsa(true), preferencies)
        vm.desaCamera(Camera(41.0, 2.0, 12.0))
        assertEquals(Camera(41.0, 2.0, 12.0), preferencies.camera)
    }
}
