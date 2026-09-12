"""Emulator smoke test: install the APK, log in, play a song, assert the player runs.

Catches the class of bug unit tests can't (e.g. a Media3 session change that
silently drops player commands). Needs a booted emulator/device on adb and a
reachable kobser server with at least one song.

    python3 smoke.py app/build/outputs/apk/debug/app-debug.apk http://10.0.2.2:8001 user pass
"""
import sys, time
from ui import adb, dump, find, tap, type_text, screen_texts

apk, server, user, password = sys.argv[1:5]

def ime_shown(): return "mInputShown=true" in adb("shell", "dumpsys", "input_method")
def hide_ime():
    for _ in range(4):
        if not ime_shown(): return
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(0.7)
def edit_fields(): return [n for n in dump().iter("node") if n.get("class") == "android.widget.EditText"]
def fill(i, value):
    tap(edit_fields()[i]); adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(60): adb("shell", "input", "keyevent", "KEYCODE_DEL")
    type_text(value); hide_ime()

adb("shell", "pm", "uninstall", "com.kobser.app", check=False)
print("install:", adb("install", "-r", apk).strip().splitlines()[-1])
adb("logcat", "-c")
adb("shell", "am", "start", "-n", "com.kobser.app/.MainActivity"); time.sleep(4)
assert len(edit_fields()) == 3, "login screen not shown"
fill(0, server); fill(1, user); fill(2, password)
tap([n for n in dump().iter("node") if n.get("class") == "android.widget.Button"][0]); time.sleep(7)
root = dump()
assert find(root, text="Play all") is not None, f"login failed: {screen_texts(root)[:8]}"

# First song row: the first clickable text below the sort header.
texts = screen_texts(root)
song = find(root, text=texts[texts.index("Sort: Recently added") + 1]) if "Sort: Recently added" in texts else None
assert song is not None, f"no song row found: {texts[:10]}"
print("playing:", song.get("text")); tap(song); time.sleep(10)

state = [l for l in adb("shell", "dumpsys", "media_session").splitlines() if "state=PlaybackState" in l]
denied = [l for l in adb("logcat", "-d").splitlines() if "isn't allowed to call command" in l]
print("playback:", state[0].strip()[:80] if state else "no session")
assert not denied, f"controller commands denied: {denied[:2]}"
assert state and "PLAYING" in state[0], "player is not PLAYING"
print("OK")
