# Text schwärzen 2.9.0-alpha.2

**Pre-release** — the Android app and the desktop version are widely tested, the
macOS app is new and has not yet been used by a second person.

## What this is

A tool that redacts text in images, PDFs and text files — on the device, without
network, without an account. For PDFs every page is rewritten as an image; the text
can no longer be selected or copied.

## Packages

| File | System |
|---|---|
| `Text-schwaerzen-android.apk` | Android 8 and newer |
| `Text-schwaerzen-windows.zip` | Windows 10/11 (with Tesseract) |
| `Text-schwaerzen-linux.tar.gz` | Linux (x86_64) |
| `Text-schwaerzen-macos.zip` | macOS (desktop version) |

The macOS app (Swift) is **not** part of the ZIP — it is installed as a bundle in
`/Applications`.

## New in this version

- **Two steps instead of four buttons at once:** first "How to proceed?" (redact
  automatically · mark it myself), then "What to redact?". The file name is shown.
- **Only the fields that apply are enabled:** blur style, font size and stamp are
  greyed out unless the chosen tool uses them; undo is grey while nothing is drawn.
- **Recent files list:** "Remove from list" (file stays) and "Delete". Only files the
  app created can be deleted (`…-geschwaerzt`, `…-markiert`) — for originals the
  button is greyed out, as in the Android version.
- **Works without PyMuPDF:** images and text files can be redacted even when PyMuPDF
  is missing; it is only needed for PDFs.

## Tested

- Android: nine unit tests run during the build
- Desktop version: build green for Windows, Linux, macOS · check script
  `pruefe_rechner_fassung.py` · counter-check against the previous state fails as expected
- macOS app: self-check of the modes; marking uses the chosen colour; preview and
  result agree for six tools; blur leaves no readable text (16 words → 0)

## Known limits

- **Windows and Linux are untested** — built, but never run on a real machine.
- The macOS app is new: mouse and keyboard have not been used for long yet.
- Cyrillic, Greek, Arabic, Hebrew are not recognised.
- The result is an image PDF: no longer searchable — by design.
- Not in the Play Store; Android asks once for permission to install from this source.

Source and bug reports: <https://github.com/Suilui1/text-schwaerzen>
