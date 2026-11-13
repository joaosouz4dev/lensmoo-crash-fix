# Build LensFix from a supported local Lensmoo installation

Requirements: Python 3.10+, Java/JDK 17+, Android SDK Platform Tools, and Build Tools 37.0.0. Windows was used for the release build. Paths are explicit so the script can also be used with equivalent Linux/macOS SDK installations; those hosts have not been tested.

## 1. Obtain your original APKs

Connect and authorize your Android phone for USB debugging. The following read-only command lists the installed app packages:

```sh
adb shell pm path --user 0 com.lensmoo.app
```

Use `adb pull` on each returned APK path and place the files under `work/input/`. No root, account extraction, network capture, or app-data backup is needed.

The current build deliberately supports one exact Lensmoo 1.8.0 (80) split set: `base.apk`, `split_config.arm64_v8a.apk`, `split_config.en.apk`, `split_config.es.apk`, `split_config.pt.apk`, and `split_config.xxhdpi.apk`. [build.py](../scripts/build.py) lists their SHA-256 values. A version string alone is not enough to establish identical inputs. Different architectures, languages, density splits, or vendor rebuilds need a separate review; do not remove the hash guards.

## 2. Build

PowerShell example, with paths adjusted to your installation:

```powershell
python scripts/build.py `
  --input work/input `
  --java-home 'C:/Program Files/Android/Android Studio/jbr' `
  --sdk "$env:LOCALAPPDATA/Android/Sdk" `
  --download-tools
```

`--download-tools` obtains Apktool 3.0.3 and APKEditor 1.4.9 from their official GitHub releases and verifies pinned checksums. Without the flag, place those exact JARs in `work/tools/` yourself. Android SDK tools are installed separately, not by this script.

The script starts from a fresh temporary build directory, modifies the one known Handler call, and compiles only `classes5.dex`. It then changes the package/provider identity and launcher name, merges the six APKs into one arm64 APK, aligns and signs it. All six other DEX files, native libraries and assets are compared with the originals after packaging. Resources and split-installation metadata must be combined for standalone installation.

Output:

- `dist/LensFix-arm64.apk`
- `dist/SHA256SUMS.txt`
- `dist/build-manifest.json`, including input hashes, tool hashes, output hash, public certificate fingerprint, and verified-content counts.

On the first local build a signing key and password are generated under ignored `work/private/`. Keep these private and preserve them if you want subsequent builds to update your own installation. Existing signing material can be supplied with `--keystore`, `--password-file`, and `--key-alias`. Never upload those files. Independently generated keys will not update a copy signed by another key.

## 3. Validate before sharing a new build

Install and open the separate package with the owner's authorization:

```sh
adb install -r dist/LensFix-arm64.apk
adb shell am start -W -n com.lensmoo.fix/com.lensmoo.business.ui.LaunchActivity
python scripts/smoke_test.py --package com.lensmoo.fix --seconds 30
```

Exercise the actual failure trigger during observation. No observed crash is not proof that photos, voice, video, or all Bluetooth functions work. A device owner must separately check connection and the functions they need. The script exits with code 1 for a detected crash, 2 if no process was observed, and 0 if the observed process remains running without a crash during the observation window.

## Patch

The failing callback effectively did:

```java
new Handler().postDelayed(retry, 500);
```

It now does:

```java
new Handler(Looper.getMainLooper()).postDelayed(retry, 500);
```

See [Android's Handler reference](https://developer.android.com/reference/android/os/Handler). The repair does not alter the vendor's keys, account service, license checks, or retry policy. The continuing invalid-key retries are a documented limitation.
