package io.github.prule.acc.client

import io.github.prule.acc.messages.AccBroadcastingInbound
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class FilteredMessageListener<T : Any>(
    private val clazz: KClass<T>,
    val filter: (T) -> Boolean = { true },
    val block: (T) -> Unit,
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
            block(body as T)
        } else {
            logger.debug("Unmatched message")
        }
    }

    companion object {
        inline operator fun <reified T : Any> invoke(
            noinline filter: (T) -> Boolean = { true },
            noinline block: (T) -> Unit,
        ): FilteredMessageListener<T> = FilteredMessageListener(T::class, filter, block)
    }
}
