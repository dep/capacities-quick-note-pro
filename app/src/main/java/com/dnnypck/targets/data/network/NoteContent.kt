package com.dnnypck.capacitiesquicknotepro.data.network

object NoteContent {
    private val urlOnly = Regex("^https?://\\S+$")

    fun isUrlOnly(text: String): Boolean {
        return urlOnly.matches(text.trim())
    }
}
