# 2.1.4-rc3: save failures and mode selection

Scope: review items 1 and 2 only, based on the user-tested rc2 workspace.

- Map start/stop, point selection and favorite writes use the existing serial error-handling writer. Cancellation remains cancellation; ordinary failures produce a UI message and do not terminate subsequent writes. Selected points are read back through the preference flow rather than displayed optimistically.
- Mode validation and persistence use the serial settings writer. A revision check discards selections superseded before or during validation. Both mode flags are committed in one preference edit before reconciling the Mock Provider service. Remote synchronization remains eventually consistent and retains the existing pending-state indicators.
- No changes to injected location delivery, icon assets, or Quick Settings handling in this iteration.

Regression coverage adds a blocked-validation/newer-selection case, recovery after a failed mode write, and rollback of both mode flags when local persistence fails. Existing SettingsWriter tests cover error reporting, subsequent-write ordering and coroutine cancellation.

Device validation still required: rapid mode changes; normal point selection and start/stop; the rc2 system-location-on/off scenarios. This candidate has not been automatically installed or published.

Validation: Debug and Release unit tests each passed 56 tests (0 failures/errors). Release lint: 0 errors, 42 warnings and 1 informational finding, unchanged in count from rc2.
