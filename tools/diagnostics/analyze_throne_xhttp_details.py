#!/usr/bin/env python3
"""
Inspect sing-box-throne origin/xhttp branch implementation.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def main():
    sb_throne = r"C:\repos\sing-box-throne"

    print("=== Commits on origin/xhttp ===")
    commits = run_git(sb_throne, ["log", "origin/xhttp", "--oneline", "-n", "30"])
    print(commits)

    print("\n=== Check client.go in transport/v2rayxhttp ===")
    client_go = run_git(sb_throne, ["show", "origin/xhttp:transport/v2rayxhttp/client.go"])
    print("\n".join(client_go.splitlines()[:60]))

    print("\n=== Check how transport is hooked into transport/v2ray/transport.go in origin/xhttp ===")
    diff_v2ray = run_git(sb_throne, ["diff", "origin/stable..origin/xhttp", "--", "transport/v2ray/transport.go"])
    print(diff_v2ray)

    print("\n=== Check how vless outbound uses it in origin/xhttp ===")
    diff_vless = run_git(sb_throne, ["diff", "origin/stable..origin/xhttp", "--", "protocol/vless/"])
    print(diff_vless)

if __name__ == "__main__":
    main()
