package com.dnnypck.capacitiesquicknotepro.data.oauth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dnnypck.capacitiesquicknotepro.MainActivity
import com.dnnypck.capacitiesquicknotepro.ui.theme.CapacitiesQuickNoteTheme
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager

class OAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferencesManager = PreferencesManager(applicationContext)
        val coordinator = OAuthCoordinator(preferencesManager)
        val redirectUri = intent?.data

        setContent {
            CapacitiesQuickNoteTheme {
                var status by remember { mutableStateOf("Connecting to Capacities…") }
                LaunchedEffect(redirectUri) {
                    val result = coordinator.completeAuthorization(redirectUri)
                    result.fold(
                        onSuccess = { space ->
                            status = "Connected to ${space.getDisplayName()}"
                            returnToApp(success = true, message = "Connected to ${space.getDisplayName()}")
                        },
                        onFailure = { error ->
                            status = error.message ?: "Sign-in failed"
                            Toast.makeText(
                                this@OAuthRedirectActivity,
                                error.message ?: "Couldn't complete Capacities sign-in",
                                Toast.LENGTH_LONG
                            ).show()
                            returnToApp(success = false, message = error.message)
                        }
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (status.startsWith("Connecting")) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    private fun returnToApp(success: Boolean, message: String?) {
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OAUTH_COMPLETED, true)
            putExtra(EXTRA_OAUTH_SUCCESS, success)
            putExtra(EXTRA_OAUTH_MESSAGE, message)
        }
        startActivity(launch)
        finish()
    }

    companion object {
        const val EXTRA_OAUTH_COMPLETED = "oauth_completed"
        const val EXTRA_OAUTH_SUCCESS = "oauth_success"
        const val EXTRA_OAUTH_MESSAGE = "oauth_message"
    }
}
