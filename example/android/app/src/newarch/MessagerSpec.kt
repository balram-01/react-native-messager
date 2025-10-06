package com.messager

import com.facebook.react.bridge.ReactApplicationContext

abstract class MessagerSpec internal constructor(context: ReactApplicationContext) :
  NativeMessagerSpec(context) {
}
