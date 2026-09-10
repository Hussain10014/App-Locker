#!/usr/bin/env python3
"""
ZenLock - Android Command-Line Locker (for Termux / PC ADB)
This script runs in a terminal on PC (via ADB) or directly inside Android Termux.
It initiates a timed lockdown session, locks the screen, and alerts you when time is up.
"""

import time
import subprocess
import sys
import shutil

def run_cmd(cmd):
    try:
        subprocess.run(cmd, shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return True
    except Exception:
        return False

def check_environment():
    """Detect whether running in Termux on phone or on PC with ADB."""
    if shutil.which("termux-wake-lock"):
        return "termux"
    elif shutil.which("adb"):
        return "adb"
    return "standard"

def lock_screen(env):
    """Sends lock command depending on environment."""
    if env == "adb":
        # Turn off / lock screen via Android Keyevent 26 (POWER)
        run_cmd("adb shell input keyevent 26")
    elif env == "termux":
        # Check if Termux-API or root lock command is present
        # In termux, can send keyevent if ADB over WiFi/wireless debugging or root is configured
        run_cmd("input keyevent 26 2>/dev/null || true")

def play_alert(env):
    if env == "termux":
        run_cmd("termux-vibrate -d 1000 2>/dev/null")
        run_cmd("termux-toast 'Focus session complete!' 2>/dev/null")
    print("\a") # ASCII terminal bell

def main():
    print("=" * 50)
    print("      ZENLOCK - TERMINAL LOCKDOWN FOR ANDROID")
    print("=" * 50)

    env = check_environment()
    if env == "adb":
        print("[+] Detected ADB on PC. Can control connected Android phone.")
    elif env == "termux":
        print("[+] Running inside Android Termux.")
    else:
        print("[i] Running in standard Python terminal mode.")

    try:
        minutes_input = input("\nEnter lock duration in minutes (e.g. 25): ").strip()
        minutes = int(minutes_input) if minutes_input else 25
    except ValueError:
        minutes = 25

    total_seconds = minutes * 60
    print(f"\n[!] Engaging {minutes}-minute lockdown.")
    
    if env == "termux":
        run_cmd("termux-wake-lock")
    
    lock_screen(env)
    print("[*] Screen locked. Keep away from your phone.\n")

    try:
        for remaining in range(total_seconds, 0, -1):
            mins, secs = divmod(remaining, 60)
            timer_format = f"[{mins:02d}:{secs:02d}] Locked in Focus... Press Ctrl+C for emergency exit."
            print(timer_format, end="\r", flush=True)
            time.sleep(1)

        print("\n\n🎉 [TIME UP] Focus session completed successfully! Phone unlocked.")
        play_alert(env)

    except KeyboardInterrupt:
        print("\n\n[!] Emergency exit triggered. Lock session cancelled.")
    finally:
        if env == "termux":
            run_cmd("termux-wake-unlock")

if __name__ == "__main__":
    main()
