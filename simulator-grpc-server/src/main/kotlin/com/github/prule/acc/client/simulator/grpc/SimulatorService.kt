package com.github.prule.acc.client.simulator.grpc

import com.github.prule.acc.client.simulator.AccSimulator
import com.github.prule.acc.client.simulator.AccSimulatorConfiguration
import com.github.prule.acc.client.simulator.FileSource
import org.slf4j.LoggerFactory

/**
 * gRPC service that manages a single [AccSimulator] instance. Starting while one is running stops
 * the existing session first.
 *
 * Holds [defaults] as the configuration template; [StartRequest] fields override individual values.
 */
class SimulatorService(private val defaults: AccSimulatorConfiguration) :
  SimulatorGrpcKt.SimulatorCoroutineImplBase() {

  private val logger = LoggerFactory.getLogger(javaClass)
  private val lock = Any()
  private var current: AccSimulator? = null
  private var currentConfig: AccSimulatorConfiguration? = null

  override suspend fun start(request: StartRequest): StatusResponse {
    require(request.playbackEventsFile.isNotBlank()) { "playback_events_file is required" }
    val config = merge(defaults, request)
    synchronized(lock) {
      stopLocked()
      logger.info(
        "Starting simulator on port {} with file {}",
        config.port,
        request.playbackEventsFile,
      )
      val sim = AccSimulator(config)
      sim.start()
      current = sim
      currentConfig = config
    }
    return status()
  }

  override suspend fun stop(request: StopRequest): StatusResponse {
    synchronized(lock) { stopLocked() }
    return status()
  }

  override suspend fun status(request: StatusRequest): StatusResponse = status()

  /** Shut down any running simulator. Called when the gRPC server is itself shutting down. */
  fun shutdown() {
    synchronized(lock) { stopLocked() }
  }

  private fun stopLocked() {
    val sim = current ?: return
    logger.info("Stopping simulator on port {}", currentConfig?.port)
    sim.stop()
    current = null
    currentConfig = null
  }

  private fun status(): StatusResponse {
    val (running, cfg) = synchronized(lock) { (current?.isRunning() == true) to currentConfig }
    val builder = StatusResponse.newBuilder()
    if (running && cfg != null) {
      builder.state = StatusResponse.State.RUNNING
      builder.running =
        RunningConfig.newBuilder()
          .setPlaybackEventsFile(filePathOf(cfg))
          .setPort(cfg.port)
          .setConnectionPassword(cfg.connectionPassword)
          .setDelayMs(cfg.delay)
          .setMaxEvents(cfg.maxEvents)
          .setOnlyPlayerEvents(cfg.onlyPlayerEvents)
          .build()
    } else {
      builder.state = StatusResponse.State.STOPPED
    }
    return builder.build()
  }

  private fun filePathOf(cfg: AccSimulatorConfiguration): String =
    (cfg.playbackEventsFile as? FileSource)?.path ?: cfg.playbackEventsFile.toString()

  private fun merge(
    defaults: AccSimulatorConfiguration,
    req: StartRequest,
  ): AccSimulatorConfiguration =
    defaults.copy(
      port = if (req.hasPort()) req.port else defaults.port,
      connectionPassword =
        if (req.hasConnectionPassword()) req.connectionPassword else defaults.connectionPassword,
      playbackEventsFile = FileSource(req.playbackEventsFile),
      delay = if (req.hasDelayMs()) req.delayMs else defaults.delay,
      maxEvents = if (req.hasMaxEvents()) req.maxEvents else defaults.maxEvents,
      onlyPlayerEvents =
        if (req.hasOnlyPlayerEvents()) req.onlyPlayerEvents else defaults.onlyPlayerEvents,
    )
}
