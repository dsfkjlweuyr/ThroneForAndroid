#!/usr/bin/env python3
"""
Inspect adapter.V2RayClientTransport in official sing-box.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def main():
    sb_official = r"C:\repos\sing-box"
    print(run_git(sb_official, ["grep", "-n", "-C", "10", "type V2RayClientTransport", "adapter/"]))

if __name__ == "__main__":
    main()
