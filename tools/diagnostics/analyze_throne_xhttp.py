#!/usr/bin/env python3
"""
Inspect how Throne Desktop routes and runs xhttp.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def main():
    throne_pc = r"C:\repos\Throne"
    sb_throne = r"C:\repos\sing-box-throne"

    print("--- 1. src/configs/common/utils.cpp in Throne ---")
    print(run_git(throne_pc, ["grep", "-n", "-C", "15", "xray_vless_preference", "src/"]))

    print("\n--- 2. How Throne Desktop defines core / outbounds for sing-box vs xray ---")
    print(run_git(throne_pc, ["grep", "-n", "-C", "5", "xhttp", "src/configs/singbox/"]))
    print(run_git(throne_pc, ["grep", "-n", "-C", "5", "xhttp", "src/configs/xray/"]))

    print("\n--- 3. In sing-box-throne, which branch is used or merged? ---")
    print("Branches containing a9e3ceef (latest xhttp):")
    print(run_git(sb_throne, ["branch", "-a", "--contains", "a9e3ceef"]))
    print("Tags containing a9e3ceef:")
    print(run_git(sb_throne, ["tag", "--contains", "a9e3ceef"]))

    print("\n--- 4. Is xhttp merged into sing-box-throne stable or dev? ---")
    print(run_git(sb_throne, ["log", "origin/stable", "--grep=xhttp", "-n", "3", "--oneline"]))
    print(run_git(sb_throne, ["log", "origin/dev", "--grep=xhttp", "-n", "3", "--oneline"]))

if __name__ == "__main__":
    main()
