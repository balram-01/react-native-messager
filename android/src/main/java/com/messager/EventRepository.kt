package com.messager

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule

object EventRepository {
  private var reactContext:ReactApplicationContext?= null

  fun initialize(context: ReactApplicationContext) {
    reactContext = context
  }

  fun emitEvent(eventName: String, eventData: Any?) {
    reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      ?.emit(eventName, eventData)
  }

  fun isInitialized(): Boolean {
    return reactContext != null
  }
}
