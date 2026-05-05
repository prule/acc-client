package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("DEPRECATION")
class RegistrationResultListenerTest {

  private val clientState = ClientState()
  private val listener = RegistrationResultListener(clientState)
  private val mockSender: MessageSender = mock()
  private val bytes = byteArrayOf(1, 2, 3)

  @Test
  fun `should update client state on successful registration`() {
    val mockInbound: AccBroadcastingInbound = mock()
    val mockResult: AccBroadcastingInbound.RegistrationResult = mock()

    whenever(mockInbound.msgType())
      .thenReturn(AccBroadcastingInbound.InboundMsgType.REGISTRATION_RESULT)
    whenever(mockInbound.body()).thenReturn(mockResult)
    whenever(mockResult.connectionId()).thenReturn(42)

    listener.onMessage(bytes, mockInbound, mockSender)

    assertThat(clientState.connectionId).isEqualTo(42)
    // Wrapper now also requests entry list + track data from the server.
    verify(mockSender, atLeast(1)).send(any())
  }
}
