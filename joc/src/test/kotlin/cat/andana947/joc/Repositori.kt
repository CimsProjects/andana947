package cat.andana947.joc

import cat.andana947.joc.config.ConfiguracioJoc
import java.io.File

/** Accés als fitxers reals del repositori des dels tests. */
object Repositori {
    val arrel: File = File(System.getProperty("arrel.repositori") ?: "..")

    val textConfiguracio: String by lazy { File(arrel, "dades/configuracio_joc.json").readText() }

    val config: ConfiguracioJoc by lazy { ConfiguracioJoc.llegeix(textConfiguracio) }
}
