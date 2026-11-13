# Lensmoo Crash Fix (LensFix) - XK One Pro Android APK

[Português brasileiro](README.pt-BR.md) | English

**Lensmoo closes immediately, keeps stopping, or crashes on your XK One Pro smart glasses setup?** LensFix is an unofficial repair for a reproduced Lensmoo 1.8.0 startup crash in `StarBurstSdk$a.onFailure`. The repaired app stays alongside the original as **LensFix**, package `com.lensmoo.fix`.

[**Download LensFix Android APK (arm64)**](https://github.com/joaosouz4dev/lensmoo-crash-fix/releases/latest/download/LensFix-arm64.apk) · [Release notes and checksums](https://github.com/joaosouz4dev/lensmoo-crash-fix/releases/latest) · [Report a problem](https://github.com/joaosouz4dev/lensmoo-crash-fix/issues/new?template=bug_report.yml)

## What this fixes

The official Lensmoo app was observed failing after its StarBurst voice-activity-detection service returned `4003 ERROR_INVALID_KEY`. The error callback constructs an Android `Handler` on a worker thread with no `Looper`, causing:

```text
java.lang.RuntimeException: Can't create handler inside thread
that has not called Looper.prepare()
at com.starburst.sdk.StarBurstSdk$a.onFailure
```

LensFix changes that callback to use `new Handler(Looper.getMainLooper())`. Vendor authentication and license checks remain in place. The vendor invalid-key response itself is **not fixed**.

**Physical-device result:** tested on Samsung Galaxy S23 Ultra (SM-S918B), Android 17 / API 37, with XK One Pro glasses. The owner confirmed that the repaired app stays open and connects to the glasses. See [validation and limits](docs/VALIDATION.md).

## Install

1. Download **LensFix-arm64.apk** from this repository's [GitHub Releases](https://github.com/joaosouz4dev/lensmoo-crash-fix/releases/latest). This is a standalone arm64 APK, not an individual split APK.
2. Optionally compare its SHA-256 with the release's `SHA256SUMS.txt`.
3. Open the downloaded APK on your Android phone and allow your chosen installer source when Android asks.
4. Open **LensFix**. Your original **Lensmoo** installation is preserved. The separate app has its own data, so sign in with your own vendor account if required.
5. Enable Bluetooth, grant nearby-device permissions when prompted, and connect the glasses.

The release is based on Lensmoo **1.8.0, versionCode 80**, includes arm64 native libraries plus English, Spanish, Portuguese, and xxhdpi resources. The app manifest requires Android 8.0+, but physical testing so far covers the device above only. Other app builds, CPU architectures, phones, and firmware revisions are not validated.

## Video recording on XK One Pro

On the tested firmware 1.0.2 unit, the owner confirmed that **two short presses of the right physical button start voice recording, not video**. The product-family guide's video gesture does not match this unit. The firmware reports `noStorageDevice: 1`, and the app hides stored-video controls.

A later diagnostic confirmed a **320x240 live H.264 stream** and reconstructed a silent ten-second MP4 with 295 distinct frames on a computer, then copied it to the phone. Direct recording through a new app button is in development and needs device validation. Published **1.0.0** still provides the original crash repair. See [tested controls and evidence](docs/VIDEO-RESEARCH.md).

## Known limitations

- StarBurst can continue returning `ERROR_INVALID_KEY` and retrying initialization. This release prevents the observed fatal thread error; it does not restore the vendor voice service or remove its retries.
- A test photo was captured, transferred into the app, and saved to the Android gallery under `Pictures/`. The exported JPEG decoded successfully. Voice features and long-term background stability have not been verified end to end.
- This release does not implement video recording, video export, firmware updates, or new glasses protocols.
- It is independently signed and uses a different package. It is not an official Lensmoo update and is not distributed through Google Play.

## Open repair tools

The [Python build tool](scripts/build.py) checks exact original-APK hashes, applies the targeted patch, assembles only `classes5.dex`, preserves the six other DEX files and native libraries, merges the splits, and verifies the final signature, 16 KB ZIP alignment, executable content, and assets. Tools are pinned by SHA-256. See [building from your own APKs](docs/BUILD.md).

The vendor app's source is not open source. The MIT license covers this repository's original repair tools and documentation only. See [third-party notices](THIRD_PARTY_NOTICES.md). No personal account data, device logs, decompiled vendor sources, or private signing keys are committed.

## Credits and references

- [Gustavo Beneduzi's XK One Pro SDK](https://github.com/beneduzi/xk-one-pro-sdk), an independent hardware/protocol investigation that helped identify the product family and its limits.
- [Android Handler documentation](https://developer.android.com/reference/android/os/Handler), for explicit Looper selection.
- [Apktool](https://github.com/iBotPeaches/Apktool) and [APKEditor](https://github.com/REAndroid/APKEditor).

LensFix is an independent community repair, unaffiliated with Lensmoo, StarBurst, or the glasses manufacturer. Search terms describing this issue include **Lensmoo app crashing**, **Lensmoo keeps stopping**, **Lensmoo fecha sozinho**, **Lensmoo não abre**, **XK One Pro Android**, and **Looper.prepare StarBurstSdk**.
