package com.dnnypck.capacitiesquicknotepro

import com.dnnypck.capacitiesquicknotepro.data.network.NoteContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteContentTest {
    @Test
    fun detectsBareHttpAndHttpsUrls() {
        assertTrue(NoteContent.isUrlOnly("https://example.com"))
        assertTrue(NoteContent.isUrlOnly("http://example.com/path?q=1"))
        assertTrue(NoteContent.isUrlOnly("  https://capacities.io/notes  "))
    }

    @Test
    fun rejectsTextWithUrlsOrWhitespaceInside() {
        assertFalse(NoteContent.isUrlOnly("https://example.com and a note"))
        assertFalse(NoteContent.isUrlOnly("see https://example.com"))
        assertFalse(NoteContent.isUrlOnly("not a url"))
        assertFalse(NoteContent.isUrlOnly(""))
        assertFalse(NoteContent.isUrlOnly("ftp://example.com"))
    }
}
