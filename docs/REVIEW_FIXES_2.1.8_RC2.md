# GeoLocation 2.1.8-rc2 candidate

Based on 2.1.8-rc1 (`f7696ab`). Scope: follow-up review items 1–3 and the corresponding tests from item 7. Alipay investigation remains paused. No installation, reboot or GitHub release is part of this change.

## Changes

1. Prepare supplemental payloads, availability notifications and GNSS initialization before reserving a finite request's delivery count or minimum-distance state. Preparation failures retain the existing bounded retry. Once a callback is attempted, its allowance is not refunded: Binder failure does not prove that the client received nothing. Original deadlines still apply.
2. System supplements query Android's foreground-location capability instead of treating every service without a visible Activity as background. The existing permissions, stored AppOps, user restrictions and suspension checks remain required. Unsupported capability queries retain the conservative visible-Activity/background-permission fallback. Application supplements permit a foreground service only when a while-in-use operation is actually authorized; service importance alone is insufficient.
3. Guard all four native `IGnssStatusListener` events for tracked simulation recipients, while allowing the module's own events. Track GNSS listeners before native registration can synchronously call them, and restore the prior entry when native registration fails. All four guards must install before GNSS supplementation is enabled. Partial/unsupported installs retain native GNSS without adding a synthetic sky. System and manager registrations remain untracked and pass through.

## Validation

- Debug and Release unit suites: 88 tests each, zero failures/errors/skips (176 executions).
- Release lint: zero errors, 34 warnings and one informational item. The additional warning is the intentional GNSS Proxy reflection adapter. Existing warnings include other framework reflection, application-context storage, dependencies and UI suggestions.
- App instrumentation APK, external regression client and its instrumentation APK compiled successfully. These device tests have not been executed.
- Release packaging completed successfully. The final build used a temporary JVM override with the same 1200 MB heap limit and normal tiered compilation; project JVM configuration is unchanged.
- Signed candidate: `outputs/GeoLocation-2.1.8-rc2-release.apk` in the task workspace, versionCode `20108`, non-debuggable, verified against existing certificate SHA-256 `26ffd7b501c54b76a7a39495105cf0f8e596a56f9fca88ec2111f230e19fb5f7`.
- APK SHA-256: `5a32054af82210c457d4ccfbe75970f31a195e272cf3c1fe147a5df530690eab`. A companion `.apk.sha256` file is provided.

Added unit cases exercise preparation failure/retry, finite counts, no refund after callback attempt, expiration, GNSS source filtering, foreground-service authorization and stored denial. The production delivery path uses the same preparation/admission helper tested by those cases.

Added external instrumentation cases:

- A real location-type foreground service starts while its Activity is visible, then continues requesting updates after the Activity goes to the background. The probe declares no background-location permission. The test rejects environments where the Activity remains foreground, so they cannot produce a false pass.
- GNSS callbacks must contain only the simulated satellite set, with one start/first-fix event, while simulation remains active. Run separately with real location enabled and disabled. Stopping simulation and unregistering also need device verification.

## Compatibility limits and next field checks

- App-only mode cannot query the privileged UID capability API. With the real location switch off, an app whose FGS authorization cannot be established remains blocked from supplemental callbacks. System mode can establish this capability independently; no extra permission is granted or requested.
- ROM-specific GNSS Proxy hooks and UID capability access still require device verification. Installation logs should show `Native IGnssStatusListener Binder guards installed (4/4)` before claiming source consistency on a device.
- Repeat WeChat/WeCom/AMap/JD/Meituan/Taobao cold-start and warm re-entry with Wi-Fi and system location on/off, plus start/stop and quick-setting tiles. Existing field success for rc1 does not validate rc2.
- Mock Provider coverage, Tencent metadata, null cached locations, broader hot-path optimization and signing-CI cleanup remain outside this candidate.

Android reference: [process capability API](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/ActivityManager.java), [UID capability permission checks](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/am/ActivityManagerService.java), [AppOps foreground evaluation contract](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/AppOpsManager.java).
