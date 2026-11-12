# Changelog

## 1.0.0
- New desktop app (dashboard, protected folders, alerts, settings) with system-tray support and desktop notifications.
- Continuous real-time monitoring (replaces the 30-second demo): recursive, debounced, compares against the baseline, so edit-then-restore raises no alert.
- Baselines are sealed with HMAC-SHA256; editing a baseline to hide a change is detected.
- Per-folder baselines stored in `~/.sentinelfs`; folder types (General / Logs / Backups) set alert severity.
- Ignore patterns (globs), scheduled safety-net scans, persistent alert history, CSV export.
- Console mode kept: `java -jar SentinelFS-<version>.jar --cli`.
- Single runnable jar (`mvn package`) and GitHub Actions for CI and tagged releases.
