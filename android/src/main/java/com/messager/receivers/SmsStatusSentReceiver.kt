package com.messager.receivers

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import android.util.Log
import com.facebook.react.bridge.WritableNativeMap
import com.messager.EventRepository
import com.messager.extensions.hasPermission
import com.messager.extensions.showErrorToast
import com.messager.helpers.PERMISSION_WRITE_SMS
import com.messager.helpers.ensureBackgroundThread

class SmsStatusSentReceiver : SendStatusReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != SMS_SENT_ACTION) {
      return
    }
    ensureBackgroundThread {
      if (!context.hasPermission(PERMISSION_WRITE_SMS)) {
        return@ensureBackgroundThread
      }
      val messageUri = intent.data
      if (messageUri == null) {
        return@ensureBackgroundThread
      }
      val messageId = messageUri.lastPathSegment?.toLongOrNull() ?: -1L
      if (messageId == -1L) {
        return@ensureBackgroundThread
      }

      val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)
      val errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, NO_ERROR_CODE)
      val values = ContentValues().apply {
        when (resultCode) {
          Activity.RESULT_OK -> put(Sms.STATUS, 0) // STATUS_COMPLETE
          SmsManager.RESULT_ERROR_GENERIC_FAILURE -> put(Sms.STATUS, 64) // STATUS_FAILED
          SmsManager.RESULT_ERROR_NO_SERVICE -> put(Sms.STATUS, 64)
          SmsManager.RESULT_ERROR_NULL_PDU -> put(Sms.STATUS, 64)
          SmsManager.RESULT_ERROR_RADIO_OFF -> put(Sms.STATUS, 64)
          else -> put(Sms.STATUS, 64)
        }
        put(Sms.TYPE, 2) // TYPE_SENT
      }
      val (status, type) = when (resultCode) {
        Activity.RESULT_OK -> Pair(Sms.STATUS_COMPLETE, Sms.MESSAGE_TYPE_SENT)
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Pair(Sms.STATUS_FAILED, Sms.MESSAGE_TYPE_FAILED)
        SmsManager.RESULT_ERROR_NO_SERVICE -> Pair(Sms.STATUS_FAILED, Sms.MESSAGE_TYPE_FAILED)
        SmsManager.RESULT_ERROR_NULL_PDU -> Pair(Sms.STATUS_FAILED, Sms.MESSAGE_TYPE_FAILED)
        SmsManager.RESULT_ERROR_RADIO_OFF -> Pair(Sms.STATUS_FAILED, Sms.MESSAGE_TYPE_FAILED)
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> Pair(Sms.STATUS_FAILED, Sms.MESSAGE_TYPE_FAILED)
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> Pair(Sms.STATUS_FAILED, Sms.MESSAGE_TYPE_FAILED)
        else -> Pair(Sms.STATUS_FAILED, Sms.MESSAGE_TYPE_FAILED)
      }
      try {
        // Update in content://sms to handle outbox -> sent transition
        val updated = context.contentResolver.update(
          Uri.parse("content://sms"),
          values,
          "${Sms._ID} = ?",
          arrayOf(messageId.toString())
        )

        // Query content provider for full message details
        val cursor = context.contentResolver.query(
          messageUri,
          arrayOf(Sms.BODY, Sms.ADDRESS, Sms.DATE, Sms.READ, Sms.THREAD_ID, Sms.SUBSCRIPTION_ID),
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
              putInt("type", type)
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
              putInt("type", type)
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
          EventRepository.emitEvent("onSmsSendStatus", eventData)
        } else {
          Log.w("SmsStatusSentReceiver", "EventRepository not initialized")
        }
      } catch (e: Exception) {
        context.showErrorToast(e)
      }
    }
  }
}
