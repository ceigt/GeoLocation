# GeoLocation 2.1.8-rc1 candidate

Based on 2.1.7 (`5e7f0e3`). This candidate addresses review items 1–7 and their regression checks. Device installation and field validation are pending; it is not a published GitHub Release.

| Item | Change and purpose | Compatibility consideration |
|---|---|---|
| 1 | Validate last-location caller against Binder UID packages and the explicit calling-package argument; attribution tags cannot change identity. | An unresolved shared UID receives no cached fix rather than a real coordinate. |
| 2 | Protect system intercept adapters without replaying native calls or swallowing native permission errors. Keep live callback registrations after adapter failures and retry with bounded delay. | Unsupported adapters still fail closed; retries do not bypass authorization. |
| 3 | Preserve native subsecond callbacks, native request completion counts, batch delivery and flush listener mapping/completion. Supplemental callbacks retain a bounded cadence and shared request limits. | Callback timing and counts can differ from the previous incorrect wrapper behavior. |
| 4 | Check current application permissions/AppOps/background access for supplements; preserve coarse granularity for Android and Tencent outputs. | Background requests without background permission are treated conservatively. |
| 5 | Clone Parcelable vendor payloads before replacing nested fixes; suppress unsupported containers instead of modifying shared instances. | Unknown MIUI containers require an explicit adapter. |
| 6 | Reject edits to malformed or out-of-range favorites without overwriting the stored JSON; surface write failures in both favorites interfaces. | Malformed data is retained and must be repaired before editing. |
| 7 | Freeze Tencent location getters and timestamp at delivery; normalize overridden primitive values to the SDK return types. | Old location objects retain their original snapshot after simulation changes/stops. |

## Validation

- Debug and Release unit suites: 82 tests each, zero failures/errors/skips (164 executions). Checks include verified UID attribution, runtime adapter/native exceptions and no replay, finite subsecond delivery budgets, bounded callback retry and immutable Tencent getter values.
- Instrumented checks added: corrupted-favorite retention, nested Parcelable ownership, coarse coordinate boundary validity, dotted-attribution last-known query, flush completion and finite subsecond request lifetime.
- Both app and regression instrumented test APKs compiled successfully for later device testing; their tests have not been executed on a phone.
- Android 11 uses scalar listener delivery; batch and flush callback forwarding require API 31 or later.
- Release lint passes with zero errors. Remaining warnings include intentional framework reflection, dependency-update suggestions, UI/KTX suggestions and application-context storage; no Activity context is retained.
- Existing dependency binary pins are retained. Missing metadata checksums are added only for the versions required by this build and verified official Maven metadata.

## Field checks before release

1. Keep the existing system-only LSPosed scope and current coordinate choices.
2. Verify WeChat, WeCom, AMap, JD, Meituan and Taobao repeatedly, including warm re-entry, with Wi-Fi on/off and the real location switch on/off.
3. Verify stop/start behavior and that system Wi-Fi/location tiles remain usable.
4. Test application mode separately, including flush, finite requests and coarse-only permission; test favorites add/delete/select and both Google/AMap maps.

The deferred GNSS source consistency, Mock Provider coverage, broader performance work, signing workflow hardening and structural cleanup are not part of this candidate.
