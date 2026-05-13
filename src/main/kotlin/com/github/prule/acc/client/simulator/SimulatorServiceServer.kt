package com.github.prule.acc.client.simulator

import com.example.simulator.grpc.SessionResponse
import com.example.simulator.grpc.SimulatorServiceGrpcKt
import com.example.simulator.grpc.StartSessionRequest
import com.example.simulator.grpc.StopSessionRequest
import com.sun.security.ntlm.Server
import io.grpc.ServerBuilder
import java.util.*
import java.util.concurrent.TimeUnit

class SimulatorServiceServer(private val port: Int) {
  private val server: Server = ServerBuilder.forPort(port).addService(SimulatorService()).build()

  fun start() {
    server.start()
    println("Server started, listening on $port")
    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          println("*** shutting down gRPC server since JVM is shutting down")
          this@SimulatorServiceServer.stop()
          println("*** server shut down")
        }
      )
  }

  fun stop() {
    server.shutdown().awaitTermination(5, TimeUnit.SECONDS)
  }

  fun blockUntilShutdown() {
    server.awaitTermination()
  }

  private class SimulatorService : SimulatorServiceGrpcKt.SimulatorServiceCoroutineImplBase() {
    private var currentSessionId: String? = null

    override suspend fun startSession(request: StartSessionRequest): SessionResponse {
      println("Received StartSession request: filePath=${request.filePath}")

      // If a session is already running, stop it first
      currentSessionId?.let {
        println("Stopping existing session: $it")
        // In a real implementation, you'd add logic here to gracefully stop the session
        currentSessionId = null
      }

      val newSessionId = UUID.randomUUID().toString()
      currentSessionId = newSessionId
      println("Started new session: $newSessionId with filePath: ${request.filePath}")

      return SessionResponse.newBuilder()
        .setSuccess(true)
        .setMessage("Session $newSessionId started successfully with filePath: ${request.filePath}")
        .setSessionId(newSessionId)
        .build()
    }

    override suspend fun stopSession(request: StopSessionRequest): SessionResponse {
      println("Received StopSession request.")
      return if (currentSessionId != null) {
        val stoppedSessionId = currentSessionId
        currentSessionId = null
        println("Stopped session: $stoppedSessionId")
        SessionResponse.newBuilder()
          .setSuccess(true)
          .setMessage("Session $stoppedSessionId stopped successfully.")
          .setSessionId(stoppedSessionId)
          .build()
      } else {
        println("No active session to stop.")
        SessionResponse.newBuilder()
          .setSuccess(false)
          .setMessage("No active session to stop.")
          .build()
      }
    }
  }
}

fun main() {
  val port = 50051
  val server = SimulatorServiceServer(port)
  server.start()
  server.blockUntilShutdown()
}
