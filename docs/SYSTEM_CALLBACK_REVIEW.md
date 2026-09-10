# System callbacks review — 2026-09-10

## Observed failure and correction

On a Pixel 9 Pro XL running Android 15, WeCom 5.0.9 could not finish location acquisition with the real location switch off. Live diagnostics established two independent issues:

- Requests could be cancelled before the first periodic callback. Provider availability is now adapted for tracked listeners, and native registration is followed by an immediate delivery attempt.
- Evaluated AppOps incorporates the real-location switch restriction. Synthetic-only delivery now checks stored UID/package authorization, fine permission, foreground/background eligibility, user restrictions and package suspension. No device setting or AppOps mode is changed by this path. Reflection errors fail closed.

After these corrections, callbacks were sent successfully but the SDK still did not produce a result while stationary. A temporary nonzero-speed experiment produced a visible WeCom location page with the real switch still off. Inspection of the locally loaded SDK identified filtering when satellite count, speed and bearing are all zero. Proprietary code and device logs are not included in this repository.

## Stationary correction

The system adapter now handles `IGnssStatusListener` registration, simulated GPS satellite status, started/first-fix events, and stopped events when simulation ends with real location off. Native registration validates the caller first. Delivery uses the same authorization checks as location callbacks; unregister and Binder death release the listener. The old GNSS registration block no longer intercepts status registration. Simulated status does not fabricate raw measurements or NMEA. User-configured speed remains unchanged.

## Review and release gate

- Reviewed manifest/exported components, WebView main-frame navigation, map credential handling, system callback lifecycle and stored authorization precedence.
- External control remains disabled by default; app backup remains disabled.
- Unit tests cover authorization precedence, explicit denial and foreground-only authorization, alongside the existing coordinate/scope/listener tests.
- Stationary real-device verification passed on the diagnostic build with the same source: the user confirmed speed **0**, real location **off**, and successful location acquisition in WeChat and WeCom. System logs confirmed both coordinate and satellite callbacks. After stopping simulation and reopening WeCom, the user confirmed no new location could be acquired. These observations validate this tested path, not all clients.
- 25 unit tests passed; debug lint reported 0 errors and 59 warnings (including dependency updates, internal APIs, accessibility and the intentionally disabled exported control receiver). The optimized signed release build also passed. The optimized release APK has not yet been separately retested on-device.
- One-shot/PendingIntent acquisition, coarse-only clients, private SDK network paths and other Android/ROM combinations remain outside this verification. This review is not a guarantee of universal replacement or undetectability.
