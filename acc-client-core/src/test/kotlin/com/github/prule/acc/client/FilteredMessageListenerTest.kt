package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound
import io.kaitai.struct.KaitaiStruct
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class FilteredMessageListenerTest {

  private val mockSender: MessageSender = mock()
  private val bytes = byteArrayOf(1, 2, 3)

  // A real subclass of KaitaiStruct to use in tests
  open class TestKaitaiBody(val data: String) : KaitaiStruct(null)

  private val innerListener: MessageListener<TestKaitaiBody> = mock()

  @Test
  fun `should forward message when type matches and filter passes`() {
    val listener =
      FilteredMessageListener(
        clazz = TestKaitaiBody::class,
        filter = { it.data == "match" },
        listeners = listOf(innerListener),
      )

    val body = TestKaitaiBody("match")
    val mockInbound: AccBroadcastingInbound = mock()
    whenever(mockInbound.body()).thenReturn(body)

    listener.onMessage(bytes, mockInbound, mockSender)

    verify(innerListener).onMessage(bytes, body, mockSender)
  }

  @Test
  fun `should not forward message when type matches but filter fails`() {
    val listener =
      FilteredMessageListener(
        clazz = TestKaitaiBody::class,
        filter = { it.data == "match" },
        listeners = listOf(innerListener),
      )

    val body = TestKaitaiBody("mismatch")
    val mockInbound: AccBroadcastingInbound = mock()
    whenever(mockInbound.body()).thenReturn(body)

    listener.onMessage(bytes, mockInbound, mockSender)

    verifyNoInteractions(innerListener)
  }

  @Test
  fun `should not forward message when type does not match`() {
    val listener =
      FilteredMessageListener(
        clazz = TestKaitaiBody::class,
        filter = { true },
        listeners = listOf(innerListener),
      )

    // Different KaitaiStruct subclass
    class OtherKaitaiBody : KaitaiStruct(null)

    val body = OtherKaitaiBody()
    val mockInbound: AccBroadcastingInbound = mock()
    whenever(mockInbound.body()).thenReturn(body)

    listener.onMessage(bytes, mockInbound, mockSender)

    verifyNoInteractions(innerListener)
  }
}
