# rc3 — coarse system outputs and minimum movement

Scope: the first two higher-priority findings from the rc2 log review. No Tencent lifecycle, Alipay, or Mock parameter changes.

System last-known, continuous native and one-shot outputs retain native permission/null checks. Fine callers receive the simulation point; coarse-only callers use the existing provider LocationFudger.createCoarse implementation. If that ROM adapter is unavailable, return no result rather than an exact simulation point or an unprocessed real point. This does not add coarse-only active supplementation with the real switch off. Application-only vendor SDK granularity is not covered by this system adapter.

Continuous system requests now capture minimum movement and share a distance anchor between native and supplemental delivery. Filtering compares delivered simulation coordinates. Rejected distance/interval fixes do not consume the update budget or advance the anchor. Each provider retains its own policy. First fixes remain allowed.

Four JVM tests cover stationary fixes, interval rejection, native/synthetic transitions and batch limits. The independent device probe adds a stationary request test and an explicitly selected coarse-cache test. For the latter, grant only coarse permission, revoke fine before starting instrumentation, and pass `-e coarseOnly true -e class io.github.ceigt.geolocation.regression.LocationRegressionTest#coarseCacheQueryPreservesGranularity` with the selected simulation coordinates. An absent native cache is a failed precondition, not a successful privacy test. Run it after the network provider has a cache with the real switch on; restore original grants afterward.

The signed rc3 was installed and the phone was rebooted. Manual multi-app checks passed with simulation enabled in both system-location-off and system-location-on states. The independent instrumentation APK was compiled but was not run for rc3.

## Build validation

Debug and Release JVM suites both passed (39 tests each). Release Lint has zero errors; warnings remain. The independent Android test APK compiled successfully but has not been executed on the phone for rc3.

Signed APK: `GeoLocation-2.1.3-rc3-20260915.apk`, versionCode 20103, non-debuggable. Certificate matches the existing release certificate. SHA-256: `468ca3912dd753d98d4442d3c7e3e4351ac0249ea569166bbfbf7d90d5c3b15e`.

## Post-test log review

The latest LSPosed module log contained 100 GeoLocation records and no GeoLocation exception, crash, preference-sync failure, or unavailable privacy adapter. Android 15 transport guards, raw GNSS guards, and supplemental system delivery were installed. One warning reported that the legacy pre-Android location service Receiver class was absent; the modern Android 15 paths installed successfully, so the stable code was left unchanged.

The phone was running the signed rc3 candidate during these checks. This document records the candidate validation performed before publishing 2.1.3.
