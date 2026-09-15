# External Intent Control

External control is disabled by default and requires explicit enablement in Settings.
The exported receiver requires `io.github.ceigt.geolocation.CONTROL`, a signature-level permission enforced by Android before delivery. A companion application must be signed with the same certificate and declare that permission with `uses-permission`.

Ordinary third-party automation applications and ordinary `adb shell am broadcast` are no longer supported. Keep the signing key private; do not distribute it to integrate an automation application.

The receiver additionally checks the local enable switch and validates coordinates. Supported explicit actions on `io.github.ceigt.geolocation/.manager.control.ControlReceiver`:

- `io.github.ceigt.geolocation.action.START`: optionally supply both `latitude` and `longitude` as doubles.
- `io.github.ceigt.geolocation.action.STOP`.
- `io.github.ceigt.geolocation.action.SET_LOCATION`: requires both coordinates, optionally `accuracy` (float) and `start` (boolean).

Latitude must be finite and within [-90,90], longitude within [-180,180]. Invalid values are rejected, not clamped.

Verification: with external control enabled, a differently signed test app and ordinary ADB shell must receive a permission denial; a same-signed companion declaring CONTROL should succeed. With the switch disabled neither may change simulation.
