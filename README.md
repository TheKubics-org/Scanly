# Scanly — Smart Document Scanner

<div align="center">

<img src="app/src/main/res/drawable/app_logo.jpg" alt="Scanly Logo" width="120" style="border-radius: 28px; box-shadow: 0 12px 36px rgba(0,0,0,0.4);" />

### Paper, digitized properly.
**A focused, offline-first smart document scanner for Android.**

[![TheKubics Project](https://img.shields.io/badge/Parent%20Studio-TheKubics-ECA8D6?style=for-the-badge&logo=appveyor&logoColor=black)](https://thekubics.space/)
[![Website](https://img.shields.io/badge/Official%20Site-scanly.thekubics.space-38BDF8?style=for-the-badge&logo=googlechrome&logoColor=white)](https://scanly.thekubics.space/)
[![Platform](https://img.shields.io/badge/Platform-Android%207%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.4-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

---

[Official Website](https://scanly.thekubics.space/) &bull; [Parent Studio](https://thekubics.space/) &bull; [Capabilities](#capabilities) &bull; [Architecture](#architecture) &bull; [Build Setup](#build--installation)

</div>

---

## Overview

**Scanly** turns physical documents into clean, organized digital files. Engineered under **TheKubics** design philosophy, it focuses on the part that matters: deterministic capture, instant edge detection, perspective correction, and high-fidelity PDF export without marketing noise or forced cloud accounts.

* **Category:** Smart Document Scanner
* **Parent Brand:** [TheKubics Software Studio](https://thekubics.space/)
* **Official Website:** [https://scanly.thekubics.space/](https://scanly.thekubics.space/)
* **Primary Tagline:** *"Scan documents. Cut the clutter."*
* **Architecture:** 100% Offline-first with optional decoupled Cloud Backup.

---

## Capabilities

| # | Feature | Technical Implementation |
|---|---|---|
|* **01** | **Smart Scanning** | Google ML Kit Document Scanner (capture, edge detection, crop). |
| **02** | **Auto Edge Detection** | Provided by ML Kit Document Scanner UI. |
| **03** | **Perspective Correction** | Handled inside the ML Kit scanning flow. |
| **04** | **Image Processing** | ColorMatrix filters: Magic Color, Clean B&W, Grayscale, Contrast stretch. |
| **05** | **Multi-Page Composer** | Full page hierarchy: add, delete, rotate, duplicate, and reorder scans. |
| **06** | **Export & Convert** | PDF / PNG / JPG export plus import of PDF and images. |
| **07** | **Local Document Library** | SQLite database via Room with folder organization and search. |
| **08** | **Biometric Security** | BiometricPrompt integration (fingerprint / face unlock). |
| **09** | **Cloud Storage (Optional)**| WorkManager BYOS sync: Telegram, Cloudflare R2, Google Drive. |

---

## Architecture & Design

Scanly follows strict **Clean Architecture** principles structured into three decoupled layers:

```
com.thekubics.scanly
├── data
│   ├── local
│   │   ├── dao          # Room DAOs (Document, Page, Folder, Cloud, SyncQueue)
│   │   ├── db           # AppDatabase (Room v2 migration & schemas)
│   │   └── entity       # Relational SQLite table entities
│   ├── mapper           # Entity <-> Domain bidirectional mappers
│   ├── repository       # Concrete repository implementations
│   ├── service          # CloudStorageServiceImpl
│   ├── storage          # BYOS providers: Telegram, Cloudflare R2, Google Drive
│   └── vault            # EncryptedSharedPreferences credential vault
├── di                   # Dagger Hilt dependency injection modules
├── domain
│   ├── model            # Immutable pure Kotlin domain models
│   ├── repository       # Repository abstractions / contracts
│   └── service          # Service interfaces (Cloud Storage, Filters, Sync)
├── presentation
│   ├── cloud            # Cloud Library & Storage Telemetry Dashboard
│   ├── common           # Reusable Compose components (Pills, Dialogs)
│   ├── editor           # Document adjustment, filters, & page composer
│   ├── folders          # Folder management & batch move
│   ├── home             # Document grid, recent scans, search
│   ├── navigation       # Type-safe Jetpack Compose navigation graphs
│   ├── scanner          # ML Kit Document Scanner integration
│   ├── search           # Document title / OCR text search
│   ├── settings         # Theme selection, save defaults, cache cleaner
│   ├── theme            # TheKubics dark-first design tokens & typography
│   ├── trash            # Trash management & restore
│   └── viewer           # Page viewer, OCR results, PDF export
├── service
│   ├── filter           # ImageFilterService (ColorMatrix & Bitmap transforms)
│   ├── pdf              # PdfGeneratorService (A4 / Letter generator)
│   └── sync             # CloudSyncWorker & periodic WorkManager scheduler
└── util                 # NetworkMonitor, FileUtils, BitmapExtensions
```

---

## Tech Stack

Scanly is built with tools that ship:

* **Language:** Kotlin 2.4.10
* **UI Framework:** Jetpack Compose with Material Design 3
* **Computer Vision:** Google ML Kit Document Detection
* **Dependency Injection:** Dagger Hilt 2.51.1 (`hilt-android`, `hilt-work`)
* **Local Persistence:** AndroidX Room 2.6.1 SQLite Database
* **Background Tasks:** AndroidX WorkManager 2.10.0
* **Image Pipeline:** Coil 3 for Compose
* **Camera / Capture:** Google ML Kit Document Scanner
* **Security:** AndroidX Biometric 1.1.0

---

## Build & Installation

### Prerequisites
* Android Studio Ladybug or newer
* JDK 17 or JDK 21 (configured as `JAVA_HOME`)
* Android SDK 37 (compileSdk: 37, minSdk: 24, targetSdk: 35)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/TheKubics-org/Scanly.git
cd Scanly

# Build the Debug APK
./gradlew assembleDebug

# Output APK path
app/build/outputs/apk/debug/app-debug.apk
```

---

## Studio & Credits

Scanly is an official product built and maintained under **TheKubics**.

* **Studio:** [TheKubics](https://thekubics.space/)
* **Email:** [thekubics.dev@gmail.com](mailto:thekubics.dev@gmail.com)
* **Organization:** [github.com/TheKubics-org](https://github.com/TheKubics-org)

---

<div align="center">

&copy; 2026 **TheKubics**. All rights reserved.  
*Software, cut precisely. Built to ship.*

</div>
