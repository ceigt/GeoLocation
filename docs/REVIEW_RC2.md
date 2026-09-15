# 2.1.3-rc2: high-priority review fixes

This batch changes high and medium-high findings only. Alipay, Mock altitude/speed support, Tencent BD-09 and the fixed polling interval are out of scope.

## Changes and compatibility

- Removed process-wide Location property hooks. Request/callback payload adapters remain. Historical/user-created Location instances retain their data. Clients that relied exclusively on property hooks need regression testing.
- External CONTROL broadcasts require the module signature permission. Ordinary automation apps and ADB shell are intentionally incompatible; see EXTERNAL_CONTROL.md.
- App register/remove operations are serialized, and failed replacements restore the prior registration. Native callbacks do not acquire the registration transaction lock.
- System listeners are tracked by Binder plus provider; a local interface proxy preserves the original Binder token and carries provider/generation provenance. Native and supplemental callbacks share count/expiry; nested transport/proxy interception counts once. Suppressed native deliveries acknowledge completion. Expired requests are unregistered from their provider only; unsupported cleanup adapters retain their guard and retry at most once per minute.
- Pending configuration writes retry after failure with a 1–30 second backoff. The UI reports sync failure and distinguishes a submitted request from verified target delivery. No target-side heartbeat is claimed.
- Supplemental authorization rechecks evaluated AppOps, stored authorization, background permission, user unlock/quiet mode and policy restrictions. A foreground service alone is not treated as a visible activity. Native null results remain null. This is deliberately conservative and may reduce background/cache-only compatibility; it is not a claim of equivalence to every vendor permission implementation.
- Both Debug and Release unit tests are now part of the draft build pipeline. The independent regression app is built with its own debug certificate, not the production key.

## Validation limits

Compilation and unit tests do not prove Binder/LSPosed/ROM compatibility. Run the independent probe on the signed, minified rc2 after reboot, and repeat with system location off/on. Do not mark device validation complete based on rc1 results. Background/permission-revocation/work-profile and cancellation stress remain required acceptance scenarios.

The probe requires a foreground Activity and fine permission. It never sends location to a server or submits attendance. It does not change the simulation point or LSPosed scope.

## Device commands

Install the signed rc2, reboot, and start simulation with speed/random offset zero. Use WGS84 for the probe. System mode needs only the three system components; app mode additionally needs the probe in scope (restart probe after scope changes).

Install `regression/build/outputs/apk/debug/regression-debug.apk` and `regression/build/outputs/apk/androidTest/debug/regression-debug-androidTest.apk`, then:

```sh
adb shell pm grant io.github.ceigt.geolocation.regression android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant io.github.ceigt.geolocation.regression android.permission.ACCESS_FINE_LOCATION
adb shell am instrument -w -e latitude YOUR_WGS84_LAT -e longitude YOUR_WGS84_LON io.github.ceigt.geolocation.regression.test/androidx.test.runner.AndroidJUnitRunner
```

The suite expects a simulated point within 200 metres, one callback for maxUpdates=1, independent gps/network delivery, no post-cancel callbacks after draining, unchanged historical Location values, and denied CONTROL permission for the differently signed probe. Repeat with the real system location switch off/on. The suite does not automate every app or background policy.

## Local validation — 2026-09-15

- Build completed successfully, including the independent probe and instrumentation APK.
- 35 unit tests passed in Debug and 35 in Release; zero failures/errors.
- Release Lint: zero errors, 56 warnings (including private API, dependencies, unused resources and unavailable library lint checks). Warnings are not represented as resolved.
- Signed, non-debuggable APK: `GeoLocation-2.1.3-rc2-20260915.apk`, package `io.github.ceigt.geolocation`, versionCode 20103.
- APK SHA-256: `5884a32914e52a4e3b06f8114da058d151eb9e01d910b04cebeb1e9aafe556cf`.
- Release certificate SHA-256: `26ffd7b501c54b76a7a39495105cf0f8e596a56f9fca88ec2111f230e19fb5f7` (same as the existing user release certificate).
- With user approval, rc2 and the independent regression APKs were installed and the Android 15 device rebooted. The user subsequently reported normal manual testing. The instrumented suite has NOT been executed. No GitHub publication was performed.

Reference for transport completion and provider-specific unregister contracts: [AOSP LocationProviderManager](https://android.googlesource.com/platform/frameworks/base/+/61f4fef1ebe73984bed5016055bc5fca70f92fa5/services/core/java/com/android/server/location/provider/LocationProviderManager.java), [AOSP LocationManagerService](https://android.googlesource.com/platform/frameworks/base/+/52756d57d5f4/services/core/java/com/android/server/location/LocationManagerService.java).

## Log review after user testing

97 GeoLocation module log records were extracted from the current boot. Modern native Binder/transport adapters and all three raw GNSS adapter installations were reported, followed by a supplemental callback delivery. One warning concerns the absent legacy LocationManagerService Receiver class; modern paths installed successfully. No GeoLocation-related exception was found in the extracted module/verbose records. This does not establish absence of real coordinates on every channel.

Current state observed read-only: rc2 installed, system location off, system simulation on, no pending_hook_settings entry. User changed simulation state during manual testing; their current state was left intact.

Remaining higher-priority follow-ups (no code changes during this log review):

1. Coarse-only output semantics are still inconsistent. SystemServicesHooks.getLastLocation replaces a native coarse result with createFakeLocation without retaining the caller's coarse granularity; SystemActiveLocationHooks intentionally declines precise supplemental delivery to coarse-only clients. Tightening supplemental authorization did not finish all coarse-result paths.
2. System supplements retain interval/duration/count but not minimum update distance. With a stationary simulated position, a distance-gated request may still consume its callback count. This was not covered by the existing count-only unit tests or user spot checks.
3. Tencent listenerProxies retains strong original/proxy references. Single-shot completion and registration failure do not remove newly created entries; removal is mainly tied to removeUpdates. This matters for long-lived processes using application-mode SDK hooks; it is less directly relevant to the current three-system-component setup.
4. Automated device coverage remains incomplete: the probe was installed but its suite was not executed. Max-update, multi-provider, cancellation, permission revocation, coarse-only and work-profile cases must be distinguished from the user's successful everyday app checks.
