#!/usr/bin/env python3
"""
Audit xhttp packages and internal xray utilities in libcore for symbol and import consistency.
"""

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIBCORE_ROOT = os.path.join(REPO_ROOT, "libcore")
VLESS_DIR = os.path.join(LIBCORE_ROOT, "protocol", "vless")

def get_go_files(directory):
    go_files = []
    for root, _, files in os.walk(directory):
        for f in files:
            if f.endswith(".go") and not f.endswith("_test.go"):
                go_files.append(os.path.join(root, f))
    return go_files

def extract_package(content):
    m = re.search(r'^\s*package\s+([a-zA-Z0-9_]+)', content, re.MULTILINE)
    return m.group(1) if m else None

def extract_imports_and_code(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    imports = []
    block_match = re.findall(r'import\s*\((.*?)\)', content, re.DOTALL)
    for block in block_match:
        for line in block.splitlines():
            line = line.strip()
            line = re.sub(r'//.*$', '', line).strip()
            m = re.search(r'"([^"]+)"', line)
            if m:
                alias = line[:m.start()].strip()
                imports.append((alias, m.group(1)))

    single_matches = re.findall(r'import\s+(?:([a-zA-Z0-9_]+)\s+)?"([^"]+)"', content)
    for alias, pkg in single_matches:
        imports.append((alias, pkg))

    # Remove import blocks and comments from code to analyze usage
    code_without_imports = re.sub(r'import\s*\((.*?)\)', '', content, flags=re.DOTALL)
    code_without_imports = re.sub(r'import\s+(?:[a-zA-Z0-9_]+\s+)?"[^"]+"', '', code_without_imports)
    code_without_comments = re.sub(r'//.*$', '', code_without_imports, flags=re.MULTILINE)
    code_without_comments = re.sub(r'/\*.*?\*/', '', code_without_comments, flags=re.DOTALL)

    return imports, extract_package(content), code_without_comments

KNOWN_PACKAGE_NAMES = {
    "libcore/protocol/vless/internal/xray": "common",
    "github.com/sagernet/quic-go": "quic",
    "golang.org/x/net/http2": "http2",
}

def get_effective_identifier(alias, pkg):
    if alias and alias != "_":
        return alias
    if pkg in KNOWN_PACKAGE_NAMES:
        return KNOWN_PACKAGE_NAMES[pkg]
    return pkg.split("/")[-1]

def main():
    print("=== Auditing libcore/protocol/vless Go packages ===")
    go_files = get_go_files(VLESS_DIR)
    print(f"Found {len(go_files)} Go files in {VLESS_DIR}")

    errors = []
    known_local_prefixes = {
        "libcore/protocol/vless/internal/xray",
        "libcore/protocol/vless/xhttp",
    }

    packages_by_dir = {}

    for gf in go_files:
        rel_path = os.path.relpath(gf, REPO_ROOT)
        imports, pkg_name, code = extract_imports_and_code(gf)
        if not pkg_name:
            errors.append(f"[{rel_path}] Missing package declaration")

        dir_path = os.path.dirname(gf)
        if dir_path not in packages_by_dir:
            packages_by_dir[dir_path] = (pkg_name, rel_path)
        else:
            first_pkg, first_file = packages_by_dir[dir_path]
            if pkg_name != first_pkg:
                errors.append(f"[{rel_path}] Inconsistent package name '{pkg_name}' (expected '{first_pkg}' from {first_file})")

        for alias, pkg in imports:
            if "sing-box/common/xray" in pkg or "sing-box/transport/v2rayxhttp" in pkg:
                errors.append(f"[{rel_path}] Stale upstream import found: {pkg}")
                continue

            if any(pkg.startswith(prefix) for prefix in known_local_prefixes):
                expected_dir = os.path.join(REPO_ROOT, os.path.normpath(pkg))
                if not os.path.isdir(expected_dir):
                    errors.append(f"[{rel_path}] Unresolved internal import: {pkg} -> {expected_dir} not found")

            if alias == "_":
                continue
            ident = get_effective_identifier(alias, pkg)
            if not re.search(r'\b' + re.escape(ident) + r'\b', code):
                errors.append(f"[{rel_path}] Unused import: '{pkg}' (identifier '{ident}')")

    print(f"Validated {len(packages_by_dir)} distinct Go package directories.")
    for d, (p, f) in sorted(packages_by_dir.items()):
        rel_d = os.path.relpath(d, REPO_ROOT)
        print(f"  {rel_d}: package {p}")

    if errors:
        print(f"\nFAILED with {len(errors)} errors:")
        for e in errors:
            print("  ERROR:", e)
        sys.exit(1)
    else:
        print("\nSUCCESS: All package declarations, internal imports, and symbol usages are valid!")

if __name__ == "__main__":
    main()
