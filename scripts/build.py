"""Build the supported Lensmoo repair from locally supplied original APKs.

Python 3.10+, Java 17+, Android SDK Build Tools 37.0.0. No vendor source or
signing secrets are downloaded, uploaded, or committed by this script.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INPUTS = {
    "base.apk": "C5A4FC685852A03F0DAC543BF970C8F5CBCF934A179EF0F51C9CD9B51B50D96B",
    "split_config.arm64_v8a.apk": "2EAD968E6A4F2A1128B084DF3410CCD36E04E8DD2F26874909CA5BE15F3A889F",
    "split_config.en.apk": "08C4347D54103CDC3C2F4A7C95AA566277611131F376AE208F921E775143804C",
    "split_config.es.apk": "C3DA9C4E8C4ABF54F575C4D452ABFD37929E6A2877639D5A15C49393EB7391D4",
    "split_config.pt.apk": "8B2D64188A12292E18A775935342676684EBBF0A4CF952B0253ED8A4EF9A763F",
    "split_config.xxhdpi.apk": "CD092494B3A2D56B64F6680913F22EEE12A25BA72D56D8EAF064BABE5AEB4726",
}
TOOLS = {
    "apktool.jar": ("https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar",
                    "DBF930B076C6B9BE08D57C449CACEFC3BDD6B71EBD59B3066FC0E1F5B14F9423"),
    "APKEditor.jar": ("https://github.com/REAndroid/APKEditor/releases/download/V1.4.9/APKEditor-1.4.9.jar",
                      "A9CD40DF818845456BE6D696DE6110C89EDF4B0A0580CB83438ED6B25A366E67"),
}
IDENTITIES = ["com.lensmoo.app", "com.lensmoo.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
              "com.lensmoo.app.provider", "com.lensmoo.app.androidx-startup",
              "com.lensmoo.app.utilcode.fileprovider", "com.lensmoo.app.flutter.share_provider",
              "com.lensmoo.app.backgroundLibrary",
              "com.lensmoo.app.MicrosoftCognitiveServicesSpeech.InternalContentProvider",
              "com.lensmoo.app.com.pairip.licensecheck.LicenseContentProvider"]
OLD_CALL = "    invoke-direct {p1}, Landroid/os/Handler;-><init>()V"
NEW_CALL = ("    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;\n\n"
            "    move-result-object v0\n\n"
            "    invoke-direct {p1, v0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V")


def digest(data):
    return hashlib.sha256(data).hexdigest().upper()


def run(command):
    result = subprocess.run([str(part) for part in command], capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    if result.returncode:
        raise RuntimeError(result.stderr or result.stdout or "External tool failed")
    return result.stdout


def binary_string(data, old, new):
    if len(old) != len(new) or not old.isascii() or not new.isascii():
        raise ValueError("String replacement must preserve encoded lengths")
    for encoding, end in [("utf-8", b"\0"), ("utf-16le", b"\0\0")]:
        data = data.replace(old.encode(encoding) + end, new.encode(encoding) + end)
    return data


def signature_entry(name):
    return name == "stamp-cert-sha256" or bool(re.fullmatch(
        r"META-INF/(MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))", name, re.IGNORECASE))


def get_tools(directory, allow_download):
    directory.mkdir(parents=True, exist_ok=True)
    for name, (url, expected) in TOOLS.items():
        path = directory / name
        if not path.exists():
            if not allow_download:
                raise ValueError(f"Missing {name}; use --download-tools to fetch the pinned official release")
            print(f"Downloading pinned {name}", flush=True)
            with urllib.request.urlopen(url, timeout=90) as response, path.open("wb") as output:
                shutil.copyfileobj(response, output)
        if digest(path.read_bytes()) != expected:
            raise ValueError(f"Tool checksum mismatch: {name}")


def check_inputs(directory):
    for name, expected in INPUTS.items():
        path = directory / name
        if not path.is_file() or digest(path.read_bytes()) != expected:
            raise ValueError(f"Unsupported or altered input: {name}. Do not bypass the source hash guard.")


def patch_dex(text):
    if text.count(OLD_CALL) != 1:
        raise ValueError("Unexpected StarBurst callback; expected exactly one worker-thread Handler constructor")
    return text.replace(OLD_CALL, NEW_CALL)


def patch_preview_bridge(text):
    """Keep legacy behavior for other payloads; intercept the observed framed AVC path."""
    owner = "Lcom/lensmoo/business/ui/main/MainFragment;"
    bridge = "Ldev/lensfix/video/VideoSupport;"
    stop_signature = ".method private final stopDecoder()V"
    start = text.index(stop_signature)
    end = text.index("\n.end method", start) + len("\n.end method")
    original_stop = text[start:end]
    alias = original_stop.replace(stop_signature, ".method private final lensfixReleaseLegacyDecoder()V", 1)
    replacement = (stop_signature + "\n    .locals 0\n\n"
                   "    invoke-static {p0}, " + bridge + "->stop(Ljava/lang/Object;)V\n"
                   "    invoke-direct {p0}, " + owner + "->lensfixReleaseLegacyDecoder()V\n"
                   "    return-void\n.end method")
    text = text[:start] + replacement + text[end:]
    start = text.index(".method private final startDecoder()V")
    end = text.index("\n.end method", start)
    body = text[start:end]
    guard = ("    invoke-static {p0}, " + bridge + "->ownsPreview(Ljava/lang/Object;)Z\n"
             "    move-result v0\n"
             "    if-eqz v0, :lensfix_legacy_decoder\n"
             "    return-void\n"
             "    :lensfix_legacy_decoder\n\n")
    if body.count("    .line 1\n") != 1:
        raise ValueError("Unexpected legacy decoder entry")
    body = body.replace("    .line 1\n", guard + "    .line 1\n", 1)
    text = text[:start] + body + text[end:]
    for signature in ["private final decodeFrame([B)V", "public onPreviewResult([B)V"]:
        start = text.index(".method " + signature)
        end = text.index("\n.end method", start)
        body = text[start:end]
        hook = ("    invoke-static {p1}, " + bridge + "->handles([B)Z\n"
                "    move-result v0\n"
                "    if-eqz v0, :lensfix_original_preview\n"
                "    invoke-direct {p0}, " + owner + "->lensfixKeepPreviewReady()V\n"
                "    invoke-static {p0, p1}, " + bridge + "->accept(Ljava/lang/Object;[B)Z\n"
                "    move-result v0\n"
                "    return-void\n"
                "    :lensfix_original_preview\n\n")
        if body.count("    .line 1\n") != 1:
            raise ValueError("Unexpected preview callback body")
        body = body.replace("    .line 1\n", hook + "    .line 1\n", 1)
        text = text[:start] + body + text[end:]
    for signature in ["public onStop()V", "public onDestroy()V", "public onHiddenChanged(Z)V"]:
        start = text.index(".method " + signature)
        end = text.index("\n.end method", start)
        body = text[start:end]
        hook = "    invoke-static {p0}, " + bridge + "->stop(Ljava/lang/Object;)V\n\n"
        if "onHiddenChanged" in signature:
            hook = ("    if-eqz p1, :lensfix_visible\n" + hook + "    :lensfix_visible\n\n")
        if body.count("    .line 1\n") != 1:
            raise ValueError("Unexpected preview lifecycle body")
        body = body.replace("    .line 1\n", hook + "    .line 1\n", 1)
        text = text[:start] + body + text[end:]
    keep_ready = ("\n\n.method private final lensfixKeepPreviewReady()V\n    .locals 1\n\n"
                  "    iget-object v0, p0, " + owner + "->disposablePreviewTimeOut:Lcom/android/mltcode/paycertificationapi/ra2;\n"
                  "    if-eqz v0, :lensfix_timeout_done\n"
                  "    invoke-interface {v0}, Lcom/android/mltcode/paycertificationapi/ra2;->dispose()V\n"
                  "    const/4 v0, 0x0\n"
                  "    iput-object v0, p0, " + owner + "->disposablePreviewTimeOut:Lcom/android/mltcode/paycertificationapi/ra2;\n"
                  "    :lensfix_timeout_done\n"
                  "    const/4 v0, 0x0\n"
                  "    invoke-direct {p0, v0}, " + owner + "->enableBtn(I)V\n"
                  "    invoke-direct {p0}, " + owner + "->lensfixReleaseLegacyDecoder()V\n"
                  "    return-void\n.end method\n")
    return text + "\n\n" + alias + keep_ready


def patch_album_bridge(text):
    """Refresh local recordings when the existing album becomes visible."""
    for signature in ["public onResume()V", "public onHiddenChanged(Z)V"]:
        start = text.index(".method " + signature)
        end = text.index("\n.end method", start)
        body = text[start:end]
        hook = "    invoke-static {p0}, Ldev/lensfix/video/AlbumSupport;->refresh(Ljava/lang/Object;)V\n\n"
        if "onHiddenChanged" in signature:
            hook = "    if-nez p1, :lensfix_album_hidden\n" + hook + "    :lensfix_album_hidden\n\n"
        if body.count("    .line 1\n") != 1:
            raise ValueError("Unexpected album lifecycle body")
        body = body.replace("    .line 1\n", hook + "    .line 1\n", 1)
        text = text[:start] + body + text[end:]
    return text


def compile_video_helper(java_home, sdk_tools, android_jar, staging):
    if not android_jar.is_file():
        raise ValueError("Android platform JAR not found: " + str(android_jar))
    suffix = ".exe" if os.name == "nt" else ""
    classes = staging / "video-classes"
    classes.mkdir()
    sources = sorted((ROOT / "android/src").rglob("*.java"))
    run([java_home / ("bin/javac" + suffix), "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-classpath", android_jar, "-d", classes, *sources])
    archive = staging / "video-helper.jar"
    with zipfile.ZipFile(archive, "w") as output:
        for source in sorted(classes.rglob("*.class")):
            output.write(source, source.relative_to(classes).as_posix())
    target = staging / "video-dex"
    target.mkdir()
    run([java_home / ("bin/java" + suffix), "-cp", sdk_tools / "lib/d8.jar", "com.android.tools.r8.D8",
         "--release", "--min-api", "26", "--lib", android_jar, "--output", target, archive])
    return target / "classes.dex"


def build(args):
    check_inputs(args.input)
    java = args.java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    keytool = args.java_home / "bin" / ("keytool.exe" if os.name == "nt" else "keytool")
    platform_exe = ".exe" if os.name == "nt" else ""
    sdk_tools = args.sdk / "build-tools" / args.build_tools_version
    zipalign = sdk_tools / ("zipalign" + platform_exe)
    signer = sdk_tools / "lib/apksigner.jar"
    for required in [java, keytool, zipalign, signer]:
        if not required.is_file():
            raise ValueError(f"Missing required build tool: {required}")
    args.work.mkdir(parents=True, exist_ok=True)
    tools = args.work / "tools"
    get_tools(tools, args.download_tools)
    # A new directory prevents edits or stale decompiled files contaminating a release.
    staging = Path(tempfile.mkdtemp(prefix="build-", dir=args.work))
    decoded = staging / "decoded"
    print("Decoding verified original APK", flush=True)
    run([java, "-jar", tools / "apktool.jar", "d", "-r", args.input / "base.apk", "-o", decoded])
    target = decoded / "smali_classes5/com/starburst/sdk/StarBurstSdk$a.smali"
    target.write_text(patch_dex(target.read_text(encoding="utf-8")), encoding="utf-8")
    preview = decoded / "smali_classes5/com/lensmoo/business/ui/main/MainFragment.smali"
    preview.write_text(patch_preview_bridge(preview.read_text(encoding="utf-8")), encoding="utf-8")
    album = decoded / "smali_classes5/com/lensmoo/business/ui/photo/PhotoLibraryFragment.smali"
    album.write_text(patch_album_bridge(album.read_text(encoding="utf-8")), encoding="utf-8")
    helper = compile_video_helper(args.java_home, sdk_tools,
                                  args.sdk / "platforms" / args.android_platform / "android.jar", staging)
    dex = staging / "classes5.dex"
    print("Assembling repaired DEX only", flush=True)
    run([java, "-cp", tools / "apktool.jar", ROOT / "scripts/AssembleLensmooDex.java",
         decoded / "smali_classes5", dex])
    splits = staging / "splits"
    splits.mkdir()
    for name in INPUTS:
        with zipfile.ZipFile(args.input / name) as source, zipfile.ZipFile(splits / name, "w") as output:
            for info in source.infolist():
                if signature_entry(info.filename):
                    continue
                data = source.read(info.filename)
                if name == "base.apk" and info.filename == "classes5.dex":
                    data = dex.read_bytes()
                elif info.filename == "AndroidManifest.xml":
                    for identity in IDENTITIES:
                        data = binary_string(data, identity, identity.replace("com.lensmoo.app", "com.lensmoo.fix", 1))
                elif info.filename == "resources.arsc":
                    for label in ["Lensmoo", "LensMoo"]:
                        data = binary_string(data, label, "LensFix")
                output.writestr(info, data)
            if name == "base.apk":
                output.writestr("classes8.dex", helper.read_bytes(), compress_type=zipfile.ZIP_STORED)
    print("Merging arm64 APK and language resources", flush=True)
    merged = staging / "merged.apk"
    run([java, "-jar", tools / "APKEditor.jar", "m", "-i", splits, "-o", merged])
    aligned = staging / "aligned.apk"
    run([zipalign, "-P", "16", "-f", "4", merged, aligned])
    keystore = args.keystore or args.work / "private/signing.jks"
    password = args.password_file or args.work / "private/password.txt"
    if not keystore.exists():
        if args.keystore or args.password_file:
            raise ValueError("Specified existing signing material was not found")
        keystore.parent.mkdir(parents=True, exist_ok=True)
        password.write_text(secrets.token_urlsafe(32), encoding="ascii")
        os.chmod(password, 0o600)
        run([keytool, "-genkeypair", "-keystore", keystore, "-alias", args.key_alias,
             "-storepass:file", password, "-keypass:file", password, "-keyalg", "RSA",
             "-keysize", "2048", "-validity", "3650", "-dname", "CN=LensFix Community Repair"])
    if not password.is_file():
        raise ValueError("Signing password file was not found")
    args.output.mkdir(parents=True, exist_ok=True)
    apk = args.output / "LensFix-arm64.apk"
    run([java, "-jar", signer, "sign", "--ks", keystore, "--ks-key-alias", args.key_alias,
         "--ks-pass", f"file:{password}", "--out", apk, aligned])
    certificate = run([java, "-jar", signer, "verify", "--verbose", "--print-certs", apk])
    run([zipalign, "-c", "-P", "16", "4", apk])
    # Check executable content after merging, alignment, and signing.
    checked = 0
    with zipfile.ZipFile(apk) as final:
        if final.read("classes8.dex") != helper.read_bytes():
            raise ValueError("Video helper changed during packaging")
        for name in INPUTS:
            with zipfile.ZipFile(args.input / name) as original:
                for entry in original.namelist():
                    if not entry.endswith("/") and (entry.startswith(("assets/", "lib/")) or re.fullmatch(r"classes\d*\.dex", entry)):
                        expected = dex.read_bytes() if entry == "classes5.dex" else original.read(entry)
                        if final.read(entry) != expected:
                            raise ValueError(f"Unexpected executable or asset change after merge: {entry}")
                        checked += 1
    cert_match = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", certificate)
    if not cert_match:
        raise ValueError("Could not record the verified public signing certificate fingerprint")
    manifest = {"release": "1.1.0", "upstreamVersion": "1.8.0 (80)", "package": "com.lensmoo.fix",
                "file": apk.name, "sha256": digest(apk.read_bytes()), "bytes": apk.stat().st_size,
                "signerCertificateSha256": cert_match.group(1).upper(),
                "sourceApks": INPUTS, "tools": {name: checksum for name, (_, checksum) in TOOLS.items()},
                "repair": "StarBurstSdk$a.onFailure uses Handler(Looper.getMainLooper())",
                "video": "Type-2 framed AVC preview, serialized Surface decoder, explicit local MP4 recording using MediaMuxer/MediaStore",
                "album": "Own-app finalized MediaStore videos shown in the existing album, without modifying the photo database",
                "addedDex": {"file": "classes8.dex", "sha256": digest(helper.read_bytes()), "source": "android/src/dev/lensfix/video"},
                "unchangedDex": ["classes.dex", "classes2.dex", "classes3.dex", "classes4.dex", "classes6.dex", "classes7.dex"],
                "executableAndAssetEntriesVerified": checked, "signatureVerified": True, "zipAlignment16KBVerified": True,
                "packagingChanges": ["Separate com.lensmoo.fix package and provider authorities", "LensFix launcher label",
                                     "Merge arm64, en, es, pt, and xxhdpi splits into a standalone APK; split-only manifest metadata removed"],
                "scope": "Vendor VAD invalid-key response remains unresolved. Video records the live preview locally without audio; it does not retrieve stored glasses recordings."}
    (args.output / "build-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (args.output / "SHA256SUMS.txt").write_text(f"{manifest['sha256'].lower()}  {apk.name}\n", encoding="ascii")
    print(json.dumps({"apk": str(apk), "sha256": manifest["sha256"], "verifiedEntries": checked}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="Directory containing the six supported original APKs")
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True, help="Android SDK root")
    parser.add_argument("--build-tools-version", default="37.0.0")
    parser.add_argument("--android-platform", default="android-36")
    parser.add_argument("--work", type=Path, default=ROOT / "work")
    parser.add_argument("--output", type=Path, default=ROOT / "dist")
    parser.add_argument("--download-tools", action="store_true")
    parser.add_argument("--keystore", type=Path)
    parser.add_argument("--password-file", type=Path)
    parser.add_argument("--key-alias", default="lensfix-local")
    args = parser.parse_args()
    for name in ["input", "java_home", "sdk", "work", "output", "keystore", "password_file"]:
        value = getattr(args, name)
        if value is not None:
            setattr(args, name, value.resolve())
    build(args)


if __name__ == "__main__":
    main()
