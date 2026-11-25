package com.dnnypck.capacitiesquicknotepro.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.capacitiesquicknotepro.data.notes.SendCoordinator
import com.dnnypck.capacitiesquicknotepro.data.oauth.OAuthCoordinator
import com.dnnypck.capacitiesquicknotepro.data.repository.CapacitiesRepository
import com.dnnypck.capacitiesquicknotepro.ui.main.MainViewModel
import com.dnnypck.capacitiesquicknotepro.ui.notes.SavedNotesViewModel
import com.dnnypck.capacitiesquicknotepro.ui.settings.SettingsViewModel

class ViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val repository: CapacitiesRepository,
    private val oauthCoordinator: OAuthCoordinator,
    private val outbox: NoteOutbox,
    private val sendCoordinator: SendCoordinator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(preferencesManager, outbox, sendCoordinator) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(preferencesManager, oauthCoordinator) as T
            }
            modelClass.isAssignableFrom(SavedNotesViewModel::class.java) -> {
                SavedNotesViewModel(outbox, sendCoordinator) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
