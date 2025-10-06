package com.messager.models

data class Conversation(
  var threadId: Long,
  var snippet: String,
  var date: Int,
  var read: Boolean,
  var isScheduled: Boolean = false,
  var usesCustomTitle: Boolean = false,
  var isArchived: Boolean = false,
  var senderName: String?, // Added sender name
  var senderPhoto: String?, // Added profile photo
  var unreadCount: Int = 0 ,// Added unread count
  val phoneNumber: String? = null
) {
  companion object {
    fun areItemsTheSame(old: Conversation, new: Conversation): Boolean {
      return old.threadId == new.threadId
    }

    fun areContentsTheSame(old: Conversation, new: Conversation): Boolean {
      return old.snippet == new.snippet &&
        old.date == new.date &&
        old.read == new.read &&
        old.senderName == new.senderName && // Compare sender name
        old.senderPhoto == new.senderPhoto && // Compare profile photo
        old.unreadCount == new.unreadCount // Compare unread count
    }
  }
}
