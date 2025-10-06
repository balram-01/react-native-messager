package com.messager.helpers

import android.os.Looper


fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()

const val MESSAGES_LIMIT = 30
const val PERMISSION_READ_SMS = 13
const val PERMISSION_WRITE_SMS = 5 // Added for WRITE_SMS permission
const val PERMISSION_SEND_SMS = 14
const val PERMISSION_READ_CONTACTS = 5
const val PERMISSION_POST_NOTIFICATIONS = 17
const val NOTIFICATION_CHANNEL = "simple_sms_messenger"
const val THREAD_ID = "thread_id"
