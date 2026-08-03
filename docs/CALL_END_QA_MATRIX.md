# Call-end QA matrix (Truecaller-style)

Filter logcat: `adb logcat -s CALLEND_DEBUG CallScreening`

## Flow

1. Call active (RINGING / OFFHOOK) → during-call overlay bubble
2. Hang up (IDLE) → dismiss bubble → after-call Activity (popup or fullscreen)
3. If Activity is blocked (MIUI) → overlay fallback after ~450ms

## Devices to prioritize (Android 11+)

| Brand | OS | Without Unrestricted battery |
|-------|-----|------------------------------|
| Pixel | 11–15 | Expect bubble → dismiss → Activity |
| Samsung | One UI 5–7 | Same; check “Put unused apps to sleep” only if misses |
| Xiaomi / Redmi / POCO | MIUI / HyperOS | Same; need Appear-on-top for bubble; Activity may need overlay fallback |
| OPPO / realme | ColorOS | Same |
| vivo / iQOO | Funtouch / OriginOS | Same |

## Cases

1. Grant call screener → `canDrawOverlays=true` without Appear-on-top Settings
2. Incoming ring → during-call bubble (“Incoming call”)
3. Answer → bubble updates to “On call”
4. Hang up → bubble dismisses → after-call Activity + ads
5. Missed call (ring → idle) → after-call Missed (fullscreen if locked)
6. Outgoing → “Calling…” bubble → after-call Activity
7. Swipe app from Recents mid-call → bubble should survive (FGS + overlay)
8. User closes bubble mid-call → after-call still shows via PHONE_STATE IDLE
9. Deny overlay → no during-call bubble; after-call Activity still attempted
10. Blocked number with screening on → no during-call bubble, no after-call

## Metrics

Settings → Advanced (aggressive OEMs) shows `Diagnostics: during=… morph=… show=… miss=…`

Or log: `CallEndMetrics: …` on App start / Home prerequisites.
