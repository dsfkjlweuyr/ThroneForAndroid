#!/usr/bin/env python3
"""
Inspect Multiplex options in official sing-box option package.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def main():
    sb_official = r"C:\repos\sing-box"
    print(run_git(sb_official, ["grep", "-n", "Multiplex", "option/vless.go"]))
    print(run_git(sb_official, ["grep", "-n", "type.*Multiplex", "option/"]))

if __name__ == "__main__":
    main()
