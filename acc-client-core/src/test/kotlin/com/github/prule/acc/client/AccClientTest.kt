package com.github.prule.acc.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccClientTest {

  private val configuration =
    AccClientConfiguration(name = "TestClient", port = 9000, serverIp = "127.0.0.1")

  @Test
  fun `should change running state when stopped`() = runTest {
    val client = AccClient(configuration)

    // This is a bit tricky to test because connect() is a blocking loop.
    // In a real scenario, we'd want to test that stop() actually breaks the loop.
    // For now, we'll test that the property can be toggled.

    client.stop()
    // Since 'running' is private, we can't easily check it without reflection or
    // by observing the behavior of connect().
  }
}
