package com.messager.models

import android.provider.Telephony

data class Message(
  val id: Long,
  val body: String,
  val type: Int,
  val status: Int,
  val date: Int,
  val read: Boolean,
  val threadId: Long,
  val isMMS: Boolean,
  var subscriptionId: Int,
  var isScheduled: Boolean = false,
  val senderPhotoUri:String?,
  val senderName:String?,
  val senderPhoneNumber: String?
) : ThreadItem() {

  fun isReceivedMessage() = type == Telephony.Sms.MESSAGE_TYPE_INBOX

  fun millis() = date * 1000L

  companion object {

    fun getStableId(message: Message): Long {
      var result = message.id.hashCode()
      result = 31 * result + message.body.hashCode()
      result = 31 * result + message.date.hashCode()
      result = 31 * result + message.threadId.hashCode()
      result = 31 * result + message.isMMS.hashCode()
      result = 31 * result + message.isScheduled.hashCode()
      return result.toLong()
    }

    fun areItemsTheSame(old: Message, new: Message): Boolean {
      return old.id == new.id
    }

    fun areContentsTheSame(old: Message, new: Message): Boolean {
      return old.body == new.body &&
        old.threadId == new.threadId &&
        old.date == new.date &&
        old.isMMS == new.isMMS &&
        old.isScheduled == new.isScheduled
    }
  }
}
