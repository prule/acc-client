package com.github.prule.acc.client.simulator.grpc.client

import com.github.prule.acc.client.simulator.grpc.SimulatorGrpcKt
import com.github.prule.acc.client.simulator.grpc.StartRequest
import com.github.prule.acc.client.simulator.grpc.StatusRequest
import com.github.prule.acc.client.simulator.grpc.StatusResponse
import com.github.prule.acc.client.simulator.grpc.StopRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Thin Kotlin wrapper around the generated coroutine stub. Embed this in any JVM app that needs to
 * control the simulator over gRPC.
 *
 * ```kotlin
 *   SimulatorGrpcClient.connect("localhost", 50051).use { client ->
 *     client.start(playbackEventsFile = "/path/to/events.csv")
 *     // ... do work ...
 *     client.stop()
 *   }
 * ```
 */
class SimulatorGrpcClient
internal constructor(
  private val channel: ManagedChannel,
  private val stub: SimulatorGrpcKt.SimulatorCoroutineStub,
) : Closeable {

  suspend fun start(
    playbackEventsFile: String,
    port: Int? = null,
    connectionPassword: String? = null,
    delayMs: Long? = null,
    maxEvents: Int? = null,
    onlyPlayerEvents: Boolean? = null,
  ): StatusResponse {
    val req =
      StartRequest.newBuilder()
        .setPlaybackEventsFile(playbackEventsFile)
        .apply {
          port?.let { setPort(it) }
          connectionPassword?.let { setConnectionPassword(it) }
          delayMs?.let { setDelayMs(it) }
          maxEvents?.let { setMaxEvents(it) }
          onlyPlayerEvents?.let { setOnlyPlayerEvents(it) }
        }
        .build()
    return stub.start(req)
  }

  suspend fun stop(): StatusResponse = stub.stop(StopRequest.getDefaultInstance())

  suspend fun status(): StatusResponse = stub.status(StatusRequest.getDefaultInstance())

  override fun close() {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
  }

  companion object {
    fun connect(host: String, port: Int): SimulatorGrpcClient {
      val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
      val stub = SimulatorGrpcKt.SimulatorCoroutineStub(channel)
      return SimulatorGrpcClient(channel, stub)
    }
  }
}
