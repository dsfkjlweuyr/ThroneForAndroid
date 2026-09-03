#!/usr/bin/env python3
"""
Inspect recursive dependencies of the needed xray packages.
"""

import subprocess

def run_git(repo_path, args):
    cmd = ["git", "-C", repo_path] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip()

def get_imports(content):
    imports = set()
    in_import = False
    for line in content.splitlines():
        line = line.strip()
        if line.startswith("import ("):
            in_import = True
            continue
        if in_import:
            if line == ")":
                in_import = False
                continue
            pkg = line.split("//")[0].strip().strip('"')
            if pkg:
                imports.add(pkg)
        elif line.startswith("import "):
            pkg = line[7:].split("//")[0].strip().strip('"')
            if pkg:
                imports.add(pkg)
    return imports

def main():
    sb_throne = r"C:\repos\sing-box-throne"

    xray_dirs = [
        "common/xray",
        "common/xray/buf",
        "common/xray/net",
        "common/xray/pipe",
        "common/xray/signal/done",
        "common/xray/uuid",
        "common/xray/bytespool",
        "common/xray/serial",
        "common/xray/errors"
    ]

    all_files = run_git(sb_throne, ["ls-tree", "-r", "--name-only", "origin/xhttp", "common/xray"]).splitlines()

    for xd in xray_dirs:
        dir_files = [f for f in all_files if f.startswith(xd + "/") and f.count("/") == xd.count("/") + 1 and f.endswith(".go") and not f.endswith("_test.go")]
        print(f"\nDirectory {xd} has {len(dir_files)} files: {[f.split('/')[-1] for f in dir_files]}")
        dir_imports = set()
        for f in dir_files:
            c = run_git(sb_throne, ["show", f"origin/xhttp:{f}"])
            dir_imports.update(get_imports(c))
        print("  Imports from xray:", [i for i in dir_imports if "xray" in i])
        print("  External imports:", [i for i in dir_imports if "xray" not in i and not i.startswith("github.com/sagernet/sing-box")])

if __name__ == "__main__":
    main()
