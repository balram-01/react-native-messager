package com.messager.receivers

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms
import android.util.Log
import com.android.mms.util.SendingProgressTokenManager.put
import com.facebook.react.bridge.WritableNativeMap
import com.messager.EventRepository
import com.messager.extensions.hasPermission
import com.messager.extensions.showErrorToast
import com.messager.extensions.toast
import com.messager.helpers.PERMISSION_WRITE_SMS
import com.messager.helpers.ensureBackgroundThread

class SmsStatusDeliveredReceiver : SendStatusReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != SMS_DELIVERED_ACTION) {
      Log.w("SmsStatusDeliveredReceiver", "Unexpected action: ${intent.action}")
      return
    }

    ensureBackgroundThread {
      if (!context.hasPermission(PERMISSION_WRITE_SMS)) {
        Log.w("SmsStatusDeliveredReceiver", "Missing WRITE_SMS permission")
        context.toast("Missing WRITE_SMS permission")
        return@ensureBackgroundThread
      }

      val messageUri = intent.data
      val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)
      val errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, NO_ERROR_CODE)

      if (messageUri == null) {
        Log.w("SmsStatusDeliveredReceiver", "No message URI provided")
        return@ensureBackgroundThread
      }

      val messageId = messageUri.lastPathSegment?.toLongOrNull() ?: -1L
      if (messageId == -1L) {
        Log.w("SmsStatusDeliveredReceiver", "Invalid message ID from URI: $messageUri")
        return@ensureBackgroundThread
      }
      val status = when (resultCode) {
        Activity.RESULT_OK -> Sms.STATUS_COMPLETE
        else -> Sms.STATUS_FAILED
      }
      val values = ContentValues().apply {
        when (resultCode) {
          Activity.RESULT_OK -> put(Sms.STATUS, 0) // STATUS_COMPLETE
          else -> put(Sms.STATUS, 64) // STATUS_FAILED
        }
      }

      try {
        val updated = context.contentResolver.update(
          Uri.parse("content://sms/sent"),
          values,
          "${Sms._ID} = ?",
          arrayOf(messageId.toString())
        )
        // Query content provider for full message details
        val cursor = context.contentResolver.query(
          messageUri,
          arrayOf(Sms.BODY, Sms.ADDRESS, Sms.DATE, Sms.READ, Sms.THREAD_ID, Sms.SUBSCRIPTION_ID, Sms.TYPE),
          null,
          null,
          null
        )
        val eventData = WritableNativeMap()
        cursor?.use {
          if (it.moveToFirst()) {
            eventData.apply {
              putInt("id", messageId.toInt())
              putString("body", it.getString(it.getColumnIndexOrThrow(Sms.BODY)))
              putInt("type", it.getInt(it.getColumnIndexOrThrow(Sms.TYPE)))
              putInt("status", status)
              putInt("date", (it.getLong(it.getColumnIndexOrThrow(Sms.DATE)) / 1000).toInt())
              putBoolean("read", it.getInt(it.getColumnIndexOrThrow(Sms.READ)) == 1)
              putInt("threadId", it.getLong(it.getColumnIndexOrThrow(Sms.THREAD_ID)).toInt())
              putBoolean("isMMS", false)
              putInt("subscriptionId", it.getInt(it.getColumnIndexOrThrow(Sms.SUBSCRIPTION_ID)))
              putBoolean("isScheduled", false)
              putString("senderPhotoUri", null)
              putString("senderName", null)
              putString("senderPhoneNumber", it.getString(it.getColumnIndexOrThrow(Sms.ADDRESS)))
              putString("messageUri", messageUri.toString())
              putInt("errorCode", errorCode)
            }
          } else {
            // Fallback if query fails
            eventData.apply {
              putInt("id", messageId.toInt())
              putString("body", "")
              putInt("type", Sms.MESSAGE_TYPE_SENT)
              putInt("status", status)
              putInt("date", (System.currentTimeMillis() / 1000).toInt())
              putBoolean("read", true)
              putInt("threadId", -1)
              putBoolean("isMMS", false)
              putInt("subscriptionId", subId)
              putBoolean("isScheduled", false)
              putString("senderPhotoUri", null)
              putString("senderName", null)
              putString("senderPhoneNumber", "")
              putString("messageUri", messageUri.toString())
              putInt("errorCode", errorCode)
            }
          }
        }

        // Emit event to React Native
        if (EventRepository.isInitialized()) {
          EventRepository.emitEvent("onSmsDeliveryStatus", eventData)
        } else {
          Log.w("SmsStatusDeliveredReceiver", "EventRepository not initialized")
        }
      } catch (e: Exception) {
        Log.e("MessageStatus", "Error updating SMS delivery status: ${e.message}")
        context.showErrorToast(e)
      }
    }
  }
}
