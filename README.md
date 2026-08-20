# SentinelFS

A Java-based file integrity and tampering detection system. Monitors files and folders using cryptographic hashing, detects unauthorized changes by comparing against trusted baselines, and supports real-time monitoring via OS-level file system events.

## Features

- **Baseline-based integrity checking** — create a trusted snapshot of a file/folder, and detect Modified/Added/Deleted files against it on demand.
- **Real-time monitoring** — uses Java's `WatchService` API to watch directories recursively and fire alerts the instant a file changes, without polling.
- **Log & backup monitoring** — separate baseline tracking for log and backup directories, with extra warnings for missing backups.
- **Alerting system** — built with the Observer pattern; alerts are printed to console and written to a timestamped log file (`alerts.log`).
- **Console-driven menu** — add folders to monitor, run checks, approve baseline updates, and start real-time monitoring, all through a simple CLI.

## Tech Stack

- Java 21
- Maven
- Gson (baseline persistence as JSON)
- JUnit 5 (unit tests)

## How to Run

mvn compile
java -cp "target\classes;target\dependency*" com.sentinelfs.Main


(First run `mvn dependency:copy-dependencies` if `target\dependency` doesn't exist yet.)

## Architecture

com.sentinelfs.core - hashing utility, FileRecord data model
com.sentinelfs.integrity - directory scanning, baseline comparison logic
com.sentinelfs.baseline - JSON persistence for baselines (Gson)
com.sentinelfs.logbackup - log/backup-specific monitoring
com.sentinelfs.alert - Observer-pattern alerting (console + file log)
com.sentinelfs.watch - real-time monitoring via WatchService
com.sentinelfs.ui - console menu controller


The core engine has no dependency on the UI layer — the console controller is just one possible front-end on top of the same detection logic.

## Design Decisions

- **SHA-256 over MD5** — MD5 is fast but not collision-resistant, which matters for an adversarial tampering scenario rather than accidental corruption. SHA-256 is the default; MD5 remains available as a faster option.
- **WatchService over polling** — event-driven OS notifications are far more efficient than a background thread re-scanning on a timer, and give genuinely real-time alerts.
- **Observer pattern for alerting** — decouples *detecting* a change from *reacting* to it. Adding a new alert channel (e.g. email) means writing one new listener class, no changes to detection logic.
- **JSON (Gson) over Java serialization** — human-readable, easy to debug, avoids deserialization security concerns tied to native Java serialization.

## Planned Extensions

- JavaFX desktop UI (FXML + CSS) on top of the existing core logic
- Remote/mobile push notifications via a lightweight backend service
- Configurable file/pattern whitelisting

## License

MIT