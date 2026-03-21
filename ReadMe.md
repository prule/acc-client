# Assetto Corsa Competizione (ACC) Client

A Kotlin client for Assetto Corsa Competizione (ACC) Dedicated Server UDP communication.

> This library depends on https://github.com/prule/acc-messages

> Configuring ACC:
> In `C:\Users\<username>\Documents\Assetto Corsa Competizione\Config\broadcasting.json` you can find the broadcasting configuration - you'll need the port and connection password from here:
> ```json
> {                                                                                                                         
>   "updListenerPort": 9000,
>   "connectionPassword": "asd",
>   "commandPassword": ""
> }
> ```
> For information on ACC broadcasting and the ACC Broadcasting Test Client see https://github.com/prule/acc-messages/blob/main/docs/AccBroadcastingExample/ReadMe.md

## Features

- **Connect to ACC Server**: Communicates with the ACC server via the UDP protocol.
- **Message Parsing**: Reads and parses ACC broadcasting messages (e.g. realtime updates, entry list, track data, etc.).
- **Logging**: Provides listeners (like `LoggingListener`) to output formatted data (JSON).
- **Recording**: Uses `CsvWriterListener` to save received events to a CSV file for analysis or replay.
- **Simulator**: Includes a simulator/playback feature (`AccSimulator`) that reads recorded CSV files and replays ACC events without needing a running ACC server (useful for development and
  debugging).
- **Car & Track Models**: Includes repositories to handle ACC car models and track data.

## Example use

Install the jar locally so other projects can use it:

```shell
./gradlew clean publishToMavenLocal
```

Configure listeners and connect:

```kotlin
AccClient(
    AccClientConfiguration(
        "Test",
        port = 9000,
        serverIp = "127.0.0.1",
    )
).connect(
    listOf(
        LoggingListener(),
        CsvWriterListener(java.nio.file.Path.of("./recordings")),
        RegistrationResultListener()
    )
)
```

The simulator can be started programmatically using

```kotlin
    AccSimulator(
    AccSimulatorConfiguration(
        port = 9000,
        connectionPassword = "asd",
        playbackEventsFile = ClasspathSource("./playback-events.csv"),
    ),
).start()
```

## Demonstration

Run AccSimulator which pretends to be ACC and sends pre-recorded UDP packets to the client.

```shell
./gradlew runAccSimulator
```

```text
> Task :io.github.prule.acc.client.simulator.AccSimulatorKt.main()
22:01:14.834 [main] DEBUG io.github.prule.acc.client.simulator.AccSimulator -- Starting simulator
```

----

Run AccClient

```shell
./gradlew runAccClient
```

The client will register with the simulator and then start receiving packets as the simulator sends them:

