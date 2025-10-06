package com.messager.extensions

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony.Threads
import android.provider.Telephony.Sms
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.loader.content.CursorLoader
import com.messager.helpers.MESSAGES_LIMIT
import com.messager.helpers.NotificationHelper
import com.messager.helpers.PERMISSION_POST_NOTIFICATIONS
import com.messager.helpers.PERMISSION_READ_CONTACTS
import com.messager.helpers.PERMISSION_READ_SMS
import com.messager.helpers.PERMISSION_SEND_SMS
import com.messager.helpers.ensureBackgroundThread
import com.messager.helpers.isOnMainThread
import com.messager.helpers.isQPlus
import com.messager.messaging.SmsSender
import com.messager.models.Conversation
import com.messager.models.Message
import com.messager.models.NamePhoto
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import com.messager.helpers.PERMISSION_WRITE_SMS
import java.io.File

val Context.notificationManager: NotificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
val Context.notificationHelper get() = NotificationHelper(this)

fun Context.showReceivedMessageNotification(messageId: Long, address: String, body: String, threadId: Long, bitmap: Bitmap?) {
  ensureBackgroundThread {
    val senderName = "Message"
    Handler(Looper.getMainLooper()).post {
      notificationHelper.showMessageNotification(messageId, address, body, threadId, bitmap, senderName)
    }
  }
}

@SuppressLint("NewApi")
fun Context.getThreadId(address: String): Long {
  return try {
    Threads.getOrCreateThreadId(this, address)
  } catch (e: Exception) {
    0L
  }
}

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
  return try {
    Threads.getOrCreateThreadId(this, addresses)
  } catch (e: Exception) {
    0L
  }
}

val Context.smsSender get() = SmsSender.getInstance(applicationContext as Application)

fun Context.toast(id: Int, length: Int = Toast.LENGTH_SHORT) {
  toast(getString(id), length)
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
  try {
    if (isOnMainThread()) {
      doToast(this, msg, length)
    } else {
      Handler(Looper.getMainLooper()).post {
        doToast(this, msg, length)
      }
    }
  } catch (e: Exception) {
  }
}

private fun doToast(context: Context, message: String, length: Int) {
  if (context is Activity) {
    if (!context.isFinishing && !context.isDestroyed) {
      Toast.makeText(context, message, length).show()
    }
  } else {
    Toast.makeText(context, message, length).show()
  }
}

fun Context.showErrorToast(msg: String, length: Int = Toast.LENGTH_LONG) {
  toast(String.format("Something error", msg), length)
}

fun Context.showErrorToast(exception: Exception, length: Int = Toast.LENGTH_LONG) {
  showErrorToast(exception.toString(), length)
}

fun Context.getPermissionString(id: Int) = when (id) {
  PERMISSION_READ_SMS -> Manifest.permission.READ_SMS
  PERMISSION_SEND_SMS -> Manifest.permission.SEND_SMS
  PERMISSION_READ_CONTACTS -> Manifest.permission.READ_CONTACTS
  PERMISSION_POST_NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
  else -> ""
}

fun Context.hasPermission(permId: Int) = ContextCompat.checkSelfPermission(this, getPermissionString(permId)) == PackageManager.PERMISSION_GRANTED

private fun Context.queryCursorUnsafe(
  uri: Uri,
  projection: Array<String>,
  selection: String? = null,
  selectionArgs: Array<String>? = null,
  sortOrder: String? = null,
  callback: (cursor: Cursor) -> Unit
) {
  val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
  cursor?.use {
    if (cursor.moveToFirst()) {
      do {
        callback(cursor)
      } while (cursor.moveToNext())
    }
  }
}


fun Context.getLastMessageBody(threadId: Long): String {
  // 🔹 First, check SMS messages
  val smsUri = Uri.parse("content://sms/")
  val smsProjection = arrayOf("_id", "body")
  val smsSelection = "thread_id = ?"
  val smsSelectionArgs = arrayOf(threadId.toString())
  val smsSortOrder = "date DESC LIMIT 1"

  val smsBody = try {
    contentResolver.query(smsUri, smsProjection, smsSelection, smsSelectionArgs, smsSortOrder)?.use { cursor ->
      if (cursor.moveToFirst()) {
        return cursor.getString(cursor.getColumnIndexOrThrow("body")) ?: ""
      }
      ""
    } ?: ""
  } catch (e: Exception) {
    Log.e("ConversationDebug", "Error fetching SMS for thread $threadId: ${e.message}")
    "[Error]"
  }

  // 🔹 If no SMS, check MMS messages
  if (smsBody.isEmpty()) {
    val mmsBody = getLastMmsMessage(threadId)
    return mmsBody ?: "[MMS Message]"
  }

  return smsBody
}

