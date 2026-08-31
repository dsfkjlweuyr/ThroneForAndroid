from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
source = (
    ROOT / "app/src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt"
).read_text(encoding="utf-8")

method = "override fun onViewCreated(view: View, savedInstanceState: Bundle?)"
assert source.count(method) == 2, source.count(method)

about_content = source.split("class AboutContent : MaterialAboutFragment() {", 1)[1]
assert about_content.count(method) == 1, about_content.count(method)
assert "findViewById<RecyclerView>(R.id.mal_recyclerview)" in about_content
assert "isNestedScrollingEnabled = false" in about_content
assert "overScrollMode = View.OVER_SCROLL_NEVER" in about_content
assert about_content.count("height = ViewGroup.LayoutParams.WRAP_CONTENT") == 2

print("about-fragment-onViewCreated-count=1")
print("about-content-onViewCreated-count=1")
print("scroll-configuration-merged=true")
