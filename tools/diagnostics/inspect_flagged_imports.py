#!/usr/bin/env python3
"""
Inspect exact usage of internal/xray in the flagged files.
"""

import os
import re

flagged = [
    r"libcore/protocol/vless/internal/xray/buf/multi_buffer.go",
    r"libcore/protocol/vless/internal/xray/buf/reader.go",
    r"libcore/protocol/vless/internal/xray/buf/writer.go",
    r"libcore/protocol/vless/internal/xray/pipe/impl.go",
    r"libcore/protocol/vless/internal/xray/signal/timer.go",
    r"libcore/protocol/vless/internal/xray/signal/pubsub/pubsub.go",
    r"libcore/protocol/vless/internal/xray/task/common.go",
    r"libcore/protocol/vless/internal/xray/uuid/uuid.go",
    r"libcore/protocol/vless/xhttp/dialer.go"
]

for f in flagged:
    with open(f, "r", encoding="utf-8") as file:
        content = file.read()
    print(f"\n=== {f} ===")
    for line in content.splitlines()[:30]:
        if "import" in line or "internal/xray" in line:
            print(line)
    
    # Check if 'common.' is used
    m_common = re.findall(r'\bcommon\.\w+', content)
    print("  common.* usage:", set(m_common))
    m_xray = re.findall(r'\bxray\.\w+', content)
    print("  xray.* usage:", set(m_xray))
