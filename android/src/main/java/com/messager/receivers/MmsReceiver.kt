package com.messager.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.klinker.android.send_message.MmsReceivedReceiver

class MmsReceiver: MmsReceivedReceiver() {

  override fun onMessageReceived(context: Context?, messageUri: Uri?) {
    println("Mms recieved $messageUri")
  }

  override fun onError(context: Context?, error: String?) {
    println("Mms error $error")
  }
}
