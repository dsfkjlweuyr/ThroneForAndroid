#!/usr/bin/env python3
"""
Transplant xhttp driver and internal xray utilities from sing-box-throne into libcore.
"""

import os
import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout

def replace_imports(content):
    replacements = [
        ("github.com/sagernet/sing-box/common/xray/json/badoption", "libcore/protocol/vless/internal/xray/badoption"),
        ("github.com/sagernet/sing-box/common/xray/buf", "libcore/protocol/vless/internal/xray/buf"),
        ("github.com/sagernet/sing-box/common/xray/bytespool", "libcore/protocol/vless/internal/xray/bytespool"),
        ("github.com/sagernet/sing-box/common/xray/crypto", "libcore/protocol/vless/internal/xray/crypto"),
        ("github.com/sagernet/sing-box/common/xray/errors", "libcore/protocol/vless/internal/xray/errors"),
        ("github.com/sagernet/sing-box/common/xray/net", "libcore/protocol/vless/internal/xray/net"),
        ("github.com/sagernet/sing-box/common/xray/pipe", "libcore/protocol/vless/internal/xray/pipe"),
        ("github.com/sagernet/sing-box/common/xray/serial", "libcore/protocol/vless/internal/xray/serial"),
        ("github.com/sagernet/sing-box/common/xray/signal/done", "libcore/protocol/vless/internal/xray/signal/done"),
        ("github.com/sagernet/sing-box/common/xray/signal/semaphore", "libcore/protocol/vless/internal/xray/signal/semaphore"),
        ("github.com/sagernet/sing-box/common/xray/signal/pubsub", "libcore/protocol/vless/internal/xray/signal/pubsub"),
        ("github.com/sagernet/sing-box/common/xray/signal", "libcore/protocol/vless/internal/xray/signal"),
        ("github.com/sagernet/sing-box/common/xray/uuid", "libcore/protocol/vless/internal/xray/uuid"),
        ("github.com/sagernet/sing-box/common/xray", "libcore/protocol/vless/internal/xray"),
        ("github.com/sagernet/sing-box/transport/v2rayxhttp", "libcore/protocol/vless/xhttp"),
    ]
    for old, new in replacements:
        content = content.replace(f'"{old}"', f'"{new}"')
        content = content.replace(f'"{old}/', f'"{new}/')
    return content

def main():
    sb_throne = r"C:\repos\sing-box-throne"
    target_base = r"libcore/protocol/vless"

    # 1. Internal xray files
    xray_tree = run_git(sb_throne, ["ls-tree", "-r", "--name-only", "origin/xhttp", "common/xray"]).splitlines()
    for rel_path in xray_tree:
        if not rel_path.endswith(".go") or rel_path.endswith("_test.go"):
            continue
        # common/xray/json/badoption/range.go -> internal/xray/badoption/range.go
        if "common/xray/json/badoption" in rel_path:
            sub = rel_path.replace("common/xray/json/badoption", "internal/xray/badoption")
        else:
            sub = rel_path.replace("common/xray", "internal/xray")
        dest_path = os.path.join(target_base, sub)
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        content = run_git(sb_throne, ["show", f"origin/xhttp:{rel_path}"])
        content = replace_imports(content)
        with open(dest_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Wrote {dest_path}")

    # 2. XHTTP client files (exclude server.go)
    client_files = [
        'transport/v2rayxhttp/client.go',
        'transport/v2rayxhttp/conn.go',
        'transport/v2rayxhttp/dialer.go',
        'transport/v2rayxhttp/http.go',
        'transport/v2rayxhttp/mux.go',
        'transport/v2rayxhttp/upload_queue.go',
        'transport/v2rayxhttp/writer.go'
    ]
    xhttp_dest_dir = os.path.join(target_base, "xhttp")
    os.makedirs(xhttp_dest_dir, exist_ok=True)
    for src in client_files:
        filename = os.path.basename(src)
        dest_path = os.path.join(xhttp_dest_dir, filename)
        content = run_git(sb_throne, ["show", f"origin/xhttp:{src}"])
        content = replace_imports(content)
        # Adapt option references within xhttp package
        content = content.replace("option.V2RayXHTTPOptions", "V2RayXHTTPOptions")
        content = content.replace("option.NormalizeXHTTPMode", "NormalizeXHTTPMode")
        content = content.replace("C.V2RayTransportTypeXHTTP", "V2RayTransportTypeXHTTP")
        with open(dest_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Wrote {dest_path}")

if __name__ == "__main__":
    main()
