package com.mimo.remote.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ConnectionService.stop(context)
    }
}
