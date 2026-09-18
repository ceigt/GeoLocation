# Coarse fallback and unresolved native callbacks

Scope: review findings 1 and 2 only; candidate changes, not a release.

- Move the ROM provider-manager method lookup inside the coarse fallback boundary.
  Missing methods, managers or fudgers now reach the existing coarse fallback after
  permission checking. This does not change the coarse coordinate algorithm.
- While system simulation is active, continuous Binder/Transport deliveries that
  cannot be attributed or decoded are suppressed and their completion is acknowledged.
  Unresolved one-shot callbacks receive null rather than the original position.
- Record system-UID and manager exemptions at incoming registration time, not from
  the outgoing callback's Binder identity. Keep those exemptions weakly referenced.
  Stopping simulation restores pass-through for unresolved callbacks.
- Mark already-sanitized one-shot Transport dispatches to avoid replacing the same
  fix twice at the Binder boundary.

The stricter behavior can turn an unsupported request into no location while
simulation is active. An unrecognized internal system callback is also suppressed
unless its registration established an exemption. Verify system controls and repeated
client requests on the device before release. These changes cannot protect a callback
path whose hook did not install at all.

Added host tests for unknown callbacks, simulation transitions and exemption isolation,
plus an Android instrumentation test for a missing ROM lookup and a null manager.
Device instrumentation and the Wi-Fi/location four-combination regression are pending.

Local validation: Debug and Release each passed 71 unit tests; Release lint reported
0 errors and 33 warnings. Debug APK and instrumentation APK assembled successfully.
The debug verification build retained the prior 2.1.5 metadata. The formal release
metadata is now 2.1.6; no device installation was performed after this version bump.
