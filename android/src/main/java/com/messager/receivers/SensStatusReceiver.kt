package com.messager.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.messager.helpers.ensureBackgroundThread

abstract class SendStatusReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val resultCode = resultCode
    ensureBackgroundThread {
      print("Messsage recieved")
    }
  }

  companion object {
    const val SMS_SENT_ACTION = "com.connectapp.receiver.SMS_SENT"
    const val SMS_DELIVERED_ACTION = "com.connectapp.receiver.SMS_DELIVERED"
    const val EXTRA_ERROR_CODE = "errorCode"
    const val EXTRA_SUB_ID = "subId"

    const val NO_ERROR_CODE = -1
  }
}
