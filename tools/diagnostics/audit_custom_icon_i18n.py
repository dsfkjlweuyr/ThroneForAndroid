from __future__ import annotations

import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES_ROOT = ROOT / "app" / "src" / "main" / "res"

REQUIRED_CUSTOM_ICON_KEYS = (
    "custom_icon",
    "custom_icon_import",
    "custom_icon_reset",
    "custom_icon_preview_shortcut",
    "custom_icon_shortcut_subtitle",
    "custom_icon_preview_tile",
    "custom_icon_apply_pack",
    "custom_icon_apply_success",
    "custom_icon_pin_shortcut_not_supported",
    "custom_icon_no_pack_to_apply",
    "custom_icon_tile_state_active",
    "custom_icon_tile_state_inactive",
    "custom_icon_import_success",
    "custom_icon_reset_success",
    "custom_icon_reset_confirm",
    "custom_icon_error_missing",
    "custom_icon_error_dimension",
    "custom_icon_error_not_png",
    "custom_icon_error_security",
    "custom_icon_requirement_tip",
)

def main() -> int:
    string_files = sorted(RES_ROOT.glob("values*/strings.xml"))
    print(f"Total string files: {len(string_files)}")
    missing_by_file = {}
    for string_file in string_files:
        rel = string_file.relative_to(ROOT)
        root = ET.parse(string_file).getroot()
        strings = root.findall("string")
        names = [el.attrib.get("name", "") for el in strings]
        counts = Counter(names)
        missing = [k for k in REQUIRED_CUSTOM_ICON_KEYS if counts[k] == 0]
        if missing:
            missing_by_file[str(rel)] = missing

    print(f"Files with missing keys: {len(missing_by_file)}")
    for f, missing in missing_by_file.items():
        print(f"  {f}: {len(missing)} missing")
    return len(missing_by_file)

if __name__ == "__main__":
    raise SystemExit(main())
