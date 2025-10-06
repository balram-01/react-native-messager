package com.messager.receivers

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.facebook.react.bridge.Arguments
import com.messager.EventRepository
import com.messager.extensions.getThreadId
import com.messager.extensions.showReceivedMessageNotification
import com.messager.helpers.ensureBackgroundThread

class SmsReceiver:BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {

    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
    var address = ""
    var body = ""
    var subject = ""
    var date = 0L
    var threadId = 0L
    var status = Telephony.Sms.STATUS_NONE
    val type = Telephony.Sms.MESSAGE_TYPE_INBOX
    val read = 0
    val subscriptionId = intent.getIntExtra("subscription", -1)
    ensureBackgroundThread {
      messages.forEach {
        address = it.originatingAddress ?: ""
        subject = it.pseudoSubject
        status = it.status
        body += it.messageBody
        date = System.currentTimeMillis()
        threadId = context.getThreadId(address)

      }
      handleMessage(
        context,
        address,
        subject,
        body,
        date,
        read,
        threadId,
        type,
        subscriptionId,
        status
      )
    }

  }

  private fun handleMessage(
    context: Context,
    address: String,
    subject: String,
    body: String,
    date: Long,
    read: Int,
    threadId: Long,
    type: Int,
    subscriptionId: Int,
    status: Int
  ) {
    // Save SMS to inbox
    saveSmsToInbox(context, address, body, date, read, threadId, subscriptionId, status)

    if(EventRepository.isInitialized()){
      val messageMap = Arguments.createMap().apply {
        putString("sender", address)
        putString("body", body)
        putDouble("threadId",threadId.toDouble())
      }
      EventRepository.emitEvent("onSmsReceived", messageMap)
    }
   
      context.showReceivedMessageNotification(1, address, body, threadId, null)

  }
  private fun saveSmsToInbox(
    context: Context,
    address: String,
    body: String,
    date: Long,
    read: Int,
    threadId: Long,
    subscriptionId: Int,
    status: Int
  ) {
    val values = ContentValues().apply {
      put(Telephony.Sms.ADDRESS, address)
      put(Telephony.Sms.BODY, body)
      put(Telephony.Sms.DATE, date)
      put(Telephony.Sms.READ, read)
      put(Telephony.Sms.THREAD_ID, threadId)
      put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX) // Inbox message
      put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
      put(Telephony.Sms.STATUS, status)
    }

    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
  }
  // Helper function to check if the app is running (foreground or background)
  // Check if the app is in the foreground
  private fun isAppInForeground(context: Context): Boolean {
    val packageName = context.packageName
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningProcesses = activityManager.runningAppProcesses ?: return false

    for (processInfo in runningProcesses) {
      if (processInfo.processName == packageName &&
        processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
        return true // App is in the foreground
      }
    }
    return false // App is either killed or in the background
  }
}
