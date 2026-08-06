package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.pref.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = UserPreferencesRepository.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val userPrefs = prefs.userPreferencesFlow.first()
                    if (userPrefs.autoReplyEnabled) {
                        Log.i("BootReceiver", "Boot completed & auto-reply enabled. Scheduling health worker.")
                        ListenerHealthWorker.schedule(context)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start service on boot: ${e.message}")
                }
            }
        }
    }
}
