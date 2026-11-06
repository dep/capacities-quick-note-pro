package com.dnnypck.capacitiesquicknotepro.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.dnnypck.capacitiesquicknotepro.R
import com.dnnypck.capacitiesquicknotepro.data.notes.FileNoteStorage
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote
import com.dnnypck.capacitiesquicknotepro.data.notes.SendCoordinator
import com.dnnypck.capacitiesquicknotepro.data.repository.CapacitiesRepository
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WidgetService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service no longer needed as widget opens main app
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun sendNote(noteText: String) {
        serviceScope.launch {
            val preferencesManager = PreferencesManager(applicationContext)
            val selectedSpaceId = preferencesManager.getSelectedSpaceId()

            val outbox = NoteOutbox(FileNoteStorage.inAppFiles(applicationContext.filesDir))
            if (!preferencesManager.hasRequiredSettings() || selectedSpaceId == null) {
                outbox.recordDraft(noteText, selectedSpaceId.orEmpty(), null, SavedNote.SOURCE_WIDGET)
                showToast("Please connect Capacities in the app first. The note is in Saved notes.")
                return@launch
            }

            val coordinator = SendCoordinator(
                outbox,
                CapacitiesRepository(preferencesManager.tokenStore).asNoteSender()
            )
            val spaceName = preferencesManager.getSpaces().find { it.id == selectedSpaceId }?.getDisplayName()
            val result = coordinator.submit(noteText, selectedSpaceId, spaceName, SavedNote.SOURCE_WIDGET)
            showToast(result.message)
        }
    }

    private fun showToast(message: String) {
        launch(Dispatchers.Main) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launch(context: kotlin.coroutines.CoroutineContext, block: suspend CoroutineScope.() -> Unit) {
        serviceScope.launch(context, block = block)
    }
}
