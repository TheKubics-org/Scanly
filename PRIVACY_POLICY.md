# Privacy Policy for Scanly

**Effective Date:** September 11, 2026  
**Last Updated:** September 11, 2026  
**Developer / Studio:** TheKubics Software Studio ([https://thekubics.space/](https://thekubics.space/))  
**App Website:** [https://scanly.thekubics.space/](https://scanly.thekubics.space/)  
**Contact Email:** [contact@thekubics.space](mailto:contact@thekubics.space)

---

## 1. Introduction & Core Philosophy

Welcome to **Scanly** ("the App"), a document scanning and PDF creation application developed by **TheKubics** ("we", "us", or "our"). 

We believe that your personal and business documents are strictly private. Scanly is designed and engineered from the ground up on an **offline-first, zero-knowledge** architecture:
- **No Developer Tracking:** We do not collect, transmit, store, sell, or broker your personal information, documents, images, or metadata.
- **No Third-Party Advertising:** The App contains zero advertising SDKs, ad networks, or user tracking beacons.
- **No Third-Party Analytics:** We do not track user behavior or device fingerprints with analytics platforms (such as Google Analytics, Firebase Analytics, Mixpanel, or Facebook SDK).
- **Your Data Remains Yours:** All scanned documents, OCR text, and generated PDFs remain strictly on your local device unless you explicitly configure decentralized cloud storage.

---

## 2. Permissions and How They Are Used

Scanly requests the minimum Android system permissions necessary to deliver document scanning and file management features. Each permission is used solely on your local device:

| Permission | Technical Identifier | Purpose & Scope |
|---|---|---|
| **Camera** | `android.permission.CAMERA` | Used exclusively to capture document images and perform real-time quad edge detection via the on-device viewfinder. Image frames are processed in volatile memory on your device and are never sent to external servers. |
| **Photos & Media** | `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | Used strictly when you explicitly select images from your gallery to import into a document, or when picking files for scanning. |
| **Storage (Save/Export)** | `WRITE_EXTERNAL_STORAGE` (Android 9 and below) | Used exclusively to write exported PDF documents and images to your device's public `Download` or `Documents` directory upon user request. |
| **Internet** | `android.permission.INTERNET` | Used **solely** for user-configured, decentralized cloud storage sync (e.g., Telegram Bot API cloud backup, Google Drive). If you do not configure cloud storage, no internet connections are initiated by the application. |
| **Biometrics** | `USE_BIOMETRIC` / `USE_FINGERPRINT` | Used to protect private documents and folders behind device biometric authentication (Fingerprint / Face Unlock / Screen PIN) via Android's native `BiometricPrompt`. Biometric data never leaves the hardware Trusted Execution Environment (TEE) and is never accessible to Scanly. |
| **Notifications** | `POST_NOTIFICATIONS` | Used strictly on Android 13+ to display local foreground service progress notifications when exporting large multi-page PDFs or executing background cloud backups. |

---

## 3. On-Device Machine Learning & Image Processing

- **Document Contour & Edge Detection:** Powered by Google ML Kit Document Detection. All computer vision and edge detection operations are executed 100% locally on your device's CPU/GPU/NPU.
- **Optical Character Recognition (OCR):** Performed entirely on-device. Extracted text is stored in your local encrypted SQLite database and is never shared.
- **Color Filters & Matrix Adjustments:** Document enhancements (Magic Color, B&W, Grayscale, Contrast) are calculated directly on your device's memory using standard Android 2D graphics pipelines.

---

## 4. User-Controlled Cloud Storage (BYOS — Bring Your Own Storage)

Scanly includes an optional cloud storage feature built on a **Bring Your Own Storage (BYOS)** model:
- **Direct User-to-Provider Communication:** If you enable cloud backup (such as Telegram Cloud storage), Scanly communicates directly with the designated API endpoint using your own credentials (e.g., your private Telegram Bot token and chat ID).
- **Zero Intermediary Servers:** We do not operate any intermediary proxy servers, databases, or relays. Your documents are transmitted directly from your device to your personal storage target over TLS/HTTPS encryption.
- **Credential Protection:** Cloud API tokens, keys, and chat IDs are stored in hardware-backed encrypted storage (`EncryptedSharedPreferences` backed by the Android Keystore with AES-256-GCM encryption).

---

## 5. Data Storage, Retention, and Deletion

- **Local Storage:** All document data, thumbnails, page orders, and application settings are stored in Android's sandboxed internal storage (`/data/data/com.docscanner.app/`).
- **User Control & Deletion:** You have complete control over your data. Deleting a document or clearing the trash within Scanly permanently erases the associated files from your device.
- **App Uninstall:** Uninstalling Scanly permanently removes all internal databases, cache files, and encrypted credentials from your device. Exported PDFs saved to public folders (e.g., `Downloads`) will remain under your direct control.

---

## 6. Children's Privacy

Scanly is not directed to children under the age of 13. Because we do not collect, process, or store any personal data whatsoever, we do not knowingly collect personal information from children.

---

## 7. Security Measures

We take the security of your documents seriously:
- **Hardware-Backed Cryptography:** Secrets are secured using Android Keystore keys (MasterKey provider) utilizing AES-256-GCM.
- **Screen Protection:** When privacy mode is enabled, Scanly utilizes `FLAG_SECURE` to prevent system screenshots and app previews in the Android Recent Apps switcher.
- **Scoped Storage & Modern Sandboxing:** The app adheres to modern Android Scoped Storage and backup extraction isolation rules (`data_extraction_rules.xml`).

---

## 8. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect changes in legal requirements or app capabilities. Any updates will be published with a revised "Effective Date" on our official website at [https://scanly.thekubics.space/privacy.html](https://scanly.thekubics.space/privacy.html) and within the GitHub repository.

---

## 9. Contact Us

If you have questions, concerns, or feedback regarding this Privacy Policy or our privacy practices, please contact us:

- **TheKubics Software Studio**
- **Email:** [contact@thekubics.space](mailto:contact@thekubics.space)
- **Website:** [https://scanly.thekubics.space/](https://scanly.thekubics.space/)
