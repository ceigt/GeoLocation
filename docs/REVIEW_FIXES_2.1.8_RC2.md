# GeoLocation 2.1.8-rc2 candidate

Based on 2.1.8-rc1 (`f7696ab`). Scope: follow-up review items 1–3 and the corresponding tests from item 7. The candidate was subsequently installed and tested on the attached device with user authorization, as recorded below. Alipay investigation remains paused. No GitHub release has been published.

## Changes

1. Prepare supplemental payloads, availability notifications and GNSS initialization before reserving a finite request's delivery count or minimum-distance state. Preparation failures retain the existing bounded retry. Once a callback is attempted, its allowance is not refunded: Binder failure does not prove that the client received nothing. Original deadlines still apply.
2. System supplements query Android's foreground-location capability instead of treating every service without a visible Activity as background. The existing permissions, stored AppOps, user restrictions and suspension checks remain required. Unsupported capability queries retain the conservative visible-Activity/background-permission fallback. Application supplements permit a foreground service only when a while-in-use operation is actually authorized; service importance alone is insufficient.
3. Guard all four native `IGnssStatusListener` events for tracked simulation recipients, while allowing the module's own events. Track GNSS listeners before native registration can synchronously call them, and restore the prior entry when native registration fails. All four guards must install before GNSS supplementation is enabled. Partial/unsupported installs retain native GNSS without adding a synthetic sky. System and manager registrations remain untracked and pass through.

## Validation

- Debug and Release unit suites: 88 tests each, zero failures/errors/skips (176 executions).
- Release lint: zero errors, 34 warnings and one informational item. The additional warning is the intentional GNSS Proxy reflection adapter. Existing warnings include other framework reflection, application-context storage, dependencies and UI suggestions.
- App instrumentation APK, external regression client and its instrumentation APK compiled successfully. The app-internal instrumentation suite remains unexecuted; external device results are recorded below.
- Release packaging completed successfully. The final build used a temporary JVM override with the same 1200 MB heap limit and normal tiered compilation; project JVM configuration is unchanged.
- Signed candidate: `outputs/GeoLocation-2.1.8-rc2-release.apk` in the task workspace, versionCode `20108`, non-debuggable, verified against existing certificate SHA-256 `26ffd7b501c54b76a7a39495105cf0f8e596a56f9fca88ec2111f230e19fb5f7`.
- APK SHA-256: `5a32054af82210c457d4ccfbe75970f31a195e272cf3c1fe147a5df530690eab`. A companion `.apk.sha256` file is provided.

Added unit cases exercise preparation failure/retry, finite counts, no refund after callback attempt, expiration, GNSS source filtering, foreground-service authorization and stored denial. The production delivery path uses the same preparation/admission helper tested by those cases.

Added external instrumentation cases:

- A real location-type foreground service starts while its Activity is visible, then continues requesting updates after the Activity goes to the background. The probe declares no background-location permission. The test rejects environments where the Activity remains foreground, so they cannot produce a false pass.
- GNSS callbacks must contain only the simulated satellite set, with one start/first-fix event, while simulation remains active. Run separately with real location enabled and disabled. Stopping simulation and unregistering also need device verification.

## Device follow-up: 2026-10-19

The signed candidate was installed over the existing app and the phone rebooted. The installed APK hash matches the candidate above. Android 16 / API 36 was used, with only `system`, `android` and `com.android.phone` in the module's LSPosed scope. Simulation was explicitly started at the saved point before checking target apps. The selected point was unchanged at the end.

| App | Observed UI | Wi-Fi on, system location on | Wi-Fi on, system location off |
|---|---|---|---|
| WeChat | Location picker, cancelled without sending | Selected region displayed | Selected region displayed |
| WeCom | Attendance map, no attendance submitted | Selected region displayed | Selected region displayed |
| AMap | Current-location map and recenter | Selected region displayed | Selected region displayed |
| JD | Instant-delivery address page / relocate | Selected region displayed | Selected region displayed |
| Meituan | Current location / relocate | Selected region displayed | Selected region displayed |
| Taobao | Flash-purchase current location / relocate | Selected region displayed | Selected region displayed |

These are observations of the named pages, not measurements of each app's coordinate error or guarantees about every feature. Warm re-entry/relocation was included; this is a short session rather than a long-duration reliability run.

The external client's four cases (single update, finite subsecond request termination, location FGS without background-location permission, and GNSS event/source consistency) passed in all four Wi-Fi/system-location combinations: **16 executions, zero failures**. The single-update and FGS cases compare returned fixes to the selected WGS84 point within 200 m; GNSS checks the simulated satellite signature and event counts.

The first external-client install exposed an existing test-manifest issue: an attribution label must reference a string resource. Changing the test manifest to `@string/regression_attribution` and adding that resource fixed installation. The client was rebuilt, installed, tested and removed along with its instrumentation package. This did not change the installed manager APK.

With Wi-Fi disabled, an additional WeCom warm-entry attempt displayed "network connection unavailable". Fresh third-party UI results in that condition are therefore **inconclusive**, and cached map/address content is not counted as a pass. The global and per-subscription mobile-data settings differ on this device; none of them or the VPN configuration were changed. The four-case native callback suites still passed with Wi-Fi disabled.

Final module logs contain zero callback-adapter failures, exceptions, unresolved native callback warnings or fatal entries; the crash buffer is empty and the system-server PID remained unchanged throughout the session. All four GNSS Binder guards installed. One older `LocationManagerService$Receiver` class-probe warning is present; the current provider-registration and transport adapters installed successfully. Private raw logs/screenshots remain local and are not committed.

Wi-Fi and the physical location switch were restored to their original enabled states, simulation remains enabled, and both temporary test packages were removed. Alipay was not tested.

## Compatibility limits and next field checks

- App-only mode cannot query the privileged UID capability API. With the real location switch off, an app whose FGS authorization cannot be established remains blocked from supplemental callbacks. System mode can establish this capability independently; no extra permission is granted or requested.
- GNSS Proxy hooks and UID capability access passed the recorded tests on this Android 16 device; other ROMs and Android versions remain unverified.
- Extend the six-app UI checks to a confirmed working cellular connection with Wi-Fi off, longer repeated entry, application-only mode, simulation stop/start and quick-setting tiles. Those checks are not established by the short session above.
- Mock Provider coverage, Tencent metadata, null cached locations, broader hot-path optimization and signing-CI cleanup remain outside this candidate.

Android reference: [process capability API](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/ActivityManager.java), [UID capability permission checks](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/am/ActivityManagerService.java), [AppOps foreground evaluation contract](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/AppOpsManager.java).
