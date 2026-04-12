package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

open class FilteredMessageListener<T : Any>(
  private val clazz: KClass<T>,
  val filter: (T) -> Boolean = { true },
  val listeners: List<MessageListener<T>>,
) : MessageListener<AccBroadcastingInbound> {
  private val logger = LoggerFactory.getLogger(javaClass)

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) {
    val body = message.body()
    @Suppress("UNCHECKED_CAST")
    if (clazz.isInstance(body) && filter(body as T)) {
      logger.debug("Matched message")
      listeners.forEach { listener -> listener.onMessage(bytes, body as T, messageSender) }
    } else {
      logger.debug("Unmatched message")
    }
  }

  companion object {
    inline operator fun <reified T : Any> invoke(
      noinline filter: (T) -> Boolean = { true },
      listeners: List<MessageListener<T>>,
    ): FilteredMessageListener<T> = FilteredMessageListener(T::class, filter, listeners)
  }
}
