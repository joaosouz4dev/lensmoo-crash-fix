# LensFix validation and limitations

Date: 2026-11-13. Physical device: Samsung Galaxy S23 Ultra / SM-S918B, Android 17 / API 37. Glasses: XK One Pro. Vendor source app: Lensmoo 1.8.0 (80).

## Reproduced original failure

The untouched installed app returned `4003 ERROR_INVALID_KEY` from StarBurst VAD and then terminated with a worker-thread `Handler` / `Looper.prepare()` exception in `StarBurstSdk$a.onFailure`. A targeted 15-second process observation detected this exact fatal exception and confirmed process exit. Device identifiers, account information, and raw device logs are not published.

## Repaired split build

The same callback used the main Looper. The vendor VAD invalid-key response still occurred, exercising the original failing error path, and the process stayed running without the fatal exception. Separate observations of 20 seconds, 30 seconds, and 15 seconds after the owner's connection confirmation detected no crash. The owner explicitly confirmed that the app remained open and connected to the glasses. The original Lensmoo installation and its data were preserved.

## Standalone release 1.0.0

The public build combines the same arm64 app and language/density splits into one APK so a user can install it directly. Its build manifest records source hashes, pinned tool hashes, output SHA-256, public signing-certificate fingerprint, signature verification, ZIP alignment verification, and unchanged executable/asset checks.

After reconnecting USB, the installed standalone APK's SHA-256 was checked against the published release and matched exactly. The app was stopped and relaunched; it reached the main activity and stayed running through a 30-second process observation with no detected crash. The StarBurst `4003 ERROR_INVALID_KEY` callback continued occurring, exercising the error path that terminated the original application. The earlier interrupted observation is superseded by this completed check. Results are recorded in the updated `validation.json` release asset. No APK code or binary was changed for this documentation update.

## Video recording in 1.1.0

The observed preview carries a five-byte type/length envelope followed by Annex-B H.264, although the vendor callback names it JPEG data. The new helper decodes this path on a single worker and provides an explicit local recording control. A competing legacy decoder also caused a Surface connection error during development; the integration now guards legacy decoder startup while the helper owns the preview.

A ten-second requested test through the new button generated **14.119656 seconds** because UI automation took additional time to locate and stop the control. The phone itself created the MP4 through MediaMuxer and published it through MediaStore under `Movies/LensFix`. The file contains H.264 320x240, no audio, **427 frames**, **684472 bytes**. FFmpeg decoded all 427 frames without errors using passthrough frame timing, with 427 distinct frame hashes. The owner initially could not locate the folder, then confirmed the video opened after it was launched through its MediaStore URI. No camera media is published.

This end-to-end recording used a private diagnostic candidate with metadata-only logs and the decoder ownership guard. The release source removes those diagnostic logs; its recording and muxing behavior is unchanged. Final artifact installation and preview checks are recorded separately in the release's `validation.json`.

## Validation scope

| Function | Result |
| --- | --- |
| Original fatal Handler callback | Reproduced, then repaired on the tested device; standalone release APK rechecked after relaunch |
| Bluetooth connection with XK One Pro | Owner confirmed on the repaired split build |
| Long-term stability and background operation | Not established |
| StarBurst voice-service authentication | Still returns invalid key; repeated retries remain |
| Photo capture and transfer into the app | Test capture confirmed; app album increased from one to two photos |
| Photo export to Android gallery | Owner confirmed Save; MediaStore entries in `Pictures/`, exported JPEG 1280x960, 580969 bytes; file decoded successfully |
| Right-front physical button double press | Owner reports voice recording; the app's built-in operation guide agrees |
| Diagnostic live-preview capture | Valid silent H.264 320x240 MP4, 10 seconds, 295 distinct decoded frames; rebuilt on computer and copied to the phone |
| 1.1 native preview | Installed on the same phone; serialized decoder reported rendered output at 320x240 and the new recording control appeared; 30-second observation detected no crash |
| Direct MP4 recording through the new app button | Phone-generated 14.12-second silent MP4, 320x240; all 427 frames decoded and distinct; owner confirmed playback opened |
| Other Android versions, architectures, phones or firmware | Not validated |

The earlier SDK's 23 passing Python protocol tests concern that independent SDK, not this vendor-app hotfix. They are not counted as LensFix application regression coverage. Short successful observations are evidence for the reproduced failure path, not a guarantee that the whole app is defect-free.

The video implementation has six passing repository tests, including generated baseline/high-profile H.264 parser fixtures and cropped SPS dimensions. Android codec, MediaStore, and lifecycle behavior additionally require physical-device checks; unit tests alone do not establish them. The release validation asset records the final source revision, CI result, and artifact checks.

## Tested photo export workflow

Open **álbum**, open the newly captured photo, and tap the save/download icon at the lower left. On the tested phone the file was saved under `Pictures/` and registered as `image/jpeg` in Android MediaStore. The exported test image decoded as 1280x960; this is an exported-file measurement and does not establish the camera sensor's native resolution. The test image and device logs are kept private.
