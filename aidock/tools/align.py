#!/usr/bin/env python3
"""Merge classes.dex into an aapt2 APK and 4-byte-align stored entries (a tiny zipalign)."""
import struct, sys, zipfile

src, dex, out = sys.argv[1:4]
STORE = {"resources.arsc"}
zin = zipfile.ZipFile(src)
items = [(i.filename, zin.read(i.filename)) for i in zin.infolist()]
items.append(("classes.dex", open(dex, "rb").read()))

with open(out, "wb") as f:
    zout = zipfile.ZipFile(f, "w")
    for name, data in items:
        zi = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        zi.external_attr = 0o644 << 16
        stored = name in STORE or name.endswith((".woff2", ".png"))
        zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        if stored:
            off = f.tell() + 30 + len(name.encode())
            pad = (-off) % 4
            if pad:
                pad += 4
                zi.extra = struct.pack("<HH", 0xD935, pad - 4) + b"\0" * (pad - 4)
        zout.writestr(zi, data)
    zout.close()