```text
> Task :io.github.prule.acc.client.AccClientKt.main()
22:23:37.876 [main] DEBUG i.g.p.acc.client.CsvWriterListener -- Writing simulator-recording-2026-03-16T22-23-37.857031.csv
22:23:37.877 [main] DEBUG i.github.prule.acc.client.AccClient -- Connecting to server
22:23:37.886 [main] DEBUG i.github.prule.acc.client.AccClient -- Sent register command, listening for data
22:23:38.161 [main] DEBUG i.g.prule.acc.client.LoggingListener -- Received bytes: 010500000001010000 {"msgType":"REGISTRATION_RESULT","body":{"connectionId":5,"connectionSuccess":1,"isReadOnly":1,"errorMessage":{"length":0,"data":""}}}
22:23:38.162 [main] DEBUG i.g.p.a.c.RegistrationResultListener -- Sending entry list request
22:23:38.162 [main] DEBUG i.g.prule.acc.client.MessageSender -- Sending bytes: 0a05000000
22:23:38.163 [main] DEBUG i.g.p.a.c.RegistrationResultListener -- Sending track data request
22:23:38.163 [main] DEBUG i.g.prule.acc.client.MessageSender -- Sending bytes: 0b05000000
22:23:38.218 [main] DEBUG i.g.prule.acc.client.LoggingListener -- Received bytes: 02000000000a0504988b49f00bae480d00000008004472697661626c650700436f636b706974090042617369632048554400ba9657471e26000000d96b01000500000003d15b0000bc9f00004b70000000010000 {"msgType":"REALTIME_UPDATE","body":{"eventIndex":0,"sessionIndex":0,"sessionType":null,"phase":"SESSION","sessionTimeMs":1143552.5,"sessionEndTimeMs":356447.5,"focusedCarIndex":13,"activeCameraSet":{"length":8,"data":"Drivable"},"activeCamera":{"length":7,"data":"Cockpit"},"currentHudPage":{"length":9,"data":"Basic HUD"},"isReplayPlaying":0,"replaySessionTime":null,"replayRemainingTime":null,"timeOfDaySeconds":55190.727,"ambientTemp":30,"trackTemp":38,"clouds":0,"rainLevel":0,"wetness":0,"bestSessionLap":{"lapTimeMs":93145,"carIndex":5,"driverIndex":0,"numSplits":3,"splits":[23505,40892,28747],"isInvalid":0,"isValidForBest":1,"isOutlap":0,"isInlap":0}}}
22:23:38.721 [main] DEBUG i.g.prule.acc.client.LoggingListener -- Received bytes: 030000000001031948d6c2675fb34317642240017800010001000000f623f33d0c00fbffffffff6d01000000000003fe5b0000c8a0000007710000000100004070010000000000035f5c000054a100008d7200000001000032280000000000000000010000 {"msgType":"REALTIME_CAR_UPDATE","body":{"carIndex":0,"driverIndex":0,"driverCount":1,"gear":3,"worldPosX":-107.140816,"worldPosY":358.74533,"yaw":2.537359,"carLocation":"TRACK","kmh":120,"position":1,"cupPosition":1,"trackPosition":0,"splinePosition":0.118720934,"laps":12,"delta":-5,"bestSessionLap":{"lapTimeMs":93695,"carIndex":0,"driverIndex":0,"numSplits":3,"splits":[23550,41160,28935],"isInvalid":0,"isValidForBest":1,"isOutlap":0,"isInlap":0},"lastLap":{"lapTimeMs":94272,"carIndex":0,"driverIndex":0,"numSplits":3,"splits":[23647,41300,29325],"isInvalid":0,"isValidForBest":1,"isOutlap":0,"isInlap":0},"currentLap":{"lapTimeMs":10290,"carIndex":0,"driverIndex":0,"numSplits":0,"splits":[],"isInvalid":0,"isValidForBest":1,"isOutlap":0,"isInlap":0}}}
22:23:39.217 [main] DEBUG i.g.prule.acc.client.LoggingListener -- Received bytes: 0412000000190000000400020005000100070006000a00080009000c000f0010000e0003001300180012000b0014001700150016000d001100 {"msgType":"ENTRY_LIST","body":{"connectionId":18,"numCarIndexes":25,"carIndexes":[0,4,2,5,1,7,6,10,8,9,12,15,16,14,3,19,24,18,11,20,23,21,22,13,17]}}
22:23:39.729 [main] DEBUG i.g.prule.acc.client.LoggingListener -- Received bytes: 05120000000d005265642042756c6c2052696e671a000000de1000000708004472697661626c650705004368617365080046617243686173650600426f6e6e657407004461736850726f0700436f636b706974040044617368060048656c6d6574070048656c6963616d01070048656c6963616d07004f6e626f6172640408004f6e626f6172643008004f6e626f6172643108004f6e626f6172643208004f6e626f6172643307007069746c616e65020c007069746c616e655f43616d310c007069746c616e655f43616d320400736574310d0900536574315f43616d330900536574315f43616d340900536574315f43616d350900536574315f43616d360900536574315f43616d370900536574315f43616d390a00536574315f43616d31300a00536574315f43616d31310a00536574315f43616d31320a00536574315f43616d31330d00536574315f43616d31345f31340900536574315f43616d310900536574315f43616d320400736574320a0900536574325f43616d330900536574325f43616d340900536574325f43616d350900536574325f43616d360900536574325f43616d370900536574325f43616d380900536574325f43616d390a00536574325f43616d31300900536574325f43616d310900536574325f43616d32050073657456520b090043616d657261565231090043616d657261565232090043616d657261565233090043616d657261565234090043616d657261565235090043616d657261565236090043616d657261565237090043616d657261565238090043616d6572615652390a0043616d657261565231300a0043616d65726156523131060500426c616e6b0900426173696320485544040048656c70090054696d655461626c650c0042726f616463617374696e670800547261636b4d6170 {"msgType":"TRACK_DATA","body":{"connectionId":18,"trackName":{"length":13,"data":"Red Bull Ring"},"trackId":26,"trackMeters":4318,"numCameraSets":7,"cameraSets":[{"cameraSetName":{"length":8,"data":"Drivable"},"numCameras":7,"cameras":[{"length":5,"data":"Chase"},{"length":8,"data":"FarChase"},{"length":6,"data":"Bonnet"},{"length":7,"data":"DashPro"},{"length":7,"data":"Cockpit"},{"length":4,"data":"Dash"},{"length":6,"data":"Helmet"}]},{"cameraSetName":{"length":7,"data":"Helicam"},"numCameras":1,"cameras":[{"length":7,"data":"Helicam"}]},{"cameraSetName":{"length":7,"data":"Onboard"},"numCameras":4,"cameras":[{"length":8,"data":"Onboard0"},{"length":8,"data":"Onboard1"},{"length":8,"data":"Onboard2"},{"length":8,"data":"Onboard3"}]},{"cameraSetName":{"length":7,"data":"pitlane"},"numCameras":2,"cameras":[{"length":12,"data":"pitlane_Cam1"},{"length":12,"data":"pitlane_Cam2"}]},{"cameraSetName":{"length":4,"data":"set1"},"numCameras":13,"cameras":[{"length":9,"data":"Set1_Cam3"},{"length":9,"data":"Set1_Cam4"},{"length":9,"data":"Set1_Cam5"},{"length":9,"data":"Set1_Cam6"},{"length":9,"data":"Set1_Cam7"},{"length":9,"data":"Set1_Cam9"},{"length":10,"data":"Set1_Cam10"},{"length":10,"data":"Set1_Cam11"},{"length":10,"data":"Set1_Cam12"},{"length":10,"data":"Set1_Cam13"},{"length":13,"data":"Set1_Cam14_14"},{"length":9,"data":"Set1_Cam1"},{"length":9,"data":"Set1_Cam2"}]},{"cameraSetName":{"length":4,"data":"set2"},"numCameras":10,"cameras":[{"length":9,"data":"Set2_Cam3"},{"length":9,"data":"Set2_Cam4"},{"length":9,"data":"Set2_Cam5"},{"length":9,"data":"Set2_Cam6"},{"length":9,"data":"Set2_Cam7"},{"length":9,"data":"Set2_Cam8"},{"length":9,"data":"Set2_Cam9"},{"length":10,"data":"Set2_Cam10"},{"length":9,"data":"Set2_Cam1"},{"length":9,"data":"Set2_Cam2"}]},{"cameraSetName":{"length":5,"data":"setVR"},"numCameras":11,"cameras":[{"length":9,"data":"CameraVR1"},{"length":9,"data":"CameraVR2"},{"length":9,"data":"CameraVR3"},{"length":9,"data":"CameraVR4"},{"length":9,"data":"CameraVR5"},{"length":9,"data":"CameraVR6"},{"length":9,"data":"CameraVR7"},{"length":9,"data":"CameraVR8"},{"length":9,"data":"CameraVR9"},{"length":10,"data":"CameraVR10"},{"length":10,"data":"CameraVR11"}]}],"numHudPages":6,"hudPages":[{"length":5,"data":"Blank"},{"length":9,"data":"Basic HUD"},{"length":4,"data":"Help"},{"length":9,"data":"TimeTable"},{"length":12,"data":"Broadcasting"},{"length":8,"data":"TrackMap"}]}}
22:23:40.224 [main] DEBUG i.g.prule.acc.client.LoggingListener -- Received bytes: 060000010c00426c61636b2046616c636f6e04000000000002000104004c756361050053746f6c7a030053544f010200 {"msgType":"ENTRY_LIST_CAR","body":{"carId":0,"carModelType":1,"teamName":{"length":12,"data":"Black Falcon"},"raceNumber":4,"cupCategory":"OVERALL_PRO","driverIndex":0,"nationality":2,"numDrivers":1,"drivers":[{"firstName":{"length":4,"data":"Luca"},"lastName":{"length":5,"data":"Stolz"},"shortName":{"length":3,"data":"STO"},"category":"SILVER","nationality":2}]}}
22:23:40.733 [main] DEBUG i.g.prule.acc.client.LoggingListener -- Received bytes: 0705090030313a33362e373435f360120006000000 {"msgType":"BROADCASTING_EVENT","body":{"type":"LAPCOMPLETED","msg":{"length":9,"data":"01:36.745"},"timeMs":1204467,"carId":6}}
22:23:42.736 [main] DEBUG i.g.prule.acc.client.MessageReceiver -- Socket timed out. Session ended.
```

