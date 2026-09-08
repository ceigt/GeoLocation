# Basic security review

Review date: 2026-09-08

## Manifest and permissions

- Exported: launcher `MainActivity` only during normal operation.
- Not exported: `MockLocationService`.
- Optional `ControlReceiver`: exported but disabled in the manifest and enabled only by an explicit setting. It has no authentication after opt-in, so keep it off except on controlled automation devices.
- Network permissions support the Baidu web map. Fine/coarse location support “my location” and Mock Provider mode. Foreground-service and notification permissions support Mock Provider. `QUERY_ALL_PACKAGES` is used to show the selectable LSPosed target-app list.
- Cleartext traffic is disabled.

## Data and network

- Settings, favorites and coordinates are stored locally in private preferences or LSPosed remote preferences.
- No analytics, subscription, advertising, updater, dynamic-code download or remote-control service was added.
- Map display, search and reverse geocoding load Baidu Map JavaScript API and therefore send ordinary map requests to Baidu.

## Residual risks

- Xposed system hooks run with broad privilege and can destabilize a ROM; application-level scope is the recommended default.
- Location hooks depend on Android/ROM internals and require real-device tests for each supported Android/LSPosed combination.
- A development-signed release cannot be upgraded to a build signed with a different key without uninstalling it first.
