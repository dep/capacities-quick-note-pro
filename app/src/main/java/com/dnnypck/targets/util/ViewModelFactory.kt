package com.dnnypck.capacitiesquicknotepro.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dnnypck.capacitiesquicknotepro.ui.main.MainViewModel
import com.dnnypck.capacitiesquicknotepro.ui.settings.SettingsViewModel

class ViewModelFactory(
    private val preferencesManager: PreferencesManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(preferencesManager) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(preferencesManager) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
