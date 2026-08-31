from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[2]


def git_lines(*args: str) -> list[str]:
    result = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return [line for line in result.stdout.splitlines() if line]


tracked_licenses = [
    path
    for path in git_lines("ls-files")
    if Path(path).name.casefold().startswith("license")
]
assert tracked_licenses == ["LICENSE"], tracked_licenses

gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
assert 'rootProject.layout.projectDirectory.file("LICENSE")' in gradle
assert "assets.srcDir(generatedLicenseAssets)" in gradle
assert "dependsOn(generateRootLicenseAsset)" in gradle

about = (
    ROOT
    / "app/src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt"
).read_text(encoding="utf-8")
assert 'assets.open("LICENSE")' in about

tracked_text = "\n".join(
    (ROOT / path).read_text(encoding="utf-8", errors="ignore")
    for path in git_lines("ls-files")
    if (ROOT / path).is_file()
)
assert "app/src/main/assets/LICENSE" not in tracked_text
assert "libcore/LICENSE" not in tracked_text

print("tracked-license=LICENSE")
print("old-path-references=none")
print("root-license-to-generated-asset=passed")
