package com.messager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.messager.EventRepository
import com.messager.helpers.THREAD_ID

class NotificationClickReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
    val sender = intent?.getStringExtra("sender")
    val body = intent?.getStringExtra("body")
    val threadId = intent?.getLongExtra("threadId", -1L)

    if (EventRepository.isInitialized()) {
      val messageMap = Arguments.createMap().apply {
        putString("sender", sender)
        putString("body", body)
        putDouble("threadId", threadId?.toDouble() ?: -1.0)
      }
      EventRepository.emitEvent("onNotificationClick", messageMap)

      context?.applicationContext?.packageManager?.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(THREAD_ID, threadId)
        context.startActivity(this) // Ensures context is valid
      } ?: run {
        Log.d("NotificationClickReceiver", "Launch intent is null, cannot open app")
      }

    }
  }
}
