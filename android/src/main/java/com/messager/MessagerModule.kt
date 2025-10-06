package com.messager

import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.messager.extensions.toast
import com.messager.helpers.MessageReader
import com.messager.helpers.isQPlus
import org.json.JSONArray
import org.json.JSONObject
@ReactModule(name = MessagerModule.NAME)
class MessagerModule(reactContext: ReactApplicationContext) :
  NativeMessagerSpec(reactContext) {
  private val messageReader: MessageReader = MessageReader(reactContext)

  init {
    EventRepository.initialize(reactContext)
  }

  companion object {
    const val NAME = "Messager"
  }

  override fun getName(): String = NAME

  override fun multiply(a: Double, b: Double, promise: Promise) {
    promise.resolve(a * b)
  }

 
  override fun requestMessageRole(promise: Promise){
    try{
      val activity = currentActivity
      if (activity != null) {
        messageReader.requestMessageRole(activity, callback = { data->
          if(data){
            promise.resolve("Set to Default Messaging App!")
          }
          else {
            promise.reject("REQUEST_FAILED", "Message role request was not successful.")
          }

        })
      }
      else {
        promise.reject("ACTIVITY_NULL", "Current activity is null.")
      }
    }
    catch (e: Exception) {
      promise.reject("ERROR", e.message)
    }
  }


  
  override fun getAllMessages(threadId:Double?,limit:Double,offset:Double,promise: Promise) {
    try {
      messageReader.getAllMessages(threadId,limit,offset) { messages ->
        try {
          promise.resolve(messages.toString())
        } catch (e: Exception) {
          promise.reject("PARSE_ERROR", "Failed to parse messages: ${e.message}")
        }
      }
    } catch (e: Exception) {
      promise.reject("ERROR", e.message)
    }
  }





  
  override fun getConversationList(threadId: Double?, limit: Double, offset: Double, promise: Promise) {
    try {
      val conversations = messageReader.getConversationsList(threadId, limit.toInt(), offset.toInt())
      Log.d("MessagerModule", "Raw Conversations JSON: $conversations")

      val writableArray: WritableArray = Arguments.createArray()

      if (conversations != null && conversations.length() > 0) {
        for (i in 0 until conversations.length()) {
          val jsonObject = conversations.optJSONObject(i)

          if (jsonObject != null) {
            val writableMap: WritableMap = Arguments.createMap().apply {
              putDouble("threadId", jsonObject.optLong("threadId", -1).toDouble())
              putString("snippet", jsonObject.optString("snippet", ""))
              putDouble("date", jsonObject.optLong("date", 0).toDouble())
              putBoolean("read", jsonObject.optBoolean("read", false))
              putBoolean("isScheduled", jsonObject.optBoolean("isScheduled", false))
              putBoolean("usesCustomTitle", jsonObject.optBoolean("usesCustomTitle", false))
              putBoolean("isArchived", jsonObject.optBoolean("isArchived", false))
              putString("senderName", jsonObject.optString("senderName", "")) // Added sender name
              putString("senderPhoto", jsonObject.optString("senderPhoto", "")) // Added profile photo
              putInt("unreadCount", jsonObject.optInt("unreadCount", 0)) // Added unread count
              putString("phoneNumber", jsonObject.optString("phoneNumber", ""))
            }
            writableArray.pushMap(writableMap)
          } else {
            Log.e("MessagerModule", "Error: Null conversation at index $i")
          }
        }
      }

      // Log formatted JSON for debugging
      val jsonArray = JSONArray()
      for (i in 0 until writableArray.size()) {
        val obj = writableArray.getMap(i)
        jsonArray.put(obj)
      }

      Log.d("MessagerModule", "Final WritableArray (JSON): ${jsonArray.toString(2)}")

      promise.resolve(writableArray)

    } catch (e: Exception) {
      Log.e("MessagerModule", "Error fetching conversations: ${e.message}")
      promise.reject("ERROR_GET_CONVERSATIONS", e.message ?: "Unknown error")
    }
  }
  
  override fun getConversationsByPhoneNumber(
    phoneNumber: String,
    limit: Double?,
    offset: Double?,
    promise: Promise
  ) {
    try {
      val conversations =
        limit?.let { messageReader.getConversationsByPhoneNumber(phoneNumber, it.toInt(), offset!!.toInt()) }
      Log.d("MessagerModule", "Raw Conversations by Phone Number JSON: $conversations")

      val writableArray: WritableArray = Arguments.createArray()

      if (conversations != null && conversations.length() > 0) {
        for (i in 0 until conversations.length()) {
          val jsonObject = conversations.optJSONObject(i)

          if (jsonObject != null) {
            val writableMap: WritableMap = Arguments.createMap().apply {
              putDouble("threadId", jsonObject.optLong("threadId", -1).toDouble())
              putString("snippet", jsonObject.optString("snippet", ""))
              putDouble("date", jsonObject.optLong("date", 0).toDouble())
              putBoolean("read", jsonObject.optBoolean("read", false))
              putBoolean("isScheduled", jsonObject.optBoolean("isScheduled", false))
              putBoolean("usesCustomTitle", jsonObject.optBoolean("usesCustomTitle", false))
              putBoolean("isArchived", jsonObject.optBoolean("isArchived", false))
              putString("senderName", jsonObject.optString("senderName", ""))
              putString("senderPhoto", jsonObject.optString("senderPhoto", ""))
              putInt("unreadCount", jsonObject.optInt("unreadCount", 0))
              putString("phoneNumber", jsonObject.optString("phoneNumber", ""))
            }
            writableArray.pushMap(writableMap)
          } else {
            Log.e("MessagerModule", "Error: Null conversation at index $i for phone number $phoneNumber")
          }
        }
      }

      // Log formatted JSON for debugging
      val jsonArray = JSONArray()
      for (i in 0 until writableArray.size()) {
        val obj = writableArray.getMap(i)
        jsonArray.put(obj)
      }
      Log.d("MessagerModule", "Final Conversations by Phone Number WritableArray (JSON): ${jsonArray.toString(2)}")

      promise.resolve(writableArray)
    } catch (e: Exception) {
      Log.e("MessagerModule", "Error fetching conversations by phone number: ${e.message}")
      promise.reject("ERROR_GET_CONVERSATIONS_BY_PHONE", e.message ?: "Unknown error")
    }
  
  }
  
 override fun getMessagesList(
    threadId: Double,
    getImageResolutions: Boolean,
    dateFrom: Double,
    includeScheduledMessages: Boolean,
    limit: Double,
    offset: Double,
    promise: Promise
  ) {
    try {
      val messages = messageReader.getMessagesList(
        threadId = threadId.toLong(),
        getImageResolutions = getImageResolutions,
        dateFrom = if (dateFrom == -1.0) -1L else dateFrom.toLong(),
        includeScheduledMessages = includeScheduledMessages,
        limit = limit.toInt(),
        offset = offset.toInt()
      )
      Log.d("MessagerModule", "Raw Messages JSON: $messages")

      val writableArray: WritableArray = Arguments.createArray()

      if (messages != null && messages.length() > 0) {
        for (i in 0 until messages.length()) {
          val jsonObject = messages.optJSONObject(i)

          if (jsonObject != null) {
            val writableMap: WritableMap = Arguments.createMap().apply {
              putDouble("id", jsonObject.optLong("id", -1).toDouble())
              putString("body", jsonObject.optString("body", ""))
              putInt("type", jsonObject.optInt("type", 0))
              putInt("status", jsonObject.optInt("status", -1))
              putDouble("date", jsonObject.optLong("date", 0).toDouble())
              putBoolean("read", jsonObject.optBoolean("read", false))
              putDouble("threadId", jsonObject.optLong("threadId", -1).toDouble())
              putBoolean("isMMS", jsonObject.optBoolean("isMMS", false))
              putInt("subscriptionId", jsonObject.optInt("subscriptionId", 0))
              putBoolean("isScheduled", jsonObject.optBoolean("isScheduled", false))
              putString("senderPhotoUri", jsonObject.optString("senderPhotoUri", ""))
              putString("senderName", jsonObject.optString("senderName", ""))
              putString("senderPhoneNumber", jsonObject.optString("senderPhoneNumber", ""))

              // Handle attachments for MMS
              if (jsonObject.has("attachments")) {
                val attachmentsArray = jsonObject.getJSONArray("attachments")
                val writableAttachments: WritableArray = Arguments.createArray()

                for (j in 0 until attachmentsArray.length()) {
                  val attachment = attachmentsArray.optJSONObject(j)
                  if (attachment != null) {
                    val attachmentMap: WritableMap = Arguments.createMap().apply {
                      putString("partId", attachment.optString("partId", ""))
                      putString("contentType", attachment.optString("contentType", ""))
                      putString("text", attachment.optString("text", ""))
                      putString("filePath", attachment.optString("filePath", ""))
                      if (getImageResolutions && attachment.has("width")) {
                        putInt("width", attachment.optInt("width", 0))
                        putInt("height", attachment.optInt("height", 0))
                      }
                    }
                    writableAttachments.pushMap(attachmentMap)
                  }
                }
                putArray("attachments", writableAttachments)
              }
            }
            writableArray.pushMap(writableMap)
          } else {
            Log.e("MessagerModule", "Error: Null message at index $i")
          }
        }
      }

      // Log formatted JSON for debugging
      val jsonArray = JSONArray()
      for (i in 0 until writableArray.size()) {
        val obj = writableArray.getMap(i)
        jsonArray.put(obj)
      }
      Log.d("MessagerModule", "Final Messages WritableArray (JSON): ${jsonArray.toString(2)}")

      promise.resolve(writableArray)

    } catch (e: Exception) {
      Log.e("MessagerModule", "Error fetching messages: ${e.message}")
      promise.reject("ERROR_GET_MESSAGES", e.message ?: "Unknown error")
    }
  }
 // New React Methods for message operations
    
    override fun markConversationAsRead(threadId: Double, promise: Promise) {
        try {
            messageReader.markConversationAsRead(threadId.toLong()) { success ->
                if (success) {
                    promise.resolve("Conversation marked as read successfully")
                } else {
                    promise.reject("MARK_READ_FAILED", "Failed to mark conversation as read")
                }
            }
        } catch (e: Exception) {
            promise.reject("MARK_READ_ERROR", e.message ?: "Unknown error marking conversation as read")
        }
    }

    
    override fun markMessageAsRead(threadId: Double, messageId: Double, isMMS: Boolean, promise: Promise) {
        try {
            messageReader.markMessageAsRead(threadId.toLong(), messageId.toLong(), isMMS) { success ->
                if (success) {
                    promise.resolve("Message marked as read successfully")
                } else {
                    promise.reject("MARK_READ_FAILED", "Failed to mark message as read")
                }
            }
        } catch (e: Exception) {
            promise.reject("MARK_READ_ERROR", e.message ?: "Unknown error marking message as read")
        }
    }

    
    override fun deleteConversation(threadId: Double, promise: Promise) {
        try {
            messageReader.deleteConversation(threadId.toLong()) { success ->
                if (success) {
                    promise.resolve("Conversation deleted successfully")
                } else {
                    promise.reject("DELETE_FAILED", "Failed to delete conversation")
                }
            }
        } catch (e: Exception) {
            promise.reject("DELETE_ERROR", e.message ?: "Unknown error deleting conversation")
        }
    }

    
    override fun deleteMessage(threadId: Double, messageId: Double, isMMS: Boolean, promise: Promise) {
        try {
            messageReader.deleteMessage(threadId.toLong(), messageId.toLong(), isMMS) { success ->
                if (success) {
                    promise.resolve("Message deleted successfully")
                } else {
                    promise.reject("DELETE_FAILED", "Failed to delete message")
                }
            }
        } catch (e: Exception) {
            promise.reject("DELETE_ERROR", e.message ?: "Unknown error deleting message")
        }
    }
  
  override fun sendSmsMessage(
    text: String,
    addresses: ReadableArray,
    subId: Double,
    requireDeliveryReport: Boolean,
    promise: Promise
  ) {
    try {
      val addressList = mutableListOf<String>()
      for (i in 0 until addresses.size()) {
        addresses.getString(i)?.let { addressList.add(it) }
      }

      // Get the list of sent messages
      val messages = messageReader.sendSmsMessage(
        text = text,
        addresses = addressList,
        subId = subId.toInt(),
        requireDeliveryReport = requireDeliveryReport
      )

      // Convert messages list to a WritableArray to send back to JavaScript
      val resultArray = Arguments.createArray()
      for (message in messages) {
        val messageMap = Arguments.createMap().apply {
          putDouble("id", message.id.toDouble())
          putString("body", message.body)
          putInt("type", message.type)
          putInt("status", message.status)
          putInt("date", message.date)
          putBoolean("read", message.read)
          putDouble("threadId", message.threadId.toDouble())
          putBoolean("isMMS", message.isMMS)
          putInt("subscriptionId", message.subscriptionId)
          putBoolean("isScheduled", message.isScheduled)
          putString("senderPhotoUri", message.senderPhotoUri)
          putString("senderName", message.senderName)
          putString("senderPhoneNumber", message.senderPhoneNumber)
        }
        resultArray.pushMap(messageMap)
      }

      promise.resolve(resultArray)
    } catch (e: Exception) {
      promise.reject("SEND_SMS_ERROR", e.localizedMessage, e)
    }
  }
  // New React Method to get unread messages count
  
  override fun getUnreadMessagesCount(threadId: Double?, promise: Promise) {
    try {
      val count = messageReader.getUnreadMessagesCount(threadId?.toLong()) // Pass null if threadId is null
      promise.resolve(count)
    } catch (e: Exception) {
      promise.reject("UNREAD_COUNT_ERROR", e.message ?: "Unknown error getting unread messages count")
    }
  }

    
    override fun markAllConversationsAsRead(promise: Promise) {
        try {
            messageReader.markAllConversationsAsRead { success ->
                if (success) {
                    promise.resolve("All conversations marked as read successfully")
                } else {
                    promise.reject("MARK_ALL_READ_FAILED", "Failed to mark all conversations as read")
                }
            }
        } catch (e: Exception) {
            promise.reject("MARK_ALL_READ_ERROR", e.message ?: "Unknown error marking all conversations as read")
        }
    }

}