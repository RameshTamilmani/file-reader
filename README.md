# My Document Viewer (Android 10+ / API 29+)

A private, 100% offline, ad-free native Android document viewer built with Kotlin, Jetpack Compose (Material 3), Hilt, Room, AndroidX PDF, and Apache POI.

## Privacy & Permissions
- **Zero `INTERNET` permission** in `AndroidManifest.xml`
- Zero ads, analytics, crash-reporting SDKs, or cloud conversion APIs
- Uses Android **Storage Access Framework (SAF)** (`OpenDocument` / `OpenDocumentTree`) instead of `MANAGE_EXTERNAL_STORAGE`
- Stores recents, favorites, bookmarks, and reading positions locally in Room (`my_document_viewer.db`)

## How to Build an Installable APK Without Android Studio

### Option 1: GitHub Actions (Recommended — Zero Local Tools Required)
1. Create a private GitHub repository and upload this `android-project` folder.
2. GitHub automatically runs `.github/workflows/build-apk.yml`.
3. Open the **Actions** tab -> select the latest workflow run -> download the **MyDocumentViewer-Debug-APK** artifact.
4. Transfer `app-debug.apk` to your Android 10+ phone and tap to install.

### Option 2: Build via Command Line (JDK 17 + Android Command-Line Tools)
1. Install JDK 17 and Android SDK Command-Line Tools (`platform-tools`, `platforms;android-35`, `build-tools;35.0.0`).
2. Set `ANDROID_HOME` and run:
   ```bash
   gradle assembleDebug
   ```
3. Install onto a USB-connected phone with USB Debugging enabled:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Library Audit (Free, Offline, Open-Source)
| Library | License | Role | Paid / Cloud Alternatives Avoided |
|---|---|---|---|
| `androidx.pdf:pdf-viewer-fragment` | Apache-2.0 | Local PDF rendering & selection | PSPDFKit / Nutrient, Apryse, Foxit SDK |
| `org.apache.poi` (`poi`, `poi-ooxml`, `poi-scratchpad`) | Apache-2.0 | Offline DOC/DOCX parsing | Aspose, CloudConvert, Google Docs Viewer |
| Android `XmlPullParser` + `ZipInputStream` | Android platform | Offline XLSX sheets, PPTX text, tables, notes, and embedded image extraction; text-only fallbacks for legacy XLS/PPT | Cloud conversion services |
| `androidx.room` + `datastore` | Apache-2.0 | On-device recents, bookmarks, preferences | Firebase Firestore / Cloud Sync |
| `io.coil-kt:coil-compose` + `coil-gif` | Apache-2.0 | Local image & animated GIF rendering | Glide/Remote CDN loaders |
| `com.google.dagger:hilt-android` | Apache-2.0 | Compile-time DI | — |

## Future Roadmap
1. **Local Format Conversion**: Export DOCX/MD/CSV to PDF locally using Android `PdfDocument` + `PrintManager`.
2. **Inline Editing & Annotations**: PDF ink/highlight annotations and Markdown/CSV quick edits saved via SAF `OpenOutputStream`.
3. **Offline OCR**: On-device text recognition for scanned PDFs/images using bundled Tesseract (`tess-two`) or offline ML Kit Text Recognition.
4. **Document Scanning**: Camera perspective crop + contrast cleanup using local OpenCV / CameraX.
5. **Optional User-Initiated Cloud Import**: Explicit SAF DocumentsProvider integration (e.g., user-chosen Drive/Nextcloud provider) without adding background sync or telemetry.
