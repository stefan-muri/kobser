"""Tiny uiautomator helper: dump the screen, find a node, tap it, type into it."""
import re, subprocess, sys, time, xml.etree.ElementTree as ET
import os
ADB = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk")), "platform-tools/adb")

def adb(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=check).stdout

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    return ET.fromstring(xml)

def nodes(root):
    return [n for n in root.iter("node")]

def find(root, text=None, contains=None, cls=None, index=0):
    hits = []
    for n in nodes(root):
        t = n.get("text", ""); d = n.get("content-desc", "")
        if text is not None and text not in (t, d): continue
        if contains is not None and contains.lower() not in (t + " " + d).lower(): continue
        if cls is not None and n.get("class") != cls: continue
        hits.append(n)
    return hits[index] if len(hits) > index else None

def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2

def tap(n):
    x, y = center(n); adb("shell", "input", "tap", str(x), str(y)); time.sleep(0.6)

def type_text(s):
    adb("shell", "input", "text", s.replace(" ", "%s")); time.sleep(0.3)

def screen_texts(root):
    return [n.get("text") for n in nodes(root) if n.get("text")]

if __name__ == "__main__":
    print(screen_texts(dump()))