// ✅ Fetch MMS text from `content://mms/`
fun Context.getLastMmsMessage(threadId: Long): String? {
  val mmsUri = Uri.parse("content://mms/")
  val projection = arrayOf("_id")
  val selection = "thread_id = ?"
  val selectionArgs = arrayOf(threadId.toString())
  val sortOrder = "date DESC LIMIT 1"

  return try {
    contentResolver.query(mmsUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val mmsId = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
        return getMmsText(mmsId) // Fetch MMS text parts
      }
      null
    }
  } catch (e: Exception) {
    Log.e("ConversationDebug", "Error fetching MMS for thread $threadId: ${e.message}")
    null
  }
}

fun Context.getMmsText(mmsId: String): String? {
  val partUri = Uri.parse("content://mms/part")
  val projection = arrayOf("_id", "ct", "text", "_data") // Added _data column
  val selection = "mid = ?"
  val selectionArgs = arrayOf(mmsId)

  var fallbackMessage: String? = null // Store non-text fallback (image, video, etc.)

  return try {
    contentResolver.query(partUri, projection, selection, selectionArgs, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val contentType = cursor.getString(cursor.getColumnIndexOrThrow("ct"))

        // 🔹 Try fetching text from 'text' column
        val textMessage = cursor.getString(cursor.getColumnIndexOrThrow("text"))
        if (!textMessage.isNullOrEmpty()) return textMessage

        // 🔹 If 'text' is empty, try reading from '_data' column
        val dataPath = cursor.getString(cursor.getColumnIndexOrThrow("_data"))
        if (!dataPath.isNullOrEmpty()) {
          val file = File(dataPath)
          if (file.exists()) {
            return file.readText() // Read the text content from file
          }
        }

        // 🔹 If no text found, store first encountered attachment type as fallback
        when {
          contentType.startsWith("image/") && fallbackMessage == null -> fallbackMessage = "[MMS Image]"
          contentType.startsWith("video/") && fallbackMessage == null -> fallbackMessage = "[MMS Video]"
          contentType.startsWith("audio/") && fallbackMessage == null -> fallbackMessage = "[MMS Audio]"
          contentType.startsWith("application/") && fallbackMessage == null -> fallbackMessage = "[MMS File]"
        }
      }
      fallbackMessage ?: "[MMS Message]" // If no text or known media, return generic "[MMS Message]"
    }
  } catch (e: Exception) {
    Log.e("ConversationDebug", "Error fetching MMS text for ID $mmsId: ${e.message}")
    null
  }
}

