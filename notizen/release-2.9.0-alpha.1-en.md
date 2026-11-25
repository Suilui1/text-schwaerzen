# Text schwärzen 2.9.0-alpha.1

**Pre-release** — the Android app and the desktop version are largely verified;
the macOS app is new and has not been tested by a second person yet.

Text redaction for images, PDFs and text files — on device, offline, no account.
For PDFs every page is re-rendered as an image; the text can no longer be selected
or copied.

## Packages

| File | System |
|---|---|
| `Text-schwaerzen-android.apk` | Android 8+ |
| `Text-schwaerzen-windows.zip` | Windows 10/11 (includes Tesseract) |
| `Text-schwaerzen-linux.tar.gz` | Linux (x86_64) |
| `Text-schwaerzen-macos.zip` | macOS (Tk version) |

## Known limits

- Windows and Linux are untested — built, but never run on a real machine.
- Cyrillic, Greek, Arabic and Hebrew are not recognised.
- The result is an image PDF: not searchable (by design).
