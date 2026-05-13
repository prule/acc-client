package com.github.prule.acc.client.simulator.grpc

import com.github.prule.acc.client.simulator.AccSimulatorConfiguration
import com.github.prule.acc.client.simulator.FileSource
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Spins up the real [SimulatorService] over in-process gRPC and hits Start/Status/Stop. Uses a tiny
 * (header-only) CSV so the playback thread has something valid to load - we only assert lifecycle
 * transitions here, not UDP behavior.
 */
class SimulatorServiceTest {
  private val cleanup = mutableListOf<() -> Unit>()
  private lateinit var service: SimulatorService

  @AfterEach
  fun tearDown() {
    if (::service.isInitialized) service.shutdown()
    cleanup.reversed().forEach { it() }
  }

  @Test
  fun `start then stop transitions state`(): Unit = runBlocking {
    val stub = newStub()
    val csv = writeTinyCsv()
    val simPort = freePort()

    val started =
      stub.start(
        StartRequest.newBuilder()
          .setPlaybackEventsFile(csv)
          .setPort(simPort)
          .setDelayMs(1000L)
          .build()
      )
    assertThat(started.state).isEqualTo(StatusResponse.State.RUNNING)
    assertThat(started.running.playbackEventsFile).isEqualTo(csv)
    assertThat(started.running.port).isEqualTo(simPort)

    val statusWhileRunning = stub.status(StatusRequest.getDefaultInstance())
    assertThat(statusWhileRunning.state).isEqualTo(StatusResponse.State.RUNNING)

    val stopped = stub.stop(StopRequest.getDefaultInstance())
    assertThat(stopped.state).isEqualTo(StatusResponse.State.STOPPED)
  }

  @Test
  fun `stop when nothing running is a no-op`(): Unit = runBlocking {
    val stub = newStub()
    val response = stub.stop(StopRequest.getDefaultInstance())
    assertThat(response.state).isEqualTo(StatusResponse.State.STOPPED)
  }

  @Test
  fun `second start replaces the first`(): Unit = runBlocking {
    val stub = newStub()
    val csv = writeTinyCsv()
    val firstPort = freePort()
    val secondPort = freePort()

    stub.start(
      StartRequest.newBuilder()
        .setPlaybackEventsFile(csv)
        .setPort(firstPort)
        .setDelayMs(1000L)
        .build()
    )
    val replaced =
      stub.start(
        StartRequest.newBuilder()
          .setPlaybackEventsFile(csv)
          .setPort(secondPort)
          .setDelayMs(1000L)
          .build()
      )
    assertThat(replaced.state).isEqualTo(StatusResponse.State.RUNNING)
    assertThat(replaced.running.port).isEqualTo(secondPort)
  }

  private fun newStub(): SimulatorGrpcKt.SimulatorCoroutineStub {
    val defaults =
      AccSimulatorConfiguration(
        port = freePort(),
        connectionPassword = "test",
        playbackEventsFile = FileSource(""),
      )
    service = SimulatorService(defaults)
    val name = InProcessServerBuilder.generateName()
    val server: Server =
      InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start()
    cleanup += { server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS) }
    val channel: ManagedChannel = InProcessChannelBuilder.forName(name).directExecutor().build()
    cleanup += { channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS) }
    return SimulatorGrpcKt.SimulatorCoroutineStub(channel)
  }

  private fun freePort(): Int = ServerSocket(0).use { it.localPort }

  private fun writeTinyCsv(): String {
    val file = kotlin.io.path.createTempFile(prefix = "simulator-events", suffix = ".csv").toFile()
    file.deleteOnExit()
    file.writeText("time,type,hex\n")
    return file.absolutePath
  }
}
