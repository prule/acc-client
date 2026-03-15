# Assetto Corsa Competizione (ACC) Client

A Kotlin client for Assetto Corsa Competizione (ACC) Dedicated Server UDP communication.

## Features

- **Connect to ACC Server**: Communicates with the ACC server via the UDP protocol.
- **Message Parsing**: Reads and parses ACC broadcasting messages (e.g. realtime updates, entry list, track data, etc.).
- **Logging**: Provides listeners (like `LoggingListener`) to output formatted data (JSON).
- **Recording**: Uses `CsvWriterListener` to save received events to a CSV file for analysis or replay.
- **Simulator**: Includes a simulator/playback feature (`AccSimulator`) that reads recorded CSV files and replays ACC events without needing a running ACC server (useful for development and
  debugging).
- **Car & Track Models**: Includes repositories to handle ACC car models and track data.

