package com.messager.messaging

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms
import com.facebook.react.bridge.WritableNativeMap
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.messager.EventRepository
import com.messager.extensions.getThreadId
import com.messager.extensions.smsSender
import com.messager.messaging.SmsException.Companion.ERROR_PERSISTING_MESSAGE
import com.messager.models.Message
class MessagingUtils(val context: Context) {

  private fun insertSmsMessage(
    subId: Int, dest: String, text: String, timestamp: Long, threadId: Long,
    status: Int = Sms.STATUS_NONE, type: Int = Sms.MESSAGE_TYPE_OUTBOX, messageId: Long? = null
  ): Uri {
    val response: Uri?
    val values = ContentValues().apply {
      put(Sms.ADDRESS, dest)
      put(Sms.DATE, timestamp)
      put(Sms.READ, 1)
      put(Sms.SEEN, 1)
      put(Sms.BODY, text)

      // insert subscription id only if it is a valid one.
      if (subId != Settings.DEFAULT_SUBSCRIPTION_ID) {
        put(Sms.SUBSCRIPTION_ID, subId)
      }

      if (status != Sms.STATUS_NONE) {
        put(Sms.STATUS, status)
      }
      if (type != Sms.MESSAGE_TYPE_ALL) {
        put(Sms.TYPE, type)
      }
      if (threadId != -1L) {
        put(Sms.THREAD_ID, threadId)
      }
    }

    try {
      if (messageId != null) {
        val selection = "${Sms._ID} = ?"
        val selectionArgs = arrayOf(messageId.toString())
        val count = context.contentResolver.update(Sms.CONTENT_URI, values, selection, selectionArgs)
        if (count > 0) {
          response = Uri.parse("${Sms.CONTENT_URI}/${messageId}")
        } else {
          response = null
        }
      } else {
        response = context.contentResolver.insert(Sms.CONTENT_URI, values)
      }
    } catch (e: Exception) {
      throw SmsException(ERROR_PERSISTING_MESSAGE, e)
    }
    return response ?: throw SmsException(ERROR_PERSISTING_MESSAGE)
  }

  fun sendSmsMessage(
    text: String,
    addresses: Set<String>,
    subId: Int,
    requireDeliveryReport: Boolean,
    messageId: Long? = null
  ): List<Message> {
    val sentMessages = mutableListOf<Message>()

    for (address in addresses) {
      val threadId = context.getThreadId(address)
      val timestamp = System.currentTimeMillis()
      val messageUri = insertSmsMessage(
        subId = subId,
        dest = address,
        text = text,
        timestamp = timestamp,
        threadId = threadId,
        messageId = messageId
      )

      try {
        context.smsSender.sendMessage(
          subId = subId,
          destination = address,
          body = text,
          serviceCenter = null,
          requireDeliveryReport = requireDeliveryReport,
          messageUri = messageUri
        )

        // Create a Message object for successful send
        val message = Message(
          id = messageId ?: timestamp, // Using timestamp as fallback if no messageId
          body = text,
          type = Sms.MESSAGE_TYPE_SENT,
          status = Sms.STATUS_COMPLETE,
          date = (timestamp / 1000).toInt(), // Convert to seconds as per Message class
          read = true,
          threadId = threadId,
          isMMS = false,
          subscriptionId = subId,
          isScheduled = false,
          senderPhotoUri = null,
          senderName = null,
          senderPhoneNumber = address
        )
        sentMessages.add(message)
// Emit event for queued message
        if (EventRepository.isInitialized()) {
          val eventData = WritableNativeMap().apply {
            putInt("id", (messageId ?: timestamp).toInt())
            putString("body", text)
            putInt("type", Sms.MESSAGE_TYPE_OUTBOX)
            putInt("status", Sms.STATUS_PENDING)
            putInt("date", (timestamp / 1000).toInt())
            putBoolean("read", true)
            putInt("threadId", threadId.toInt())
            putBoolean("isMMS", false)
            putInt("subscriptionId", subId)
            putBoolean("isScheduled", false)
            putString("senderPhotoUri", null)
            putString("senderName", null)
            putString("senderPhoneNumber", address)
            putString("messageUri", messageUri.toString())
            putInt("errorCode", 0)
          }
          EventRepository.emitEvent("onSmsQueued", eventData)
        }
      } catch (e: Exception) {
        // Create a Message object even for failed attempts with failure status
        val failedMessage = Message(
          id = messageId ?: timestamp,
          body = text,
          type = Sms.MESSAGE_TYPE_SENT,
          status =Sms.STATUS_FAILED,
          date = (timestamp / 1000).toInt(),
          read = true,
          threadId = threadId,
          isMMS = false,
          subscriptionId = subId,
          isScheduled = false,
          senderPhotoUri = null,
          senderName = null,
          senderPhoneNumber = address
        )
        sentMessages.add(failedMessage)
        // Emit event for immediate failure
        if (EventRepository.isInitialized()) {
          val eventData = WritableNativeMap().apply {
            putInt("id", (messageId ?: timestamp).toInt())
            putString("body", text)
            putInt("type", Sms.MESSAGE_TYPE_OUTBOX)
            putInt("status", Sms.STATUS_PENDING)
            putInt("date", (timestamp / 1000).toInt())
            putBoolean("read", true)
            putInt("threadId", threadId.toInt())
            putBoolean("isMMS", false)
            putInt("subscriptionId", subId)
            putBoolean("isScheduled", false)
            putString("senderPhotoUri", null)
            putString("senderName", null)
            putString("senderPhoneNumber", address)
            putString("messageUri", messageUri.toString())
            putInt("errorCode", 0)
          }
          EventRepository.emitEvent("onSmsQueued", eventData)
        }
        throw e // Still propagate error to caller as in original
      }
    }

    return sentMessages
  }
}
