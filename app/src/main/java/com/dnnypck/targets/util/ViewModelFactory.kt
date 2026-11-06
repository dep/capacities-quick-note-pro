package com.dnnypck.capacitiesquicknotepro.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dnnypck.capacitiesquicknotepro.data.oauth.OAuthCoordinator
import com.dnnypck.capacitiesquicknotepro.data.repository.CapacitiesRepository
import com.dnnypck.capacitiesquicknotepro.ui.main.MainViewModel
import com.dnnypck.capacitiesquicknotepro.ui.settings.SettingsViewModel

class ViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val repository: CapacitiesRepository,
    private val oauthCoordinator: OAuthCoordinator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(preferencesManager, repository) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(preferencesManager, oauthCoordinator) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
