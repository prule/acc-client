package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound

/**
 * Legacy thin wrapper around [ContextUpdater] for callers that only want registration +
 * focused-car-index handling. Prefer wiring [ContextUpdater] directly — it owns the full preamble
 * cache and is required by [SessionDetector].
 */
@Deprecated(
  "Use ContextUpdater instead — it owns registration, focused-car-index, and the full preamble cache.",
  ReplaceWith("ContextUpdater(clientContext)"),
)
class RegistrationResultListener(clientContext: ClientContext) :
  MessageListener<AccBroadcastingInbound> {
  private val delegate = ContextUpdater(clientContext)

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) = delegate.onMessage(bytes, message, messageSender)
}
