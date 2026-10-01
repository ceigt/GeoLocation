# Basic security review

Review date: 2026-10-01 (targeted launcher and WebView follow-up)

## Manifest and permissions

- App-defined exported component during normal operation: launcher `MainActivity`. The merged APK also contains AndroidX/libxposed components; their permissions and access controls must be reviewed separately from the app's source manifest.
- Not exported: `MockLocationService`.
- Optional `ControlReceiver`: exported but disabled in the manifest and enabled only by an explicit setting. The signature-level `io.github.ceigt.geolocation.CONTROL` permission also restricts callers to packages signed with the same certificate (or privileged shell/root callers where permitted by Android).
- Network permissions support the selected Baidu, AMap, or Google web map. Fine/coarse location support “my location” and Mock Provider mode. Foreground-service and notification permissions support Mock Provider. `QUERY_ALL_PACKAGES` is used to show the selectable LSPosed target-app list.
- Cleartext traffic is disabled.

## Data and network

- Settings, favorites and coordinates are stored locally in private preferences or LSPosed remote preferences.
- No analytics, subscription, advertising, updater, dynamic-code download or remote-control service was added.
- Map display, search and reverse geocoding load only the selected provider's JavaScript API and therefore send ordinary map requests to Baidu, AMap, or Google.
- Provider API keys and AMap `securityJsCode` are stored in the app's private local preferences and injected only into the selected provider page. They are not included in logs or remote preferences.
- Map WebViews now replace their displayed instance when provider/credentials change, invalidate callbacks on disposal, and recover once after renderer termination. Repeated termination displays an explicit Retry action instead of an automatic reload loop. These changes do not grant additional WebView capabilities or log provider credentials.

## Residual risks

- Xposed system hooks run with broad privilege and can destabilize a ROM; application-level scope is the recommended default.
- Location hooks depend on Android/ROM internals and require real-device tests for each supported Android/LSPosed combination.
- A development-signed release cannot be upgraded to a build signed with a different key without uninstalling it first.
- Debug no longer uses the formal release key when a local signing configuration exists. Candidate upgrade APKs must be non-debuggable release builds signed with the original formal certificate.
- On the connected Android 16 PJE110, the installed 2.1.6 APK certificate matched the release workflow's expected SHA-256 fingerprint. The retained process-exit history showed task removal and WebView memory-pressure reclamation, with no app CRASH/ANR entries. This is a bounded observation, not an exhaustive ROM or hook compatibility claim.