Messages received are written to a file as mentioned in the output:

```text
Writing simulator-recording-2026-03-16T22-23-37.857031.csv
```

----
You'll see the simulator send and receive packets:

```text
> Task :io.github.prule.acc.client.simulator.AccSimulatorKt.main()
22:23:27.791 [main] DEBUG io.github.prule.acc.client.simulator.AccSimulator -- Starting simulator
22:23:38.029 [main] DEBUG io.github.prule.acc.client.LoggingListener -- Received bytes: 01040400546573740300617364e80300000300617364 {"msgType":"REGISTER_COMMAND_APPLICATION","body":{"protocolVersion":4,"displayName":{"length":4,"data":"Test"},"connectionPassword":{"length":3,"data":"asd"},"msRealtimeUpdateInterval":1000,"commandPassword":{"length":3,"data":"asd"}}}
22:23:38.029 [main] DEBUG io.github.prule.acc.client.MessageSender -- Sending bytes: 010500000001010000
22:23:38.044 [DefaultDispatcher-worker-1] INFO io.github.prule.acc.client.simulator.PlaybackEventsRepository -- Loading /Users/paulrule/IdeaProjects/acc-client/./playback-events.csv
22:23:38.166 [main] DEBUG io.github.prule.acc.client.LoggingListener -- Received bytes: 0a05000000 {"msgType":"REQUEST_ENTRY_LIST","body":{"connectionId":5}}
22:23:38.167 [main] DEBUG io.github.prule.acc.client.LoggingListener -- Received bytes: 0b05000000 {"msgType":"REQUEST_TRACK_DATA","body":{"connectionId":5}}
22:23:38.207 [DefaultDispatcher-worker-1] DEBUG io.github.prule.acc.client.MessageSender -- Sending bytes: 02000000000a0504988b49f00bae480d00000008004472697661626c650700436f636b706974090042617369632048554400ba9657471e26000000d96b01000500000003d15b0000bc9f00004b70000000010000
22:23:38.711 [DefaultDispatcher-worker-1] DEBUG io.github.prule.acc.client.MessageSender -- Sending bytes: 030000000001031948d6c2675fb34317642240017800010001000000f623f33d0c00fbffffffff6d01000000000003fe5b0000c8a0000007710000000100004070010000000000035f5c000054a100008d7200000001000032280000000000000000010000
22:23:39.213 [DefaultDispatcher-worker-1] DEBUG io.github.prule.acc.client.MessageSender -- Sending bytes: 0412000000190000000400020005000100070006000a00080009000c000f0010000e0003001300180012000b0014001700150016000d001100
22:23:39.715 [DefaultDispatcher-worker-1] DEBUG io.github.prule.acc.client.MessageSender -- Sending bytes: 05120000000d005265642042756c6c2052696e671a000000de1000000708004472697661626c650705004368617365080046617243686173650600426f6e6e657407004461736850726f0700436f636b706974040044617368060048656c6d6574070048656c6963616d01070048656c6963616d07004f6e626f6172640408004f6e626f6172643008004f6e626f6172643108004f6e626f6172643208004f6e626f6172643307007069746c616e65020c007069746c616e655f43616d310c007069746c616e655f43616d320400736574310d0900536574315f43616d330900536574315f43616d340900536574315f43616d350900536574315f43616d360900536574315f43616d370900536574315f43616d390a00536574315f43616d31300a00536574315f43616d31310a00536574315f43616d31320a00536574315f43616d31330d00536574315f43616d31345f31340900536574315f43616d310900536574315f43616d320400736574320a0900536574325f43616d330900536574325f43616d340900536574325f43616d350900536574325f43616d360900536574325f43616d370900536574325f43616d380900536574325f43616d390a00536574325f43616d31300900536574325f43616d310900536574325f43616d32050073657456520b090043616d657261565231090043616d657261565232090043616d657261565233090043616d657261565234090043616d657261565235090043616d657261565236090043616d657261565237090043616d657261565238090043616d6572615652390a0043616d657261565231300a0043616d65726156523131060500426c616e6b0900426173696320485544040048656c70090054696d655461626c650c0042726f616463617374696e670800547261636b4d6170
22:23:40.219 [DefaultDispatcher-worker-1] DEBUG io.github.prule.acc.client.MessageSender -- Sending bytes: 060000010c00426c61636b2046616c636f6e04000000000002000104004c756361050053746f6c7a030053544f010200
22:23:40.725 [DefaultDispatcher-worker-1] DEBUG io.github.prule.acc.client.MessageSender -- Sending bytes: 0705090030313a33362e373435f360120006000000
```