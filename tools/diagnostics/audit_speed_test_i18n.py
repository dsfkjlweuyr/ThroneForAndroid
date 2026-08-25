from __future__ import annotations

import sys
import re
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RES_ROOT = ROOT / "app" / "src" / "main" / "res"

REQUIRED_KEYS = (
    "connection_test_url",
    "connection_test_url_test",
    "speed_test_settings",
    "speed_test_mode",
    "speed_test_mode_download_upload",
    "speed_test_mode_download",
    "speed_test_mode_upload",
    "speed_test_mode_simple_download",
    "speed_test_timeout_ms",
    "speed_test_timeout_invalid",
    "simple_download_url",
    "speed_test_url_invalid",
    "speed_test_group",
    "speed_test_confirm_title",
    "speed_test_confirm_message",
    "speed_test_stage_pending",
    "speed_test_stage_discovery",
    "speed_test_stage_latency",
    "speed_test_stage_download",
    "speed_test_stage_upload",
    "speed_test_stage_complete",
    "speed_test_stage_cancelled",
    "speed_test_stage_error",
    "speed_test_latency_format",
    "speed_test_server_format",
    "speed_test_download_format",
    "speed_test_upload_format",
    "speed_test_rate_mbps",
)

DEPRECATED_RESOURCE_KEYS = {
    "connection_test_tcp_ping",
    "connection_test_icmp_ping",
}

CHECKED_SOURCE_ROOTS = (
    ROOT / "app" / "src" / "main",
    ROOT / "libcore",
)

CHECKED_DOCUMENTS = (
    ROOT / "README.md",
    ROOT / "THR_FILE_RESEARCH.md",
    ROOT / "openspec" / "specs" / "android-application" / "spec.md",
    ROOT / "openspec" / "specs" / "libcore-integration" / "spec.md",
)

FORBIDDEN_TEXT = (
    "http://www.gstatic.com/generate_204",
    ">URL Test</string>",
    "connection_test_tcp_ping",
    "connection_test_icmp_ping",
    "action_connection_tcp_ping",
    "action_connection_icmp_ping",
    "pingTest(",
    "canTCPing(",
    "canICMPing(",
)

FORBIDDEN_PATTERNS = (
    re.compile(r"default_connection_test_concurrent[^\n>]*>\s*5\s*<"),
    re.compile(r"connectionTestConcurrent[^\n]*(?:\{|=)\s*5\b"),
)


def main() -> int:
    failed = False
    string_files = sorted(RES_ROOT.glob("values*/strings.xml"))

    for string_file in string_files:
        root = ET.parse(string_file).getroot()
        strings = root.findall("string")
        names = [element.attrib.get("name", "") for element in strings]
        counts = Counter(names)
        missing = [key for key in REQUIRED_KEYS if counts[key] == 0]
        duplicate = sorted(name for name, count in counts.items() if name and count > 1)
        non_translatable = sorted(
            element.attrib.get("name", "")
            for element in strings
            if element.attrib.get("translatable") == "false"
            and element.attrib.get("name") in REQUIRED_KEYS
        )
        deprecated = sorted(DEPRECATED_RESOURCE_KEYS.intersection(names))

        issues = []
        if missing:
            issues.append(f"missing={','.join(missing)}")
        if duplicate:
            issues.append(f"duplicate={','.join(duplicate)}")
        if non_translatable:
            issues.append(f"non-translatable={','.join(non_translatable)}")
        if deprecated:
            issues.append(f"deprecated={','.join(deprecated)}")

        relative = string_file.relative_to(ROOT)
        if issues:
            failed = True
            print(f"FAIL {relative}: {'; '.join(issues)}")
        else:
            print(f"PASS {relative}")

    if not string_files:
        print("No localized strings.xml files found", file=sys.stderr)
        return 1

    checked_files = list(CHECKED_DOCUMENTS)
    for source_root in CHECKED_SOURCE_ROOTS:
        checked_files.extend(
            path
            for path in source_root.rglob("*")
            if path.is_file() and path.suffix in {".go", ".java", ".kt", ".md", ".xml"}
        )

    for checked_file in checked_files:
        text = checked_file.read_text(encoding="utf-8")
        relative = checked_file.relative_to(ROOT)
        for forbidden in FORBIDDEN_TEXT:
            if forbidden in text:
                failed = True
                print(f"FAIL {relative}: forbidden text={forbidden}")
        for pattern in FORBIDDEN_PATTERNS:
            if pattern.search(text):
                failed = True
                print(f"FAIL {relative}: forbidden pattern={pattern.pattern}")

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
