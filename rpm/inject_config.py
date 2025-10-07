#!/usr/bin/python3

# Teragrep ZEP_01
# Copyright (C) 2025  Suomen Kanuuna Oy
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://github.com/teragrep/teragrep/blob/main/LICENSE>.
#
#
# Additional permission under GNU Affero General Public License version 3
# section 7
#
# If you modify this Program, or any covered work, by linking or combining it
# with other code, such other code is not for that reason alone subject to any
# of the requirements of the GNU Affero GPL version 3 as long as this Program
# is the same Program as licensed from Suomen Kanuuna Oy without any additional
# modifications.
#
# Supplemented terms under GNU Affero General Public License version 3
# section 7
#
# Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
# versions must be marked as "Modified version of" The Program.
#
# Names of the licensors and authors may not be used for publicity purposes.
#
# No rights are granted for use of trade names, trademarks, or service marks
# which are in The Program if any.
#
# Licensee must indemnify licensors and authors for any liability that these
# contractual assumptions impose on licensors and authors.
#
# To the extent this program is licensed as part of the Commercial versions of
# Teragrep, the applicable Commercial License may apply to this file if you as
# a licensee so wish it.

import json
import os
import sys
import shutil
import time

interpreter_settings_file = os.environ.get("INTERPRETER_SETTINGS_FILE", "/opt/teragrep/zep_01/interpreter/spark/interpreter-setting.json")
interpreter_file = os.environ.get("INTERPRETER_FILE", "/opt/teragrep/zep_01/conf/interpreter.json")

# Check interpreter settings file exists
if not os.path.isfile(interpreter_settings_file):
    print(f"Can't find {filename}, failing.")
    sys.exit(1)

# Read interpreter settings file
try:
    config = json.load(open(interpreter_settings_file, "r"))
except Exception as e:
    print(f"Can't read {interpreter_settings_file}: {e}")
    sys.exit(1)

# Patch interpreter.json if any
if os.path.isfile(interpreter_file):
    try:
        interpreter = json.load(open(interpreter_file, "r"))
    except Exception as e:
        print(f"Can't read {interpreter_file}: {e}")
        sys.exit(1)
    # Check if configs should be patched and flag for rewrite if we do
    rewrite = False
    for key in config["properties"]:
        if key not in interpreter["interpreterSettings"]["spark"]["properties"]:
            print(f"Adding {key} to {interpreter_file}")
            rewrite = True
            # interpreter.json has different format
            new_props = { "envName": cofnig["properties"][key]["envName"], "name": key, "value": config["properties"][key]["defaultValue"], "type": config["properties"][key]["type"], "description": config["properties"][key]["description"] }
            interpreter["interpreterSettings"]["spark"]["properties"][key] = new_props
    if rewrite:
        # We dont care about decimals
        timestamp = int(time.time())
        print(f"Copying {interpreter_file} to {interpreter_file}.{timestamp}")
        shutil.copy(interpreter_file, f"{interpreter_file}.{timestamp}")
        print(f"Patching {interpreter_file}")
        with open(interpreter_file, "w") as fh:
            fh.write(json.dumps(interpreter, indent=2))
