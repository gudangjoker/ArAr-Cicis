package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.voice.CiciVoiceService
import com.example.ui.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.ReminderScheduler

class MainActivity : ComponentActivity() {

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure daily reminder alarm is active if enabled
        if (ReminderScheduler.isReminderEnabled(this)) {
            ReminderScheduler.scheduleDailyReminder(this)
        }

        // Check essential permissions on startup
        checkAndRequestPermissions()

        val openQuickAdd = intent?.getBooleanExtra("OPEN_QUICK_ADD", false) ?: false

        setContent {
            MyApplicationTheme {
                val viewModel: FinanceViewModel = viewModel(
                    factory = FinanceViewModel.provideFactory(application)
                )

                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScreen(
                        viewModel = viewModel,
                        initialOpenQuickAdd = openQuickAdd
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionRequestLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    /**
     * CRITICAL PRIVACY & SAFETY:
     * When the user closes the app, ensure any background voice service or microphone usage
     * is terminated immediately so the microphone does NOT linger in the background.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            CiciVoiceService.stopService(this)
        }
    }
}
