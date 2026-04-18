package com.dnnypck.targets.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Space(
    val id: String,
    val nickname: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getDisplayName(): String {
        return nickname?.takeIf { it.isNotBlank() } ?: "${id.take(5)}..."
    }
}
