package cat.andana947

import cat.andana947.avatar.Avatar
import cat.andana947.avatar.CatalegAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalegAvatarTest {
    @Test
    fun `els identificadors no es repeteixen`() {
        val ids = CatalegAvatar.peces.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `cada peça que es pot triar té nom i capes`() {
        for (p in CatalegAvatar.peces.filterNot { it.npc }) {
            assertNotNull(p.id, p.nom)
            assertTrue(p.id, p.capes.isNotEmpty())
        }
    }

    @Test
    fun `cada ranura té opcions`() {
        for (r in CatalegAvatar.ranures) assertTrue(r.id, CatalegAvatar.opcions(r.id).isNotEmpty())
    }

    @Test
    fun `el director i l'avatar inicial fan servir peces que existeixen`() {
        for (a in listOf(CatalegAvatar.director, CatalegAvatar.inicial)) {
            for (id in listOfNotNull(a.cabell, a.cap, a.tronc, a.coll, a.ulls, a.orelles, a.bigoti)) {
                assertNotNull(id, CatalegAvatar.peca(id))
            }
        }
    }

    @Test
    fun `les capes surten ordenades de darrere cap endavant`() {
        val z = CatalegAvatar.capes(CatalegAvatar.director, "cara_director").map { it.z }
        assertEquals(z.sorted(), z)
    }

    @Test
    fun `el cos tria la cara`() {
        val noia = CatalegAvatar.capes(Avatar(cos = "noia"))
        val cara = CatalegAvatar.peca("cara_noia")!!.capes
        assertTrue(noia.containsAll(cara))
    }

    @Test
    fun `canviar una ranura opcional la pot buidar però una d'obligatòria no`() {
        val a = Avatar(cap = "cap_casc")
        assertNull(a.amb("cap", null).cap)
        assertEquals("tronc_samarreta", a.amb("tronc", null).tronc)
        assertEquals("cap_llana", a.amb("cap", "cap_llana").a("cap"))
    }

    @Test
    fun `totes les peces i colors són a la migració del servidor`() {
        val sql = File("../supabase/migrations/20261030140000_avatar_introduccio.sql").readText()
        val ids = CatalegAvatar.peces.map { it.id } +
            CatalegAvatar.tonsPell.map { it.id } + CatalegAvatar.colorsCabell.map { it.id }
        for (id in ids) assertTrue("Falta $id a la migració", sql.contains("('$id',"))
        for (p in CatalegAvatar.peces) assertTrue(p.id, sql.contains("('${p.id}', '${p.ranura}', ${p.npc})"))
    }
}
