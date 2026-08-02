# Call-end QA matrix (Truecaller-style)

Filter logcat: `adb logcat -s CALLEND_DEBUG CallScreening`

## Devices to prioritize (Android 11+)

| Brand | OS | Without Unrestricted battery |
|-------|-----|------------------------------|
| Pixel | 11–15 | Expect during-call bubble → morph call-end |
| Samsung | One UI 5–7 | Same; check “Put unused apps to sleep” only if misses |
| Xiaomi / Redmi / POCO | MIUI / HyperOS | Same; Autostart only if misses after force-idle |
| OPPO / realme | ColorOS | Same |
| vivo / iQOO | Funtouch / OriginOS | Same |

## Cases

1. Grant call screener → `canDrawOverlays=true` without Appear-on-top Settings
2. Incoming ring → during-call bubble (“Incoming call”)
3. Answer → bubble updates to “On call”
4. Hang up → same window morphs to full call-end card + ads
5. Missed call (ring → idle) → call-end Missed
6. Outgoing → “Calling…” bubble → call-end
7. Swipe app from Recents mid-call → bubble should survive (FGS + overlay)
8. User closes bubble mid-call → call-end still shows via PHONE_STATE IDLE cold path
9. Deny overlay / revoke role → fallback notification opens CallEndActivity
10. Blocked number with screening on → no during-call bubble, call rejected

## Metrics

Settings → Advanced (aggressive OEMs) shows `Diagnostics: during=… morph=… show=… miss=…`

Or log: `CallEndMetrics: …` on App start / Home prerequisites.
