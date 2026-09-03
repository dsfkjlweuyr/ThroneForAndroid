#!/usr/bin/env python3
"""
Inspect range.go in common/xray/json/badoption/range.go.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def main():
    sb_throne = r"C:\repos\sing-box-throne"

    content = run_git(sb_throne, ["show", "origin/xhttp:common/xray/json/badoption/range.go"])
    print(content)

if __name__ == "__main__":
    main()
