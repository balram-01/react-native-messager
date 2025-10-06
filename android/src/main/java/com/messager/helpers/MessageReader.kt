package com.messager.helpers

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.ActivityCompat
import com.messager.extensions.deleteConversation
import com.messager.extensions.deleteMessage
import com.messager.extensions.getConversations
import com.messager.extensions.getConversationsByPhoneNumber
import com.messager.extensions.getMessages
import com.messager.extensions.getPermissionString
import com.messager.extensions.getUnreadMessagesCount
import com.messager.extensions.hasPermission
import com.messager.extensions.markAllConversationsAsRead
import com.messager.extensions.markConversationMessagesAsRead
import com.messager.extensions.markMessageAsRead
import com.messager.extensions.showErrorToast
import com.messager.extensions.toast
import com.messager.messaging.MessagingUtils
import com.messager.models.Message
import org.json.JSONArray
import org.json.JSONObject


class MessageReader(private val context:Context) {

  private val MAKE_DEFAULT_APP_REQUEST = 1001
  private var packageName:String= context.packageName
  private val GENERIC_PERM_HANDLER = 100
  private var isAskingPermissions = false
  private var actionOnPermission: ((granted: Boolean) -> Unit)? = null


  fun requestMessageRole(activity: Activity, callback: (success: Boolean) -> Unit) {
    if (isQPlus()) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
        if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
          askPermissions(activity, callback)
        } else {
          val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
          activity.startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
        }
      } else {
        callback(false)
        context.toast("SMS role not available on this device!")
      }
    } else {
      if (Telephony.Sms.getDefaultSmsPackage(context) == packageName) {
        askPermissions(activity, callback)
      } else {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
          putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
        activity.startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
      }
    }
  }

  private fun askPermissions(activity: Activity, callback: (success: Boolean) -> Unit) {
    handlePermission(PERMISSION_READ_SMS, activity) { readSmsGranted ->
      if (readSmsGranted) {
        handlePermission(PERMISSION_SEND_SMS, activity) { sendSmsGranted ->
          if (sendSmsGranted) {
            handlePermission(PERMISSION_READ_CONTACTS, activity) { readContactsGranted ->
              handleNotificationPermission(activity) { notificationGranted ->
                if (readContactsGranted && notificationGranted) {
                  callback(true)
                } else {
                  context.showErrorToast("Please grant all required permissions.")
                  callback(false)
                }
              }
            }
          } else {
            context.showErrorToast("Please grant SMS send permissions.")
            callback(false)
          }
        }
      } else {
        context.showErrorToast("Please grant SMS read permissions.")
        callback(false)
      }
    }
  }

  private fun handlePermission(permissionId: Int, activity: Activity, callback: (granted: Boolean) -> Unit) {
    if (context.hasPermission(permissionId)) {
      callback(true)
    } else {
      isAskingPermissions = true
      actionOnPermission = callback
      ActivityCompat.requestPermissions(activity, arrayOf(context.getPermissionString(permissionId)), GENERIC_PERM_HANDLER)
    }
  }

  private fun handleNotificationPermission(activity: Activity, callback: (granted: Boolean) -> Unit) {
    if (!isTiramisuPlus()) {
      callback(true)
    } else {
      handlePermission(PERMISSION_POST_NOTIFICATIONS, activity) { granted ->
        callback(granted)
      }
    }
  }

  fun getConversationsList(
    threadId: Double?,
    limit: Int = 20,
    offset: Int = 0
  ): JSONArray {
    val conversations = context.getConversations(threadId = threadId?.toLong(), limit, offset)
    val jsonArray = JSONArray()

    for (conversation in conversations) {
      jsonArray.put(JSONObject().apply {
        put("threadId", conversation.threadId)
        put("snippet", conversation.snippet)
        put("date", conversation.date)
        put("read", conversation.read)
        put("isScheduled", conversation.isScheduled)
        put("usesCustomTitle", conversation.usesCustomTitle)
        put("isArchived", conversation.isArchived)
        put("senderName", conversation.senderName) // Added sender name
        put("senderPhoto", conversation.senderPhoto) // Added profile photo
        put("unreadCount", conversation.unreadCount) // Added unread count
        put("phoneNumber", conversation.phoneNumber)
      })
    }

    return jsonArray
  }
  fun getConversationsByPhoneNumber(
    phoneNumber: String,
    limit: Int = 20,
    offset: Int = 0
  ): JSONArray {
    val conversations = context.getConversationsByPhoneNumber(phoneNumber, limit, offset)
    val jsonArray = JSONArray()

    for (conversation in conversations) {
      jsonArray.put(JSONObject().apply {
        put("threadId", conversation.threadId)
        put("snippet", conversation.snippet)
        put("date", conversation.date)
        put("read", conversation.read)
        put("isScheduled", conversation.isScheduled)
        put("usesCustomTitle", conversation.usesCustomTitle)
        put("isArchived", conversation.isArchived)
        put("senderName", conversation.senderName)
        put("senderPhoto", conversation.senderPhoto)
        put("unreadCount", conversation.unreadCount)
        put("phoneNumber", conversation.phoneNumber)
      })
    }

    return jsonArray
  }
  fun getMessagesList(
    threadId: Long,
    getImageResolutions: Boolean = false,
    dateFrom: Long = -1L,
    includeScheduledMessages: Boolean = true,
    limit: Int = MESSAGES_LIMIT,
    offset: Int = 0
  ): JSONArray {
    // Call the optimized getMessages function
    val messages = context.getMessages(
      threadId = threadId,
      getImageResolutions = getImageResolutions,
      dateFrom = dateFrom,
      includeScheduledMessages = includeScheduledMessages,
      limit = limit,
      offset = offset
    )

    // Since getMessages already returns a JSONArray, we can return it directly
    // However, for consistency with getConversationsList and potential future modifications,
    // we'll create a new JSONArray and copy the data

    val jsonArray = JSONArray()

    try {
      for (i in 0 until messages.length()) {
        val message = messages.getJSONObject(i)
        jsonArray.put(JSONObject().apply {
          put("id", message.getLong("id"))
          put("body", message.getString("body"))
          put("type", message.getInt("type"))
          put("status", message.getInt("status"))
          put("date", message.getLong("date"))
          put("read", message.getBoolean("read"))
          put("threadId", message.getLong("threadId"))
          put("isMMS", message.getBoolean("isMMS"))
          put("subscriptionId", message.getInt("subscriptionId"))
          put("isScheduled", message.getBoolean("isScheduled"))
          put("senderPhotoUri", message.getString("senderPhotoUri"))
          put("senderName", message.getString("senderName"))
          put("senderPhoneNumber", message.getString("senderPhoneNumber"))

          // Add attachments if present (for MMS)
          if (message.has("attachments")) {
            put("attachments", message.getJSONArray("attachments"))
          }
        })
      }
    } catch (e: Exception) {
      Log.e("GetMessagesList", "Error processing messages: ${e.message}")

    }

    return jsonArray
  }
  fun getAllMessages(
    threadId:Double?,
    limit:Double,
    offset:Double,
    callback: (JSONArray) -> Unit,
  ){
    ensureBackgroundThread{
      val conversations = context.getConversations(threadId = threadId?.toLong(),limit.toInt(),offset.toInt())
      val jsons = JSONArray()
      conversations.map { it.threadId }.forEach { threadId ->
        val messages = context.getMessages(
          threadId,
          getImageResolutions = false,
          includeScheduledMessages = false
        )
        jsons.put(messages)
      }
      jsons.put(conversations)
      callback(jsons)
    }
  }


  fun sendSmsMessage(
    text: String,
    addresses: List<String>,
    subId: Int,
    requireDeliveryReport: Boolean,
  ): List<Message> {
    return try {
      val messagingUtils = MessagingUtils(context)
      val addressSet = addresses.toSet()
      messagingUtils.sendSmsMessage(
        text = text,
        addresses = addressSet,
        subId = subId,
        requireDeliveryReport = requireDeliveryReport
      )
    } catch (e: Exception) {
      Log.e("sendSmsMessage", "Error sending SMS: ${e.message}", e)
      throw e
    }
  }
  // New method to mark all messages in a conversation as read
  fun markConversationAsRead(threadId: Long, callback: (success: Boolean) -> Unit) {
    val success = context.markConversationMessagesAsRead(threadId)
    callback(success)
  }

  // New method to mark a specific message as read
  fun markMessageAsRead(threadId: Long, messageId: Long, isMMS: Boolean = false, callback: (success: Boolean) -> Unit) {
    val success = context.markMessageAsRead(threadId, messageId, isMMS)
    callback(success)
  }

  // New method to delete a conversation
  fun deleteConversation(threadId: Long, callback: (success: Boolean) -> Unit) {
    val success = context.deleteConversation(threadId)
    callback(success)
  }

  // New method to delete a specific message
  fun deleteMessage(threadId: Long, messageId: Long, isMMS: Boolean = false, callback: (success: Boolean) -> Unit) {
    val success = context.deleteMessage(threadId, messageId, isMMS)
    callback(success)
  }
  // New method to get unread messages count using the existing Context extension
  fun getUnreadMessagesCount(threadId: Long? = null): Int {
    return context.getUnreadMessagesCount(threadId)
  }
  // New method to mark all messages in all conversations as read
    fun markAllConversationsAsRead(callback: (success: Boolean) -> Unit) {
        val success = context.markAllConversationsAsRead()
        callback(success)
    }
}
