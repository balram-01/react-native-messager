package com.messager.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class NotificationClickActivity:AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
    super.onCreate(savedInstanceState, persistentState)
    println("started Activity.............")
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    if (launchIntent != null) {
      startActivity(launchIntent)
      finish()
    } else {
      Log.d("ComposeSmsActivity", "Launch intent is null, cannot open app")
    }
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    println("started Activity.............")
  }
}
