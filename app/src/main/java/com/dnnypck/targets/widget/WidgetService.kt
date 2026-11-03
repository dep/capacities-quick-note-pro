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

            if (!preferencesManager.hasRequiredSettings() || selectedSpaceId == null) {
                showToast("Please connect Capacities in the app first")
                return@launch
            }

            val result = CapacitiesRepository(preferencesManager.tokenStore)
                .sendNote(selectedSpaceId, noteText)

            result.fold(
                onSuccess = {
                    showToast("Note sent successfully!")
                },
                onFailure = { error ->
                    showToast("Failed to send: ${error.message}")
                }
            )
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
