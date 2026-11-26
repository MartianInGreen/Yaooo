package app.yaooo.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yaooo.AppHolder
import app.yaooo.ui.App

class MainActivity : ComponentActivity() {
    private var openWorkId by mutableStateOf<Long?>(null)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* nothing to do either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (application as YaoooApplication).permissionRequester = ::requestNotificationPermission
        openWorkId = workIdFrom(intent)
        if (savedInstanceState == null) requestNotificationPermission()
        setContent { App(AppHolder.container, openWorkId) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openWorkId = workIdFrom(intent)
    }

    private fun workIdFrom(i: Intent?): Long? = i?.getLongExtra("workId", -1L)?.takeIf { it > 0 }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
