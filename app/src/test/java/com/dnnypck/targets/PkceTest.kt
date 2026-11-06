package com.dnnypck.capacitiesquicknotepro

import com.dnnypck.capacitiesquicknotepro.data.oauth.Base64Url
import com.dnnypck.capacitiesquicknotepro.data.oauth.Pkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PkceTest {
    @Test
    fun verifierIsUrlSafeAndLongEnough() {
        val pkce = Pkce.generate()
        assertTrue(pkce.verifier.length in 43..128)
        assertTrue(pkce.verifier.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertEquals("S256", pkce.method)
    }

    @Test
    fun challengeIsS256OfVerifier() {
        val pkce = Pkce.generate()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(pkce.verifier.toByteArray(Charsets.US_ASCII))
        assertEquals(Base64Url.encode(digest), pkce.challenge)
    }

    @Test
    fun generateProducesDistinctPairs() {
        val first = Pkce.generate()
        val second = Pkce.generate()
        assertNotEquals(first.verifier, second.verifier)
        assertNotEquals(first.challenge, second.challenge)
    }

    @Test
    fun base64UrlHasNoPadding() {
        assertEquals("YQ", Base64Url.encode(byteArrayOf('a'.code.toByte())))
        assertEquals("YWI", Base64Url.encode("ab".toByteArray(Charsets.US_ASCII)))
        assertEquals("YWJj", Base64Url.encode("abc".toByteArray(Charsets.US_ASCII)))
    }
}
