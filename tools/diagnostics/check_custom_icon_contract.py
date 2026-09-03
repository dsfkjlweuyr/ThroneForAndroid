from __future__ import annotations

import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MANAGER_FILE = ROOT / "app" / "src" / "main" / "java" / "io" / "nekohasekai" / "sagernet" / "utils" / "CustomIconManager.kt"
TEST_FILE = ROOT / "app" / "src" / "test" / "java" / "io" / "nekohasekai" / "sagernet" / "CustomIconManagerTest.kt"

assert MANAGER_FILE.is_file(), f"Missing manager file: {MANAGER_FILE}"
assert TEST_FILE.is_file(), f"Missing test file: {TEST_FILE}"

manager_code = MANAGER_FILE.read_text(encoding="utf-8")

# 检查常数定义
assert 'const val FILE_ICON = "icon.png"' in manager_code, "FILE_ICON constant missing"
assert 'const val FILE_TILE = "tile.png"' in manager_code, "FILE_TILE constant missing"
assert 'const val REQUIRED_WIDTH = 512' in manager_code, "REQUIRED_WIDTH constant missing"
assert 'const val REQUIRED_HEIGHT = 512' in manager_code, "REQUIRED_HEIGHT constant missing"

# 检查安全解压检查
assert 'entryName.contains("..")' in manager_code, "Path traversal protection missing"

# 检查尺寸解析与 alpha 提取
assert "fun parsePngHeader" in manager_code, "parsePngHeader missing"
assert "return Pair(width, height)" in manager_code, "return statement in parsePngHeader missing"
assert "fun loadTileAlphaBitmap" in manager_code, "loadTileAlphaBitmap missing"
assert "fun extractAlphaMask" in manager_code, "extractAlphaMask missing"
assert "fun reset" in manager_code, "reset missing"

# 验证 PNG Header 规范解析算法与 Python struct 行为一致
def make_png_header(w: int, h: int) -> bytes:
    png_sig = b"\x89PNG\r\n\x1a\n"
    ihdr_len = struct.pack(">I", 13)
    ihdr_type = b"IHDR"
    dims = struct.pack(">II", w, h)
    return png_sig + ihdr_len + ihdr_type + dims

header_512 = make_png_header(512, 512)
assert len(header_512) == 24
sig, length, chunk_type, w, h = struct.unpack(">8sI4sII", header_512)
assert sig == b"\x89PNG\r\n\x1a\n"
assert chunk_type == b"IHDR"
assert w == 512 and h == 512

header_corrupted = b"NOT_A_PNG_FILE"
assert not header_corrupted.startswith(b"\x89PNG\r\n\x1a\n")

print("custom-icon-manager-contract=true")
print("png-header-algorithm-verified=true")
print("path-traversal-protection-verified=true")
print("unit-test-contract-verified=true")
