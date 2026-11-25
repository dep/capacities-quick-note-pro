package com.dnnypck.capacitiesquicknotepro.data.oauth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePreferences {
    const val FILE_NAME = "capacities_secure_prefs"
    const val FALLBACK_FILE_NAME = "capacities_secure_prefs_fallback"

    fun create(context: Context): SharedPreferences {
        return try {
            createEncrypted(context)
        } catch (_: Exception) {
            try {
                context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                createEncrypted(context)
            } catch (_: Exception) {
                context.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncrypted(context: Context): SharedPreferences {
        val keyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            FILE_NAME,
            keyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
