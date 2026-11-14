# PPTX Viewer (Android 10+ / API 29+)

A private, offline Android viewer dedicated to PowerPoint `.pptx` presentations. Slides are read from the local OpenDocument provider and laid out at the presentation's actual aspect ratio, with text, pictures, shapes, and tables positioned from the PPTX package.

## Privacy

- No Internet permission, ads, analytics, or cloud conversion
- Uses Android's Storage Access Framework to open local or provider-backed presentations
- Keeps recent files, favorites, bookmarks, and slide positions on-device

## Build an APK

GitHub Actions runs tests and builds an installable debug APK when changes are pushed to `main`. Download the `MyDocumentViewer-Debug-APK` artifact from the latest successful run.

To build locally with JDK 17 and Android SDK 35 installed:

```bash
gradle testDebugUnitTest assembleDebug
```
