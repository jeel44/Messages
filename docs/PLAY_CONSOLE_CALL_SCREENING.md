# Play Console checklist — Call screening + call-end overlay

Complete these before submitting a build that uses `ROLE_CALL_SCREENING`.
In-app screening is implemented; listing and declarations are still required.

## Store listing

- Lead with **spam / blocked-call screening** (and SMS), not only after-call ads.
- Screenshots: Settings → Calling (call screener on, block toggles) and Blocked numbers.
- Short description should mention call blocking / spam screening.
- Full description should explain:
  - User can set the app as call screener
  - Blocked numbers are rejected before they ring
  - Optional silence for unknown callers
  - After-call screen shows caller info when enabled

## Privacy policy (update the Sites page)

URL in app: `https://sites.google.com/view/messagesappspolicy/home`

Add sections covering:

1. **Call screening** — numbers evaluated on-device to allow, silence, or block; blocked list stored on device.
2. **Call log** — used to resolve caller names/numbers for the after-call screen.
3. **Appear on top / overlay** — used for after-call and SMS category popups; on Android 11+ may be granted with call screener role.
4. **BlockedNumberContract** — may sync blocked numbers with the system when allowed.

## Permissions declaration form

Re-declare (or update) if you use:

- `READ_CALL_LOG` — caller ID / after-call name resolution (existing)
- Call screening / spam blocking use case for the call screener role
- SMS permissions as default SMS handler (existing)

If permission use changes, submit an updated declaration.

## Policy risk notes

- Do not describe the call screener prompt as “enable ads” or “appear on top”.
- Keep Settings screening toggles functional (not a stub that always allows calls).
- After-call ads are fine as secondary; screening must remain a real, visible product feature.
