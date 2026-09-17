# 2.1.5-rc1: Wi-Fi controls and repeated WeCom requests

Based on release 2.1.4. Candidate only; no new release has been published.

## Evidence

- The Wi-Fi status adapter previously treated every UID >= 10000 as a location client. SystemUI uses an app-range UID on the test ROM. This can return enabled while Wi-Fi is actually off and leave the tile with stale state.
- On 2026-09-17 the user reproduced indefinite loading with Wi-Fi, system location and simulation enabled. The system registered WeCom GPS at 08:48:36.373 and unregistered it at 08:48:36.451. Similar short lifetimes occurred in earlier repeated attempts. Module delivery logs alone do not establish success in the client UI.
- Inspection of the previously extracted client SDK shows GPS registration before GNSS status registration. Its stationary-fix processing rejects a fix with zero speed/bearing before satellite status has arrived. This is a plausible ordering explanation, not a confirmed complete root cause.

## Candidate changes

- Resolve Wi-Fi callers from the service context and full UID package set. Preserve real state for SystemUI, Settings, shared control UIDs, system app IDs in secondary users, the manager, and unresolved identities. Use this check consistently across Wi-Fi adapters.
- Give WeCom GPS/passive simulated fixes a 500 ms initial window. GNSS status is not delayed. Apply the same window to native replacements and supplemental fixes; do not extend request deadlines or change speed/coordinates. Existing registration-identity checks prevent delayed tasks from reviving canceled/replaced requests.

## Required device checks

Toggle Wi-Fi off/on from Quick Settings while simulating and after stopping. Test repeated WeCom page entry without force-stopping, with Wi-Fi and system location both on/off combinations. Verify zero-speed configuration and other previously working clients. A bounded delay cannot guarantee client handler readiness; retain candidate status until repeated device tests pass.

## Build and installation

- Debug and Release unit tests: 62 each, no failures or errors. Release lint: 0 errors, 42 warnings, 1 informational finding.
- Signed non-debuggable candidate: `GeoLocation-2.1.5-rc1-20260917.apk`, versionCode 20105. Certificate matches the existing release certificate.
- SHA-256: `BD65A1D006B3EFBE05C8D767EBE5BAA3F9DA73D76CDAE6C62A3AE93733C869FE`.
- ADB update installation succeeded, installed version verified as 2.1.5-rc1, and reboot issued.

## Failed device validation and rc2 rollback

The user reports continued loading, including with Wi-Fi off. Logs at 13:04–13:05 show GPS requests canceled after about 70–110 ms, before the 500 ms initial window can deliver a fix. Removed the initial window entirely; this restores pre-rc1 delivery timing but does not establish why the client cancels requests. Retained the independent Wi-Fi caller fix. Added Google Maps console diagnostics that log only the SDK error/warning identifier, never raw messages, keys or URLs. No release readiness claim until further device testing.

rc2: Debug/Release unit tests 60 each passed; lint has 0 errors, 42 warnings and 1 informational finding. Signed non-debuggable APK `GeoLocation-2.1.5-rc2-20260917.apk` uses the existing release certificate; SHA-256 `78A28C6B8EE59BC50BB8DECEA6E8E12CCC69B60C3FD3B5493D028504E55EBBD4`. Update installation succeeded and reboot was issued. Runtime validation remains pending.

rc2 logs confirmed immediate synthetic location delivery, followed by simulated GNSS status, while WeCom canceled requests after roughly 60 ms to 3 seconds. Static inspection of its bundled Tencent location SDK shows that a zero-speed/zero-bearing fix is rejected while the used-in-fix satellite count is zero. rc3 therefore replaces the failed 500 ms delay with a WeCom-only 20 ms GPS/passive ordering window; GNSS and every other client remain immediate. It also reports only sanitized Google Maps authentication/script error identifiers through Logcat. Debug/Release unit tests 61 each passed; lint has 0 errors, 42 warnings and 1 informational finding. Signed non-debuggable APK `GeoLocation-2.1.5-rc3-20260917.apk` uses the existing release certificate; SHA-256 `8F107FF2543A28CAC3DF08844420F9848188F579A520C7E9FAF71BA3213CF563`. Installation and reboot succeeded; runtime validation remains pending.

rc3 device testing remained intermittent even though logs proved simulated satellite status preceded the 20 ms location callback. The same client SDK also rejects a fix when speed is exactly zero and filters repeated identical zero-speed fixes. rc4 keeps user coordinates unchanged and maps only WeCom's exact zero speed to `0.001 m/s` (3.6 metres/hour), which is operationally stationary but avoids both equality checks. Other apps and explicitly nonzero speeds remain unchanged. Google diagnostics reported `ApiNotActivatedMapError`; the configured key's Google Cloud project must enable Maps JavaScript API before any app-side validation can continue.

The user reports normal rc4 device behavior. The phone disconnected before the final log pull, so that report is recorded as user validation rather than a post-test log assertion. Formal 2.1.5 validation ran 64 Debug and 64 Release unit tests with no failures, Release lint with 0 errors, and built both regression APKs. `GeoLocation-2.1.5-20260917.apk` is non-debuggable, uses the existing release certificate, and has SHA-256 `42732DC1888A4F16C4A66A70C56E82528388923FD7494E0AD71DB8C5110A0BA6`.
