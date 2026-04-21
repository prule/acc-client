package com.github.prule.acc.client

interface MessageListener<T> {
  fun onStart() {}

  fun onMessage(
      bytes: ByteArray,
      message: T,
      messageSender: MessageSender,
  )

  fun onStop() {}
}
