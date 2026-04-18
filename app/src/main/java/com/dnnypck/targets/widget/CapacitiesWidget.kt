package com.dnnypck.capacitiesquicknotepro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dnnypck.capacitiesquicknotepro.MainActivity
import com.dnnypck.capacitiesquicknotepro.R
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager

class CapacitiesWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    companion object {
        const val ACTION_QUICK_NOTE = "com.dnnypck.capacitiesquicknotepro.ACTION_QUICK_NOTE"
        const val ACTION_SELECT_SPACE = "com.dnnypck.capacitiesquicknotepro.ACTION_SELECT_SPACE"
        const val ACTION_OPEN_SETTINGS = "com.dnnypck.capacitiesquicknotepro.ACTION_OPEN_SETTINGS"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val preferencesManager = PreferencesManager(context)
            val spaces = preferencesManager.getSpaces()
            val selectedSpaceId = preferencesManager.getSelectedSpaceId()
            val selectedSpace = spaces.find { it.id == selectedSpaceId }

            // Construct the RemoteViews object
            val views = RemoteViews(context.packageName, R.layout.capacities_widget)

            // Update space selector text
            val spaceText = if (selectedSpace != null) {
                selectedSpace.getDisplayName()
            } else if (spaces.isEmpty()) {
                "No spaces - tap to configure"
            } else {
                "Tap to select space"
            }
            views.setTextViewText(R.id.widget_space_selector, spaceText)

            // Set up space selector click - opens space selection activity
            val selectSpaceIntent = Intent(context, WidgetSpaceSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val selectSpacePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 100,
                selectSpaceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_space_selector, selectSpacePendingIntent)

            // Set up EditText click - opens note input activity
            val noteInputIntent = Intent(context, WidgetNoteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val noteInputPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 100 + 1,
                noteInputIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_note_input, noteInputPendingIntent)

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
