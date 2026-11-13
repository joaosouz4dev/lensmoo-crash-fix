"""Observe the actual Android process and its crash buffer during a user action."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", help="Optional device serial; never printed in the report")
    parser.add_argument("--package", choices=["com.lensmoo.app", "com.lensmoo.fix"], default="com.lensmoo.fix")
    parser.add_argument("--seconds", type=int, default=30)
    parser.add_argument("--output", type=Path, help="Optional JSON report path; no raw logs are saved")
    args = parser.parse_args()
    if not 1 <= args.seconds <= 60:
        parser.error("--seconds must be between 1 and 60")
    command = [args.adb] + (["-s", args.serial] if args.serial else [])

    def adb(*parts):
        return subprocess.run(command + list(parts), capture_output=True, text=True,
                              encoding="utf-8", errors="replace")

    state = adb("get-state")
    if state.returncode or state.stdout.strip() != "device":
        raise SystemExit("Connect and authorize one Android device, or specify --serial")
    start = adb("shell", "date '+%m-%d %H:%M:%S.000'")
    if start.returncode:
        raise SystemExit("Could not obtain the device's log timestamp")
    start_text = start.stdout.strip()
    seen = set()
    deadline = time.monotonic() + args.seconds
    while time.monotonic() < deadline:
        seen.update(p for p in adb("shell", "pidof", args.package).stdout.split() if p.isdigit())
        time.sleep(0.5)
    crash = "\n".join(adb("logcat", "-b", "crash", "-d", "-T", start_text, f"--pid={pid}",
                           "AndroidRuntime:E", "*:S").stdout for pid in sorted(seen))
    alive = bool(adb("shell", "pidof", args.package).stdout.strip())
    has_crash = bool(re.search(r"Process: " + re.escape(args.package) + ",", crash))
    result = {"observedAtUtc": datetime.now(timezone.utc).isoformat(), "package": args.package,
              "seconds": args.seconds, "processObserved": bool(seen), "runningAtEnd": alive,
              "crashDetected": has_crash,
              "handlerLooperCrashDetected": has_crash and "has not called Looper.prepare()" in crash,
              "limitation": "Exercise the original trigger during observation. This is not full functionality validation."}
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    raise SystemExit(1 if has_crash else (0 if seen and alive else 2))


if __name__ == "__main__":
    main()
