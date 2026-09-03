#!/usr/bin/env python3
"""
Inspect the rest of V2RayXHTTPOptions in origin/xhttp:option/v2ray_transport.go.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def main():
    sb_throne = r"C:\repos\sing-box-throne"

    content = run_git(sb_throne, ["show", "origin/xhttp:option/v2ray_transport.go"])
    lines = content.splitlines()
    for i, line in enumerate(lines):
        if "type V2RayXHTTPBaseOptions struct" in line:
            print("\n".join(lines[i:min(len(lines), i+80)]))
            break

if __name__ == "__main__":
    main()
