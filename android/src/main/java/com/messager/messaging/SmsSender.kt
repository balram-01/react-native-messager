package com.messager.messaging

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import com.messager.helpers.isSPlus
import com.messager.receivers.SendStatusReceiver
import com.messager.receivers.SmsStatusDeliveredReceiver
import com.messager.receivers.SmsStatusSentReceiver
import com.messager.messaging.SmsException.Companion.ERROR_SENDING_MESSAGE

class SmsSender(val app: Application) {

  private val TAG = "SmsSender"
  private val sendMultipartSmsAsSeparateMessages = false

  fun sendMessage(
    subId: Int, destination: String, body: String, serviceCenter: String?,
    requireDeliveryReport: Boolean, messageUri: Uri
  ) {
    Log.d(TAG, "Preparing to send message to $destination with subId=$subId")

    if (body.isEmpty()) {
      Log.e(TAG, "Attempted to send empty text message")
      throw IllegalArgumentException("SmsSender: empty text message")
    }

    val smsManager = getSmsManager(subId)
    val messages = smsManager.divideMessage(body)
    if (messages == null || messages.size < 1) {
      Log.e(TAG, "Failed to divide message into parts")
      throw SmsException(ERROR_SENDING_MESSAGE)
    }

    Log.d(TAG, "Message divided into ${messages.size} parts")

    sendInternal(
      subId, destination, messages, serviceCenter, requireDeliveryReport, messageUri
    )
  }

  private fun sendInternal(
    subId: Int, dest: String,
    messages: ArrayList<String>, serviceCenter: String?,
    requireDeliveryReport: Boolean, messageUri: Uri
  ) {
    Log.d(TAG, "Sending message to $dest with ${messages.size} parts")

    val smsManager = getSmsManager(subId)
    val messageCount = messages.size
    val deliveryIntents = ArrayList<PendingIntent?>(messageCount)
    val sentIntents = ArrayList<PendingIntent>(messageCount)

    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (isSPlus()) {
      flags = flags or PendingIntent.FLAG_MUTABLE
    }

    for (i in 0 until messageCount) {
      val partId = if (messageCount <= 1) 0 else i + 1
      if (requireDeliveryReport && i == messageCount - 1) {
        deliveryIntents.add(
          PendingIntent.getBroadcast(
            app,
            partId,
            getDeliveredStatusIntent(messageUri, subId),
            flags
          )
        )
        Log.d(TAG, "Added delivery intent for part $partId")
      } else {
        deliveryIntents.add(null)
      }

      sentIntents.add(
        PendingIntent.getBroadcast(
          app,
          partId,
          getSendStatusIntent(messageUri, subId),
          flags
        )
      )
      Log.d(TAG, "Added sent intent for part $partId")
    }

    try {
      if (sendMultipartSmsAsSeparateMessages) {
        Log.d(TAG, "Sending multipart SMS as separate messages")
        for (i in 0 until messageCount) {
          Log.d(TAG, "Sending part ${i + 1}/${messageCount}")
          smsManager.sendTextMessage(
            dest,
            serviceCenter,
            messages[i],
            sentIntents[i],
            deliveryIntents[i]
          )
        }
      } else {
        Log.d(TAG, "Sending multipart SMS using sendMultipartTextMessage")
        smsManager.sendMultipartTextMessage(
          dest, serviceCenter, messages, sentIntents, deliveryIntents
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Exception while sending SMS: ${e.message}", e)
      throw SmsException(ERROR_SENDING_MESSAGE, e)
    }
  }

  private fun getSendStatusIntent(requestUri: Uri, subId: Int): Intent {
    val intent = Intent(SendStatusReceiver.SMS_SENT_ACTION, requestUri, app, SmsStatusSentReceiver::class.java)
    intent.putExtra(SendStatusReceiver.EXTRA_SUB_ID, subId)
    Log.d(TAG, "Created send status intent for subId=$subId")
    return intent
  }

  private fun getDeliveredStatusIntent(requestUri: Uri, subId: Int): Intent {
    val intent = Intent(SendStatusReceiver.SMS_DELIVERED_ACTION, requestUri, app, SmsStatusDeliveredReceiver::class.java)
    intent.putExtra(SendStatusReceiver.EXTRA_SUB_ID, subId)
    Log.d(TAG, "Created delivery status intent for subId=$subId")
    return intent
  }

  companion object {
    private var instance: SmsSender? = null
    fun getInstance(app: Application): SmsSender {
      if (instance == null) {
        Log.d("SmsSender", "Creating new instance of SmsSender")
        instance = SmsSender(app)
      }
      return instance!!
    }
  }

  private fun getSmsManager(subId: Int): SmsManager {
    return SmsManager.getSmsManagerForSubscriptionId(subId)
  }
}
