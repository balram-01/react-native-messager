package com.messager.helpers


import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.messager.extensions.notificationManager
import com.messager.service.NotificationClickReceiver

class NotificationHelper(private val context: Context) {

  private val notificationManager = context.notificationManager
  private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
  private val user = Person.Builder()
    .setName("Tester")
    .build()

  @SuppressLint("NewApi", "LaunchActivityFromNotification")
  fun showMessageNotification(
    messageId: Long,
    address: String,
    body: String,
    threadId: Long,
    bitmap: Bitmap?,
    sender: String?,
    alertOnlyOnce: Boolean = false
  ) {
    maybeCreateChannel(name = "get_sms")

    val notificationId = threadId.hashCode()
    val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(THREAD_ID, threadId)
      putExtra("sender",address)
      putExtra("threadId",threadId)
      putExtra("body",body)
    }

    val broadcastIntent = Intent(context,NotificationClickReceiver::class.java).apply {
      putExtra("sender", address)
      putExtra("body", body)
      putExtra("threadId", threadId)
    }

    val contentPendingIntent =
      PendingIntent.getActivity(context, notificationId, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

    val largeIcon = bitmap ?: if (sender != null) {
      null
    } else {
      null
    }
    val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL).apply {
      setLargeIcon(largeIcon)
      setStyle(getMessagesStyle(address, body, notificationId, sender))
      setSmallIcon(com.facebook.react.R.drawable.ic_resume)
      setContentIntent(contentPendingIntent)
      priority = NotificationCompat.PRIORITY_MAX
      setDefaults(Notification.DEFAULT_LIGHTS)
      setCategory(Notification.CATEGORY_MESSAGE)
      setAutoCancel(true)
      setOnlyAlertOnce(alertOnlyOnce)
      setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
    }
    notificationManager.notify(notificationId, builder.build())
  }



  private fun maybeCreateChannel(name: String) {
    if (isOreoPlus()) {
      val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
        .build()

      val id = NOTIFICATION_CHANNEL
      val importance = IMPORTANCE_HIGH
      NotificationChannel(id, name, importance).apply {
        setBypassDnd(false)
        enableLights(true)
        setSound(soundUri, audioAttributes)
        enableVibration(true)
        notificationManager.createNotificationChannel(this)
      }
    }
  }

  private fun getMessagesStyle(address: String, body: String, notificationId: Int, name: String?): NotificationCompat.MessagingStyle {
    val sender = if (name != null) {
      Person.Builder()
        .setName(name)
        .setKey(address)
        .build()
    } else {
      null
    }

    return NotificationCompat.MessagingStyle(user).also { style ->
      val newMessage = NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
      style.addMessage(newMessage)
    }
  }

}
