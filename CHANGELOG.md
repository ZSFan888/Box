# Changelog

## [Unreleased]

### Added
- QR code scan import via ML Kit BarcodeScanning + CameraX
- Home screen widget with live speed + VPN toggle (3×1)
- Subscription import: URL fetch with HTTP error diagnostics
- Multi-format parser: Clash YAML / vmess / vless / ss / trojan / hy2 / tuic / Base64
- Config converter: Clash → Xray JSON / sing-box JSON
- Per-app proxy (whitelist / blacklist / global)
- Auto-update worker for subscriptions
- Boot receiver for auto-start
- Quick tile service
- Real-time log viewer with keyword filter
- 5-screen UI: Dashboard / Proxies / Settings / Logs / PerApp
- Dark surface UI system with muted log colors
- Dynamic versionCode from CI run number

### Fixed
- AndroidManifest xmlns:tools placement
- Subscription Base64 detection (no padding)
- OkHttp: redirect follow + ClashForAndroid UA
- ProxyWidget: replaced Glance with AppWidgetProvider + RemoteViews
