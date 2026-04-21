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

    // Determine the target for the listener: either the wrapper or the body
    @Suppress("UNCHECKED_CAST")
    val target: T? =
      when {
        clazz.isInstance(message) -> message as T
        clazz.isInstance(body) -> body as T
        else -> null
      }

    if (target != null && filter(target)) {
      logger.debug("Matched message: ${target::class.simpleName}")
      listeners.forEach { listener -> listener.onMessage(bytes, target, messageSender) }
    }
  }

  companion object {
    inline operator fun <reified T : Any> invoke(
      noinline filter: (T) -> Boolean = { true },
      listeners: List<MessageListener<T>>,
    ): FilteredMessageListener<T> = FilteredMessageListener(T::class, filter, listeners)
  }
}
