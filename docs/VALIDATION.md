# LensFix validation and limitations

Date: 2026-11-13. Physical device: Samsung Galaxy S23 Ultra / SM-S918B, Android 17 / API 37. Glasses: XK One Pro. Vendor source app: Lensmoo 1.8.0 (80).

## Reproduced original failure

The untouched installed app returned `4003 ERROR_INVALID_KEY` from StarBurst VAD and then terminated with a worker-thread `Handler` / `Looper.prepare()` exception in `StarBurstSdk$a.onFailure`. A targeted 15-second process observation detected this exact fatal exception and confirmed process exit. Device identifiers, account information, and raw device logs are not published.

## Repaired split build

The same callback used the main Looper. The vendor VAD invalid-key response still occurred, exercising the original failing error path, and the process stayed running without the fatal exception. Separate observations of 20 seconds, 30 seconds, and 15 seconds after the owner's connection confirmation detected no crash. The owner explicitly confirmed that the app remained open and connected to the glasses. The original Lensmoo installation and its data were preserved.

## Standalone release APK

The public build combines the same arm64 app and language/density splits into one APK so a user can install it directly. Its build manifest records source hashes, pinned tool hashes, output SHA-256, public signing-certificate fingerprint, signature verification, ZIP alignment verification, and unchanged executable/asset checks.

After reconnecting USB, the installed standalone APK's SHA-256 was checked against the published release and matched exactly. The app was stopped and relaunched; it reached the main activity and stayed running through a 30-second process observation with no detected crash. The StarBurst `4003 ERROR_INVALID_KEY` callback continued occurring, exercising the error path that terminated the original application. The earlier interrupted observation is superseded by this completed check. Results are recorded in the updated `validation.json` release asset. No APK code or binary was changed for this documentation update.

## What these tests do not establish

| Function | Result |
| --- | --- |
| Original fatal Handler callback | Reproduced, then repaired on the tested device; standalone release APK rechecked after relaunch |
| Bluetooth connection with XK One Pro | Owner confirmed on the repaired split build |
| Long-term stability and background operation | Not established |
| StarBurst voice-service authentication | Still returns invalid key; repeated retries remain |
| Photo capture and gallery export | Not validated end to end |
| Video capture and video export | Not validated; see [video research](VIDEO-RESEARCH.md) |
| Other Android versions, architectures, phones or firmware | Not validated |

The earlier SDK's 23 passing Python protocol tests concern that independent SDK, not this vendor-app hotfix. They are not counted as LensFix application regression coverage. Short successful observations are evidence for the reproduced failure path, not a guarantee that the whole app is defect-free.
