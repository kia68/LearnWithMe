package de.optadata.odil.learnwithme.content.internal.dedup

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ContentHasherTest {

    @Test
    fun `known SHA-256 test vector`() {
        // FIPS 180-2 Testvektor: sha256("abc")
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val actual = ContentHasher.sha256("abc".toByteArray()).joinToString("") { "%02x".format(it) }
        actual shouldBe expected
    }

    @Test
    fun `identical bytes hash identically, different bytes hash differently`() {
        val a = ContentHasher.sha256("hello".toByteArray())
        val b = ContentHasher.sha256("hello".toByteArray())
        val c = ContentHasher.sha256("world".toByteArray())

        a.contentEquals(b) shouldBe true
        a.contentEquals(c) shouldBe false
    }
}
