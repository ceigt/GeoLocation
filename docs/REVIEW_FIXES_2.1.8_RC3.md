# GeoLocation 2.1.8-rc3 candidate

Based on 2.1.8-rc2 (`b5da48e`). Scope: review items 1, 3, 5 and 6. Alipay investigation remains paused. No GitHub release is requested by this round.

## Changes

- Replace an empty native last/current location only for an existing, caller-visible provider and a currently authorized recipient. Keep native request completion, cancellation and timeout; no additional one-shot request or independent timer is started. Apply the existing fine/coarse privacy boundary. Capture caller identity before entering privileged authorization queries and restore it before native delivery continues.
- Use the existing durable SettingsWriter for target coordinate changes and scope mirroring. Failed writes report an error; queued coordinate changes read the latest persisted selection. Keep application scope mirroring inside the existing reconnect/retry job. Numeric controls reset their local draft on a failed-save revision even if the persisted value never changed.
- Validate Wi-Fi scan response construction before installing the adapter. Cache the factory, not a ParceledListSlice instance: each response gets a fresh slice because Android allows each slice to be parceled only once. A static empty-list factory can support a ROM without the expected constructor. Unsupported response shapes explicitly report that scan blocking is unavailable; coverage on such ROMs must not be claimed.
- Cache reflection members in the system callback and privacy paths. Authorization, user restrictions, foreground eligibility and AppOps are still queried live. Generate a supplemental fix once rather than replacing a newly generated synthetic fix a second time.

## Validation completed before installation

- Debug and Release unit suites: 95 tests each, zero failures/errors/skips. Added null-fix authorization, compatible scan payload, factory fallback and fresh-slice cases.
- The test-only location FGS now checks fine permission and handles revocation, closing a MissingPermission error found when running lint across both modules.
- External client includes direct one-shot completion/cancellation, empty native cache, nonexistent provider, repeated scan reads, denied runtime permission/AppOps and stopped-simulation checks, in addition to existing continuous/finite/FGS/GNSS tests.
- Red/green baseline on the installed rc2: continuous single-update test passed; a new provider with no fix ever written returned null, reproducing the empty-cache defect. Exactly one of the two baseline checks failed as expected.

## Candidate/device follow-up

Pending final packaging and device verification. Raw logs, configuration snapshots and UI images stay in the private workspace, outside the repository.

## Compatibility limits

This round does not add an independent real-location source, extend Mock Provider coverage, change Tencent metadata policy, or claim support for unverified ROMs. Single-shot delivery still waits for the native request to finish; it does not promise a faster first fix. Other review items remain separate work.

Android references: [native one-shot lifecycle](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/location/java/android/location/LocationManager.java), [provider validation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/services/core/java/com/android/server/location/LocationManagerService.java), [single-use scan container](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/core/java/android/content/pm/BaseParceledListSlice.java).
