#!/usr/bin/env python3
"""
Inspect common/xray in sing-box-throne origin/xhttp.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def main():
    sb_throne = r"C:\repos\sing-box-throne"

    files = run_git(sb_throne, ["ls-tree", "-r", "--name-only", "origin/xhttp", "common/xray"]).splitlines()
    print("Files in common/xray:")
    for f in sorted(files):
        print(" ", f)

    print("\nDoes client.go or others in v2rayxhttp call server.go?")
    res = run_git(sb_throne, ["grep", "-n", "NewServer", "transport/v2rayxhttp/"])
    print(res)

if __name__ == "__main__":
    main()
