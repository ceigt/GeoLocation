# Maintenance roadmap

## Release policy

- Keep `main` buildable with JDK 17, Android SDK 36 and the checked-in Gradle wrapper.
- Run unit tests plus debug and release builds before tagging.
- Use semantic versions and retain one owner-controlled signing key for all public releases.
- Review permission, exported-component and dependency changes in every release.

## Near-term work

1. Test Android 11–16 on at least one AOSP-like ROM and one OEM ROM.
2. Add instrumented tests for preference migration and Mock Provider lifecycle.
3. Add a map-provider interface so an OpenStreetMap implementation can remove the Baidu AK dependency.
4. Harden optional external control with a signature permission before recommending it for general use.
5. Track libxposed API and Android location API changes; keep system hooks opt-in.

## Issue reports

Include device/ROM, Android version, root solution, LSPosed version, GeoMimic version, selected mode and scope, reproduction steps, and redacted logs. Never include account identifiers or precise personal locations.
