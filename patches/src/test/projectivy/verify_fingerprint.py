#!/usr/bin/env python3
"""Verify version-specific fingerprint assumptions against the developer-supplied APK.

Usage: python -m pip install androguard && python verify_fingerprint.py /path/to/app.apk
This is a static bytecode check, not a Morphe patch application or device test.
"""
import sys
from pathlib import Path

from androguard.core.apk import APK
from androguard.core.dex import DEX
from loguru import logger

logger.remove()
apk = APK(sys.argv[1])
assert apk.package == "com.spocky.projengmenu"
assert apk.get_androidversion_name() == "4.73"
assert apk.get_androidversion_code() == "98"
classes = [c for dex in apk.get_all_dex() for c in DEX(dex).get_classes()
           if c.get_name() == "Lcom/spocky/projengmenu/PTApplication;"]
assert len(classes) == 1
methods = classes[0].get_methods()
setter = [m for m in methods if m.get_name() == "d" and m.get_descriptor() == "(Z)V"]
assert len(setter) == 1 and set(setter[0].get_access_flags_string().split()) >= {"public", "final"}
ops = [(i.get_name(), i.get_output()) for i in setter[0].get_instructions()]
clock = [n for n, (_, output) in enumerate(ops) if "Ljava/lang/System;->currentTimeMillis()J" in output]
update = [n for n, (_, output) in enumerate(ops) if "Llf4;->h(Ljava/lang/Object;)V" in output]
assert len(clock) == len(update) == 1 and clock[0] < update[0]
assert ops[0][0] == "if-eqz" and "v5" in ops[0][1]
assert setter[0].get_code().get_registers_size() == 6  # p1 is v5; const/4 is encodable

constructor = [m for m in methods if m.get_name() == "<init>" and m.get_descriptor() == "()V"]
assert len(constructor) == 1
ops = [(i.get_name(), i.get_output()) for i in constructor[0].get_instructions()]
initial = [n for n, (_, output) in enumerate(ops) if "Ljava/lang/Boolean;->TRUE" in output]
assert len(initial) == 1 and ops[initial[0]][0] == "sget-object"
assert any("Lts1;->a(Ljava/lang/Object;)Llf4;" in out for _, out in ops[initial[0]+1:])
assert any("Lzv4;->c(Llf4;)Liq3;" in out for _, out in ops[initial[0]+1:])
source = (Path(__file__).resolve().parents[2] / "main/kotlin/app/template/patches/projectivy/UnlockPremiumPatch.kt").read_text()
setter_source = source.split("private object RestrictedModeSetter", 1)[1].split("private object ApplicationConstructor", 1)[0]
assert setter_source.index('name = "currentTimeMillis"') < setter_source.index('name = "h"')
print("Projectivy 4.73 setter and constructor fingerprint assumptions verified")
