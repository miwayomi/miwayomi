import sys
import shutil
import zipfile

OVERRIDDEN = [
    "android/app/Application",
    "android/content/Context",
    "android/content/ContextWrapper",
    "android/content/ContextThemeWrapper",
    "android/content/Intent",
    "android/content/SharedPreferences",
    "android/content/pm/PackageManager",
    "android/content/pm/PackageInfo",
    "android/content/pm/ApplicationInfo",
    "android/content/pm/Signature",
    "android/content/pm/ResolveInfo",
    "android/content/res/Resources",
    "android/graphics/Bitmap",
    "android/graphics/BitmapFactory",
    "android/graphics/Rect",
    "android/graphics/drawable/Drawable",
    "android/net/Uri",
    "android/os/Build",
    "android/os/Bundle",
    "android/os/Handler",
    "android/os/Looper",
    "android/os/Message",
    "android/os/SystemClock",
    "android/text/Html",
    "android/text/format/Formatter",
    "android/util/AttributeSet",
    "android/util/Base64",
    "android/util/DisplayMetrics",
    "android/util/Log",
    "android/view/View",
    "android/view/ViewGroup",
    "android/webkit/WebChromeClient",
    "android/webkit/WebSettings",
    "android/webkit/WebView",
    "android/webkit/WebViewClient",
    "android/webkit/ValueCallback",
    "android/webkit/CookieManager",
    "android/widget/AbsoluteLayout",
]

DROPPED_PREFIXES = ("java/", "javax/", "org/", "junit/")

def should_drop(name: str) -> bool:
    if name.endswith(".class"):
        base = name[: -len(".class")]
    else:
        base = name
    if base.startswith(DROPPED_PREFIXES):
        return True
    for c in OVERRIDDEN:
        if base == c or base.startswith(c + "$"):
            return True
    return False

def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "android.jar"

    zin = zipfile.ZipFile(src)
    removed = []
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if should_drop(item.filename):
                removed.append(item.filename)
                continue
            zout.writestr(item, zin.read(item.filename))
    zin.close()

    print(f"total entradas originales: {len(removed) + len(zipfile.ZipFile(out).namelist())}")
    print(f"removidas: {len(removed)}")
    if "--list" in sys.argv:
        for r in removed:
            print(" -", r)
    print(f"escrito: {out}")

if __name__ == "__main__":
    main()
