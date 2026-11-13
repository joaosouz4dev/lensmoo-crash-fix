# Changelog

## 1.0.0 - 2026-11-13

- Repair Lensmoo 1.8.0's fatal `StarBurstSdk$a.onFailure` Handler construction by explicitly selecting the main Looper.
- Install the repaired copy as LensFix (`com.lensmoo.fix`) alongside the original app.
- Provide a standalone arm64 APK, checksums, public certificate fingerprint, and build manifest.
- Publish source-hash-guarded repair tools and English/Portuguese documentation.
- Document the continuing vendor invalid-key error and the unvalidated photo, video, voice and long-term stability features.
