# MaoozOS Android 4.1.2

Defensive reliability build based on the 4.1.1 Android baseline.

Fixes include:
- Stable, single semester reopen/finalize implementation with snapshot preservation.
- Explicit current-semester ID instead of relying only on semester-name numbering.
- Historical-semester GPA/credit support.
- Finalized-semester GPA/CGPA snapshot isolation from later grading-profile changes.
- Course controls for GPA/CGPA inclusion.
- Explicit grade-point override behavior.
- Broader default grade scale (A+ through D-/F) while remaining fully customizable.
- Timetable grid includes all 24 hours.
- Native reminder search horizon extended for long date ranges.
- Safer recurring-reminder recovery after reboot/time/timezone changes.
- Android 6+ compatibility fix by removing direct java.time usage from reminder quiet-hours logic.
- Safer notification permission/test handling.
- WebView asset loading through AndroidX WebKit with local file access disabled.
- WebView renderer-crash recovery.
- Cleartext traffic disabled for the embedded WebView.
- Android version metadata updated to 4.1.2.

This project is designed to be built by the existing GitHub Actions workflow.
