package com.github.prule.acc.client

import org.slf4j.LoggerFactory

class LoggingListener<T> : MessageListener<T> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onMessage(
        bytes: ByteArray,
        message: T,
        messageSender: MessageSender,
    ) {
        logger.debug("Received bytes: ${bytes.toHexString()} ${JsonFormatter.toJsonString(message as Any)}")
    }
}
