#!/usr/bin/env python3
"""
Inspect official sing-box vless outbound implementation.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def main():
    sb_official = r"C:\repos\sing-box"
    content = run_git(sb_official, ["show", "v1.13.16:protocol/vless/outbound.go"])
    print(content)

if __name__ == "__main__":
    main()