fun Context.getPhoneNumberFromRecipientId(recipientId: String): String? {
  val uri = Uri.parse("content://mms-sms/canonical-addresses")
  val projection = arrayOf("_id", "address")
  val selection = "_id = ?"
  val selectionArgs = arrayOf(recipientId)

  contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
      return cursor.getStringValue("address")
    }
  }
  return null
}
fun Context.getUnreadMessagesCount(threadId: Long? = null): Int {
  val uri = Uri.parse("content://sms/inbox")
  val projection = arrayOf("COUNT(*)")

  // Adjust selection based on whether threadId is provided
  val selection = if (threadId != null) "thread_id = ? AND read = ?" else "read = ?"
  val selectionArgs = if (threadId != null) arrayOf(threadId.toString(), "0") else arrayOf("0")

  contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
      return cursor.getInt(0)
    }
  }
  return 0
}
fun Context.getConversations(
  threadId: Long? = null,
  limit: Int = 20,  // Fetch only 20 at a time
  offset: Int = 0   // Start from a specific position
): ArrayList<Conversation> {
  val uri = Uri.parse("${Threads.CONTENT_URI}?simple=true")
  val projection = arrayOf(
    Threads._ID,
    Threads.SNIPPET,
    Threads.DATE,
    Threads.READ,
    Threads.RECIPIENT_IDS
  )

  var selection = "${Threads.MESSAGE_COUNT} > ?"
  var selectionArgs = arrayOf("0")
  if (threadId != null) {
    selection += " AND ${Threads._ID} = ?"
    selectionArgs = arrayOf("0", threadId.toString())
  }

    //val sortOrder = "${Threads.DATE} DESC"
  val conversations = ArrayList<Conversation>()
  val sortOrder = "${Threads.DATE} DESC LIMIT ${limit.toInt()} OFFSET ${offset.toInt()}"
  val jsonArray = JSONArray()

  try {
    contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
      if (cursor.count == 0) {
        Log.d("PaginationDebug", "No conversations found.")
        return@use
      }

      if (offset >= cursor.count) {
        Log.d("PaginationDebug", "Offset ($offset) is out of bounds, no more data to load.")
        return@use
      }

      if (!cursor.moveToPosition(offset)) {
        Log.d("PaginationDebug", "Failed to move cursor to offset: $offset")
        return@use
      }

      var count = 0
      do {
        if (count >= limit) break

        val threadId = cursor.getLongValue(Threads._ID)
        var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
        var date = cursor.getLongValue(Threads.DATE)
        val recipientIds = cursor.getStringValue(Threads.RECIPIENT_IDS)

        // 🔹 Get phone number from recipient ID
        val phoneNumber = recipientIds?.split(" ")?.firstOrNull()?.let { getPhoneNumberFromRecipientId(it) }

        // 🔹 Get sender's name and photo
        val namePhoto = if (phoneNumber != null) getNameAndPhotoFromPhoneNumber(phoneNumber)
        else NamePhoto("Unknown", null)

        // Convert date if needed
        if (date.toString().length > 10) {
          date /= 1000
        }

        val read = cursor.getIntValue(Threads.READ) == 1

        // 🔹 Get unread messages count
        val unreadCount = getUnreadMessagesCount(threadId)

        if (snippet.isEmpty()) {
          snippet = getLastMessageBody(threadId) ?: ""
          Log.w("ConversationDebug", "Thread ID $threadId has an empty snippet. Fetched last message: '$snippet'")
        }



        val conversation = Conversation(
          threadId = threadId,
          snippet = snippet,
          date = date.toInt(),
          read = read,
          isScheduled = false,
          usesCustomTitle = false,
          isArchived = false,
          senderName = namePhoto.name,  // ⬅️ Added sender name
          senderPhoto = namePhoto.photoUri, // ⬅️ Added profile photo
          unreadCount = unreadCount ,// ⬅️ Added unread count
          phoneNumber = phoneNumber
        )

        Log.d("ConversationDebug", "Loaded: $conversation")
        conversations.add(conversation)

        count++
      } while (cursor.moveToNext())
    }


  } catch (sqliteException: SQLiteException) {

    if (sqliteException.message?.contains("no such column: archived") == true) {
      return getConversations(threadId, limit, offset)
    } else {
      showErrorToast(sqliteException)
    }
  } catch (e: Exception) {
    Log.e("ConversationDebug", "Exception: ${e.message}")
    showErrorToast(e)
  }

  return conversations
}
fun Context.getConversationsByPhoneNumber(
    phoneNumber: String,
    limit: Int = 20,
    offset: Int = 0
): ArrayList<Conversation> {
    val conversations = ArrayList<Conversation>()
    val normalizedNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")

    // Step 1: Find recipient IDs for the phone number
    val recipientIds = mutableListOf<String>()
    val canonicalUri = Uri.parse("content://mms-sms/canonical-addresses")
    val projection = arrayOf("_id", "address")
    val selection = "address LIKE ? OR address LIKE ?"
    val selectionArgs = arrayOf("%$normalizedNumber", "%${normalizedNumber.takeLast(10)}")

    try {
        contentResolver.query(canonicalUri, projection, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getStringValue("address")?.replace("[^0-9+]".toRegex(), "") ?: ""
                // Strict matching: exact match or match without country code
                if (address == normalizedNumber || address.endsWith(normalizedNumber.takeLast(10))) {
                    recipientIds.add(cursor.getStringValue("_id"))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("ConversationDebug", "Error fetching recipient IDs for $phoneNumber: ${e.message}")
        showErrorToast(e)
        return conversations
    }

    if (recipientIds.isEmpty()) {
        Log.d("ConversationDebug", "No recipient IDs found for phone number: $phoneNumber")
        return conversations
    }

    // Step 2: Query threads with matching recipient IDs
    val uri = Uri.parse("${Threads.CONTENT_URI}?simple=true")
    val threadProjection = arrayOf(
        Threads._ID,
        Threads.SNIPPET,
        Threads.DATE,
        Threads.READ,
        Threads.RECIPIENT_IDS
    )
    val threadSelection = "${Threads.MESSAGE_COUNT} > ? AND ${Threads.RECIPIENT_IDS} IN (${recipientIds.joinToString(",") { "?" }})"
    val threadSelectionArgs = arrayOf("0", *recipientIds.toTypedArray())
    val sortOrder = "${Threads.DATE} DESC LIMIT $limit OFFSET $offset"

    try {
        contentResolver.query(uri, threadProjection, threadSelection, threadSelectionArgs, sortOrder)?.use { cursor ->
            if (cursor.count == 0) {
                Log.d("ConversationDebug", "No conversations found for phone number: $phoneNumber")
                return@use
            }

            if (!cursor.moveToFirst()) {
                Log.d("ConversationDebug", "Failed to move cursor to first position")
                return@use
            }

            var count = 0
            do {
                if (count >= limit) break

                val threadId = cursor.getLongValue(Threads._ID)
                var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
                var date = cursor.getLongValue(Threads.DATE)
                val read = cursor.getIntValue(Threads.READ) == 1
                val recipientIdsStr = cursor.getStringValue(Threads.RECIPIENT_IDS) ?: ""

                // Verify phone number match
                val phoneNumbers = recipientIdsStr.split(" ").mapNotNull { getPhoneNumberFromRecipientId(it) }
                val matchingPhoneNumber = phoneNumbers.firstOrNull { number ->
                    val normalizedContactsonyNumber = number.replace("[^0-9+]".toRegex(), "")
                    normalizedNumber == normalizedNumber || normalizedNumber.endsWith(normalizedNumber.takeLast(10))
                } ?: normalizedNumber

                // Get sender's name and photo
                val namePhoto = getNameAndPhotoFromPhoneNumber(matchingPhoneNumber)

                // Convert date if needed
                if (date.toString().length > 10) {
                    date /= 1000
                }

                // Get unread messages count
                val unreadCount = getUnreadMessagesCount(threadId)

                if (snippet.isEmpty()) {
                    snippet = getLastMessageBody(threadId) ?: ""
                    Log.w("ConversationDebug", "Thread ID $threadId has empty snippet. Fetched last message: '$snippet'")
                }

                val conversation = Conversation(
                    threadId = threadId,
                    snippet = snippet,
                    date = date.toInt(),
                    read = read,
                    isScheduled = false,
                    usesCustomTitle = false,
                    isArchived = false,
                    senderName = namePhoto.name,
                    senderPhoto = namePhoto.photoUri,
                    unreadCount = unreadCount,
                    phoneNumber = matchingPhoneNumber
                )

                Log.d("ConversationDebug", "Loaded conversation for $phoneNumber: $conversation")
                conversations.add(conversation)
                count++
            } while (cursor.moveToNext())
        }
    } catch (e: Exception) {
        Log.e("ConversationDebug", "Error fetching conversations for $phoneNumber: ${e.message}")
        showErrorToast(e)
    }

    Log.d("ConversationDebug", "Total conversations returned: ${conversations.size}")
    return conversations
}
fun Context.getNameAndPhotoFromPhoneNumber(number: String): NamePhoto {
  if (!hasPermission(PERMISSION_READ_CONTACTS)) {
    return NamePhoto(number, null)
  }

  val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
  val projection = arrayOf(
    ContactsContract.PhoneLookup.DISPLAY_NAME,
    ContactsContract.PhoneLookup.PHOTO_URI
  )

  try {
    val cursor = contentResolver.query(uri, projection, null, null, null)
    cursor.use {
      if (cursor?.moveToFirst() == true) {
        val name = cursor.getStringValue(ContactsContract.PhoneLookup.DISPLAY_NAME)
        val photoUri = cursor.getStringValue(ContactsContract.PhoneLookup.PHOTO_URI)
        return NamePhoto(name, photoUri)
      }
    }
  } catch (e: Exception) {
  }

  return NamePhoto(number, null)
}

fun Context.getMessages(
  threadId: Long,
  getImageResolutions: Boolean = false,
  dateFrom: Long = -1L,
  includeScheduledMessages: Boolean = true,
  limit: Int = MESSAGES_LIMIT,
  offset: Int = 0
): JSONArray {
  val messagesArray = JSONArray()

  Log.d("MessageStatus", "Permissions - READ_SMS: ${hasPermission(PERMISSION_READ_SMS)}, WRITE_SMS: ${hasPermission(PERMISSION_WRITE_SMS)}, SEND_SMS: ${hasPermission(PERMISSION_SEND_SMS)}")
  Log.d("MessageStatus", "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android Version: ${Build.VERSION.RELEASE}")

  try {
    // SMS Query
    val smsUri = Sms.CONTENT_URI
    val smsProjection = arrayOf(
      Sms._ID,
      Sms.BODY,
      Sms.TYPE,
      Sms.ADDRESS,
      Sms.DATE,
      Sms.READ,
      Sms.THREAD_ID,
      Sms.SUBSCRIPTION_ID,
      Sms.STATUS
    )

    val rangeQuery = if (dateFrom == -1L) "" else "AND ${Sms.DATE} < ?"
    val smsSelection = "${Sms.THREAD_ID} = ? $rangeQuery"
    val smsSelectionArgs = if (dateFrom == -1L)
      arrayOf(threadId.toString())
    else
      arrayOf(threadId.toString(), dateFrom.toString())

    val smsSortOrder = "${Sms.DATE} DESC LIMIT $limit OFFSET $offset"

    Log.d("MessageStatus", "Querying SMS messages for threadId: $threadId")
    queryCursor(smsUri, smsProjection, smsSelection, smsSelectionArgs, smsSortOrder) { cursor ->
      val messageObj = createMessageObjectFromCursor(cursor, isMMS = false)
      Log.d("MessageStatus", "SMS ID: ${messageObj.getLong("id")}, Type: ${messageObj.getInt("type")}, Status: ${messageObj.getInt("status")}")
      val starredIndex = cursor.getColumnIndex("starred")
      val starred = if (starredIndex != -1) {
        cursor.getInt(starredIndex) == 1
      } else {
        false
      }
      messageObj.put("starred", starred)
      messagesArray.put(messageObj)
    }

    // MMS Query
    val mmsUri = Uri.parse("content://mms/")
    val mmsProjection = arrayOf(
      "_id",
      "date",
      "read",
      "thread_id",
      "sub_id",
      "m_type",
      "st"
    )

    val mmsSelection = "thread_id = ? ${if (dateFrom == -1L) "" else "AND date < ?"}"
    val mmsSelectionArgs = if (dateFrom == -1L)
      arrayOf(threadId.toString())
    else
      arrayOf(threadId.toString(), (dateFrom / 1000).toString())

    val mmsSortOrder = "date DESC LIMIT $limit OFFSET $offset"

    Log.d("MessageStatus", "Querying MMS messages for threadId: $threadId")
    queryCursor(mmsUri, mmsProjection, mmsSelection, mmsSelectionArgs, mmsSortOrder) { cursor ->
      val mmsId = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
      val messageObj = createMessageObjectFromCursor(cursor, isMMS = true)
      Log.d("MessageStatus", "MMS ID: ${messageObj.getLong("id")}, Type: ${messageObj.getInt("type")}, Status: ${messageObj.getInt("status")}")
      val starredIndex = cursor.getColumnIndex("st")
      val starred = if (starredIndex != -1) {
        cursor.getInt(starredIndex) == 1
      } else {
        false
      }
      messageObj.put("starred", starred)
      messageObj.put("attachments", getMmsAttachmentData(mmsId, getImageResolutions))
      messagesArray.put(messageObj)
    }

  } catch (e: Exception) {
    Log.e("MessageFetch", "Error fetching messages: ${e.message}")
    showErrorToast(e)
  }

  Log.d("MessageStatus", "Total messages fetched: ${messagesArray.length()}")
  return messagesArray
}
// Helper function to create message object from cursor
private fun Context.createMessageObjectFromCursor(cursor: Cursor, isMMS: Boolean): JSONObject {
  val messageObj = JSONObject()
  var senderNumber = ""
  try {
    senderNumber = if (isMMS) {
      getMmsSenderAddress(cursor.getString(cursor.getColumnIndexOrThrow("_id"))) ?: ""
    } else {
      cursor.getStringValue(Sms.ADDRESS) ?: ""
    }
  } catch (e: Exception) {
    Log.e("MessageStatus", "Error getting sender number for ${if (isMMS) "MMS" else "SMS"}: ${e.message}")
  }

  val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)

  val statusColumn = if (isMMS) "st" else Sms.STATUS
  val statusIndex = cursor.getColumnIndex(statusColumn)
  val status = if (statusIndex != -1) {
    try {
      val value = cursor.getInt(statusIndex)
      if (isMMS) {
        val mType = cursor.getIntValue("m_type")
        Log.d("MessageStatus", "MMS status column '$statusColumn' found, value: $value, m_type: $mType")
      } else {
        Log.d("MessageStatus", "SMS status column '$statusColumn' found, value: $value")
        if (cursor.getIntValue(Sms.TYPE) == 4) {
          Log.d("MessageStatus", "SMS ID: ${cursor.getLongValue(Sms._ID)} is draft/queued, status expected to be -1")
        }
      }
      value
    } catch (e: Exception) {
      Log.e("MessageStatus", "Error reading status column '$statusColumn': ${e.message}")
      -1
    }
  } else {
    Log.w("MessageStatus", "${if (isMMS) "MMS" else "SMS"} status column '$statusColumn' NOT found, defaulting to -1")
    -1
  }

  messageObj.apply {
    put("id", cursor.getLongValue(if (isMMS) "_id" else Sms._ID))
    put("body", if (isMMS) getMmsText(cursor.getString(cursor.getColumnIndexOrThrow("_id"))) else cursor.getStringValue(Sms.BODY))
    put("type", if (isMMS) cursor.getIntValue("m_type") else cursor.getIntValue(Sms.TYPE))
    put("status", status)
    put("date", if (isMMS) cursor.getLongValue("date") else (cursor.getLongValue(Sms.DATE) / 1000))
    put("read", cursor.getIntValue(if (isMMS) "read" else Sms.READ) == 1)
    put("threadId", cursor.getLongValue(if (isMMS) "thread_id" else Sms.THREAD_ID))
    put("isMMS", isMMS)
    put("subscriptionId", cursor.getIntValue(if (isMMS) "sub_id" else Sms.SUBSCRIPTION_ID))
    put("isScheduled", false)
    put("senderPhotoUri", namePhoto.photoUri ?: "")
    put("senderName", namePhoto.name)
    put("senderPhoneNumber", senderNumber)
  }

  return messageObj
}

// Get MMS attachment data
private fun Context.getMmsAttachmentData(mmsId: String, getResolutions: Boolean): JSONArray {
  val attachments = JSONArray()
  val partUri = Uri.parse("content://mms/part")
  val projection = arrayOf("_id", "ct", "text", "_data", "cl") // cl = content location

  queryCursor(partUri, projection, "mid = ?", arrayOf(mmsId)) { cursor ->
    val attachmentObj = JSONObject()
    val contentType = cursor.getString(cursor.getColumnIndexOrThrow("ct"))
    val dataPath = cursor.getString(cursor.getColumnIndexOrThrow("_data"))

    attachmentObj.apply {
      put("partId", cursor.getString(cursor.getColumnIndexOrThrow("_id")))
      put("contentType", contentType)
      put("text", cursor.getString(cursor.getColumnIndexOrThrow("text")))
      put("filePath", dataPath)

      if (getResolutions && dataPath != null && contentType.startsWith("image/")) {
        try {
          val bitmap = BitmapFactory.decodeFile(dataPath)
          if (bitmap != null) {
            put("width", bitmap.width)
            put("height", bitmap.height)
            bitmap.recycle()
          }
        } catch (e: Exception) {
          Log.e("MMSAttachment", "Error getting image resolution: ${e.message}")
        }
      }
    }
    attachments.put(attachmentObj)
  }

  return attachments
}
// Helper to get MMS sender address
private fun Context.getMmsSenderAddress(mmsId: String): String {
  val addrUri = Uri.parse("content://mms/$mmsId/addr")
  val projection = arrayOf("address", "type")

  var senderAddress = ""
  queryCursor(addrUri, projection, "type = 137") { cursor ->  // 137 = sender type
    senderAddress = cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: ""
  }
  return senderAddress
}

fun Context.markAllConversationsAsRead(): Boolean {
  if (!hasPermission(PERMISSION_READ_SMS) || !hasPermission(PERMISSION_WRITE_SMS)) {
    toast("Missing SMS read/write permissions")
    return false
  }

  return try {
    ensureBackgroundThread {
      // Update SMS messages
      val smsUri = Sms.CONTENT_URI
      val smsValues = ContentValues().apply {
        put(Sms.READ, 1) // 1 = read
      }
      if (smsValues.size() == 0) {
        throw IllegalStateException("Empty SMS ContentValues")
      }
      val smsSelection = "${Sms.READ} = ?"
      val smsSelectionArgs = arrayOf("0") // 0 = unread
      val smsUpdated = contentResolver.update(
        smsUri,
        smsValues,
        smsSelection,
        smsSelectionArgs
      )

      // Update MMS messages
      val mmsUri = Uri.parse("content://mms/")
      val mmsValues = ContentValues().apply {
        put("read", 1)
      }
      if (mmsValues.size() == 0) {
        throw IllegalStateException("Empty MMS ContentValues")
      }
      val mmsSelection = "read = ?"
      val mmsSelectionArgs = arrayOf("0")
      val mmsUpdated = contentResolver.update(
        mmsUri,
        mmsValues,
        mmsSelection,
        mmsSelectionArgs
      )

      // Query and update conversation threads individually
      val threadUri = Threads.CONTENT_URI
      val threadProjection = arrayOf(Threads._ID)
      val threadSelection = "${Threads.READ} = ?"
      val threadSelectionArgs = arrayOf("0")
      val threadIds = mutableListOf<Long>()

      // Query unread threads
      contentResolver.query(threadUri, threadProjection, threadSelection, threadSelectionArgs, null)?.use { cursor ->
        while (cursor.moveToNext()) {
          threadIds.add(cursor.getLong(cursor.getColumnIndexOrThrow(Threads._ID)))
        }
      }

      // Update each thread individually
      var threadUpdatedCount = 0
      threadIds.forEach { threadId ->
        val singleThreadUri = Uri.parse("${Threads.CONTENT_URI}/$threadId")
        val threadValues = ContentValues().apply {
          put(Threads.READ, 1)
        }
        if (threadValues.size() == 0) {
          throw IllegalStateException("Empty Thread ContentValues")
        }
        val updated = contentResolver.update(singleThreadUri, threadValues, null, null)
        if (updated > 0) threadUpdatedCount++
      }

      // Clear notifications
      notificationManager.cancelAll()

      // Post success toast on main thread
      if (smsUpdated > 0 || mmsUpdated > 0 || threadUpdatedCount > 0) {
        Handler(Looper.getMainLooper()).post {
          toast("All messages marked as read")
        }
      }
    }
    true
  } catch (e: Exception) {
    showErrorToast(e)
    false
  }
}
// Mark all messages in a conversation as read
fun Context.markConversationMessagesAsRead(threadId: Long): Boolean {
  if (!hasPermission(PERMISSION_READ_SMS)) {
    toast("Missing SMS permissions")
    return false
  }

  return try {
    ensureBackgroundThread {
      // Update SMS messages
      val smsUri = Sms.CONTENT_URI
      val smsSelection = "${Sms.THREAD_ID} = ? AND ${Sms.READ} = ?"
      val smsSelectionArgs = arrayOf(threadId.toString(), "0") // 0 = unread

      val smsValues = ContentValues().apply {
        put(Sms.READ, 1) // 1 = read
      }
      val smsUpdated = contentResolver.update(
        smsUri,
        smsValues,
        smsSelection,
        smsSelectionArgs
      )

      // Update MMS messages
      val mmsUri = Uri.parse("content://mms/")
      val mmsSelection = "thread_id = ? AND read = ?"
      val mmsSelectionArgs = arrayOf(threadId.toString(), "0")

      val mmsValues = ContentValues().apply {
        put("read", 1)
      }
      val mmsUpdated = contentResolver.update(
        mmsUri,
        mmsValues,
        mmsSelection,
        mmsSelectionArgs
      )

      // Update thread read status
      val threadUri = Uri.parse("${Threads.CONTENT_URI}/$threadId")
      val threadValues = ContentValues().apply {
        put(Threads.READ, 1)
      }
      val threadUpdated = contentResolver.update(
        threadUri,
        threadValues,
        null,
        null
      )


    }
    true
  } catch (e: Exception) {
    Log.e("MarkAsRead", "Error marking messages as read: ${e.message}")
    showErrorToast(e)
    false
  }
}

// Bonus: Method to mark a specific message as read
fun Context.markMessageAsRead(threadId: Long, messageId: Long, isMMS: Boolean = false): Boolean {
  if (!hasPermission(PERMISSION_READ_SMS)) {
    toast("Missing SMS permissions")
    return false
  }

  return try {
    ensureBackgroundThread {
      val uri = if (isMMS) {
        Uri.parse("content://mms/$messageId")
      } else {
        Uri.parse("content://sms/$messageId")
      }

      val values = ContentValues().apply {
        put(if (isMMS) "read" else Sms.READ, 1)
      }

      val updated = contentResolver.update(uri, values, null, null)

      // Update thread if this was the last unread message
      if (updated > 0 && getUnreadMessagesCount(threadId) == 0) {
        val threadUri = Uri.parse("${Threads.CONTENT_URI}/$threadId")
        val threadValues = ContentValues().apply {
          put(Threads.READ, 1)
        }
        contentResolver.update(threadUri, threadValues, null, null)
      }

      // Handler(Looper.getMainLooper()).post {
      //   if (updated > 0) {
      //     toast("Message marked as read")
      //   }
      // }
    }
    true
  } catch (e: Exception) {
    Log.e("MarkAsRead", "Error marking message as read: ${e.message}")
    showErrorToast(e)
    false
  }
}

// method to delete conversation
fun Context.deleteConversation(threadId: Long): Boolean {
  if (!hasPermission(PERMISSION_READ_SMS)) {
    toast("Missing SMS permissions")
    return false
  }

  return try {
    ensureBackgroundThread {
      val smsUri = Sms.CONTENT_URI
      val smsSelection = "${Sms.THREAD_ID} = ?"
      val smsSelectionArgs = arrayOf(threadId.toString())
      val smsDeleted = contentResolver.delete(smsUri, smsSelection, smsSelectionArgs)

      val mmsUri = Uri.parse("content://mms/")
      val mmsSelection = "thread_id = ?"
      val mmsSelectionArgs = arrayOf(threadId.toString())
      val mmsDeleted = contentResolver.delete(mmsUri, mmsSelection, mmsSelectionArgs)

      val threadUri = Uri.parse("${Threads.CONTENT_URI}/$threadId")
      val threadDeleted = contentResolver.delete(threadUri, null, null)

      val success = smsDeleted > 0 || mmsDeleted > 0 || threadDeleted > 0
      if (success) {
        Handler(Looper.getMainLooper()).post {
          toast("Conversation deleted successfully")
        }
      }
    }
    true
  } catch (e: Exception) {
    Log.e("DeleteConversation", "Error deleting conversation: ${e.message}")
    showErrorToast(e)
    false
  }
}
//method to delete specific message from conversation
@SuppressLint("NewApi")


fun Context.deleteMessage(threadId: Long, messageId: Long, isMMS: Boolean = false): Boolean {
  if (!hasPermission(PERMISSION_READ_SMS)) {
    toast("Missing SMS permissions")
    return false
  }

  return try {
    ensureBackgroundThread {
      val uri = if (isMMS) {
        Uri.parse("content://mms/$messageId")
      } else {
        Uri.parse("content://sms/$messageId")
      }

      // Perform the deletion
      val deletedRows = contentResolver.delete(uri, null, null)
      if (deletedRows > 0) {
        // Check if there are any remaining messages in the conversation
        val remainingMessages = getMessages(threadId, limit = Int.MAX_VALUE).length()
        if (remainingMessages == 0) {
          deleteConversation(threadId)
        }
      } else {
        Log.w("DeleteMessage", "No rows deleted for messageId: $messageId, isMMS: $isMMS")
      }
    }
    true
  } catch (e: Exception) {
    Log.e("DeleteMessage", "Error deleting message: ${e.message}")
    showErrorToast(e)
    false
  }
}


// Helper to get MMS sender addres
fun Context.queryCursor(
  uri: Uri,
  projection: Array<String>,
  selection: String? = null,
  selectionArgs: Array<String>? = null,
  sortOrder: String? = null,
  showErrors: Boolean = false,
  callback: (cursor: Cursor) -> Unit
) {
  try {
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
    cursor?.use {
      if (cursor.moveToFirst()) {
        do {
          callback(cursor)
        } while (cursor.moveToNext())
      }
    }
  } catch (e: Exception) {
    if (showErrors) {
      showErrorToast(e)
    }
  }
}

@RequiresApi(Build.VERSION_CODES.O)
fun Context.queryCursor(
  uri: Uri,
  projection: Array<String>,
  queryArgs: Bundle,
  showErrors: Boolean = false,
  callback: (cursor: Cursor) -> Unit
) {
  try {
    val cursor = contentResolver.query(uri, projection, queryArgs, null)
    cursor?.use {
      if (cursor.moveToFirst()) {
        do {
          callback(cursor)
        } while (cursor.moveToNext())
      }
    }
  } catch (e: Exception) {
    if (showErrors) {
      showErrorToast(e)
    }
  }
}
