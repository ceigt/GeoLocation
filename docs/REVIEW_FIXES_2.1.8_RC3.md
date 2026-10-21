# GeoLocation 2.1.8-rc3 candidate

Based on 2.1.8-rc2 (`b5da48e`). Scope: review items 1, 3, 5 and 6. Alipay investigation remains paused. No GitHub release is requested by this round.

## Changes

- Replace an empty native last/current location only for an existing, caller-visible provider and a currently authorized recipient. Keep native request completion, cancellation and timeout; no additional one-shot request or independent timer is started. Apply the existing fine/coarse privacy boundary. Capture caller identity before entering privileged authorization queries and restore it before native delivery continues.
- Use the existing durable SettingsWriter for target coordinate changes and scope mirroring. Failed writes report an error in both full-screen and bottom-sheet UI; queued coordinate changes read the latest persisted selection. Keep application scope mirroring inside the existing reconnect/retry job. Numeric controls reset their local draft on a failed-save revision even if the persisted value never changed.
- Validate Wi-Fi scan response construction before installing the adapter. Cache the factory, not a ParceledListSlice instance: each response gets a fresh slice because Android allows each slice to be parceled only once. A static empty-list factory can support a ROM without the expected constructor. Unsupported response shapes explicitly report that scan blocking is unavailable; coverage on such ROMs must not be claimed.
- Cache reflection members in the system callback and privacy paths. Authorization, user restrictions, foreground eligibility and AppOps are still queried live. Generate a supplemental fix once rather than replacing a newly generated synthetic fix a second time.

## Validation completed before installation

- Debug and Release unit suites: 95 tests each, zero failures/errors/skips. Added null-fix authorization, compatible scan payload, factory fallback and fresh-slice cases.
- The test-only location FGS now checks fine permission and handles revocation, closing a MissingPermission error found when running lint across both modules.
- External client includes direct one-shot completion/cancellation, empty native cache, nonexistent provider, repeated scan reads, denied runtime permission/AppOps and stopped-simulation checks, in addition to existing continuous/finite/FGS/GNSS tests.
- Red/green baseline on the installed rc2: continuous single-update test passed; a new provider with no fix ever written returned null, reproducing the empty-cache defect. Exactly one of the two baseline checks failed as expected.

## Final candidate and device verification (2026-10-21)

- Installed the non-debuggable Release candidate using the existing release certificate and rebooted the connected Android 16/API 36 phone. Installed APK SHA-256: `8ba33180f9b8d8bc85c18051e88ff0dafcba846489729b1209f20dff42f4d8d8`. Certificate SHA-256 remains `26ffd7b501c54b76a7a39495105cf0f8e596a56f9fca88ec2111f230e19fb5f7`.
- Final APK: nine external-client cases passed in each of four Wi-Fi/system-location combinations (36 executions, no failures). Cases cover single/finite updates, foreground service, GNSS, one-shot completion and cancellation, genuinely empty native cache, repeated Wi-Fi scan reads and nonexistent-provider rejection.
- Five further final-APK checks passed: effective UID-level AppOps denial, revoked runtime permissions, coarse-only cache granularity, and stopped-simulation empty-cache behavior with physical location off/on. The earlier candidate also passed dotted attribution handling. Package-only AppOps denial was initially an invalid test setup because a UID foreground grant remained effective; the corrected UID-level denial passed.
- Actual bottom-sheet coordinate-save fault injection showed the failure toast, retained WGS84 and left the manager alive. Restored directory permissions to their original mode, verified successful GCJ02/BD09/WGS84 saves, and restored the original mapping. Numeric draft rollback and recovery were verified on the preceding candidate with the same numeric-control changes; effective original speed settings were restored.
- Read-only UI checks on WeChat, WeCom, AMap, JD, Meituan and Taobao showed the selected simulated area with physical location enabled and disabled. Repeated WeCom page entry did not reproduce persistent loading. These are page smoke checks, not a quantified accuracy or long-duration reliability claim. No position was sent, check-in submitted or order placed. Alipay remains untested.
- Wi-Fi and location quick tiles changed both their visible state and the actual system setting. No fatal crash, failed system adapter installation or system_server restart appeared during this verification window. Expected injected preference-write failures are recorded separately.
- Removed both temporary regression APKs. Preserved the selected point, app-coordinate mapping, effective speed/mode settings and three-component LSPosed scope. Restored Wi-Fi on, physical location off and simulation off to match the final test session's starting state.

Raw logs, configuration snapshots and UI images stay in the private workspace, outside the repository.

## Cloud builds

Candidate builds now run on pushes to `main` or `fixes/**`, using a pinned Android SDK setup action and CI JVM settings instead of the local low-memory settings. The existing release key stays local; downloaded unsigned APKs can be signed without recompiling. [The first successful cloud run](https://github.com/ceigt/GeoLocation/actions/runs/39523342297) completed in about nine minutes, passed 95 Debug and 95 Release tests with zero failures and Release lint with zero errors, and uploaded candidate/device-test APKs plus verification reports. This run built commit `e8233307382b2dc8c36993aa238e611edeec5b6b`; subsequent documentation-only commits do not change the APK sources. No public Release was created.

## Compatibility limits

This round does not add an independent real-location source, extend Mock Provider coverage, change Tencent metadata policy, or claim support for unverified ROMs. Single-shot delivery still waits for the native request to finish; it does not promise a faster first fix. Other review items remain separate work.

Android references: [native one-shot lifecycle](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/location/java/android/location/LocationManager.java), [provider validation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/services/core/java/com/android/server/location/LocationManagerService.java), [single-use scan container](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/core/java/android/content/pm/BaseParceledListSlice.java).
