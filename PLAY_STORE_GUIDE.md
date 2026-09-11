# Google Play Store Submission Guide for Scanly

This guide provides the complete, copy-paste ready metadata, policy declarations, graphic asset specifications, and step-by-step instructions required to publish **Scanly** to the Google Play Console.

---

## 1. App Identity & Store Listing

### App Details
* **App Name (Max 30 chars):** `Scanly: PDF Document Scanner`
* **Short Description (Max 80 chars):** `Offline smart doc scanner: auto edge crop, OCR, PDF maker & encrypted backup.`
* **Category:** `Productivity` (or `Business`)
* **Tags:** `Document scanner`, `PDF scanner`, `OCR`, `Scanner`, `PDF creator`

### Full Description (Markdown/Text for Play Console):
```markdown
Scanly turns physical documents, receipts, invoices, whiteboards, and notes into crisp, high-definition PDF files in seconds. Engineered under the philosophy of simplicity and privacy, Scanly provides everything you need to digitize paper without subscription paywalls, intrusive ads, or forced cloud accounts.

Key Features:

📸 SMART CAMERA SCANNER
• Instant optical capture with dynamic viewfinder
• Real-time automatic edge and quad contour detection powered by Google ML Kit
• Smart perspective correction and planar rectification for angled shots
• Automatic flashlight toggle for low-light scanning

🎨 PROFESSIONAL DOCUMENT ENHANCEMENT
• Magic Color: Intelligently boosts text contrast while balancing backgrounds
• Clean B&W: Removes shadows and delivers crisp monochrome documents for printing
• Grayscale: Smooth continuous tones for certificates and forms
• Manual adjustments: Precise rotation, crop adjustments, and contrast control

📑 MULTI-PAGE DOCUMENT COMPOSER
• Combine unlimited scans into clean multi-page documents
• Easily add, reorder, duplicate, or delete pages
• Flexible ISO standard export: Generates lossless A4 and US Letter PDFs
• Custom document naming and timestamped exports

🔒 PRIVATE & OFFLINE-FIRST
• 100% Offline functionality: Scanning, image processing, OCR, and PDF generation happen directly on your device
• Zero Developer Telemetry: No user tracking, no behavioral analytics, no ad SDKs
• Hardware-Backed Security: Encrypted vault backed by Android Keystore (AES-256-GCM)
• Biometric App Lock: Protect sensitive documents with Fingerprint or Face Unlock
• Anti-Snooping Protection: Optional FLAG_SECURE window masking hides app previews from recent tasks

☁️ DECENTRALIZED CLOUD BACKUP (BYOS)
• Bring Your Own Storage: Backup your scans directly to your personal Telegram channel or cloud drive
• Direct peer-to-cloud: No intermediary developer servers or third-party relays
• Granular sync control: Auto-upload on save with metering options

Experience paper, digitized properly. Download Scanly today.
```

---

## 2. Store Settings & Contact Information

* **Developer Email:** `contact@thekubics.space`
* **Official Website:** `https://scanly.thekubics.space/`
* **Privacy Policy URL:** `https://scanly.thekubics.space/privacy.html`
  *(Alternative fallback: `https://raw.githubusercontent.com/<username>/docscanner_android/main/PRIVACY_POLICY.md`)*
* **Developer / Studio Name:** `TheKubics`

---

## 3. Graphic Asset Requirements

| Asset | Dimensions | Format | Requirement | Notes |
|---|---|---|---|---|
| **App Icon** | 512 x 512 px | 32-bit PNG | Mandatory | Max 1MB. Flat square with rounded corners applied automatically by Google Play. |
| **Feature Graphic** | 1024 x 500 px | JPEG or 24-bit PNG | Mandatory | Max 15MB. No transparency. Promotional banner shown on Play Store listing. |
| **Phone Screenshots** | Min 320px, Max 3840px (16:9 or 9:16) | JPEG or 24-bit PNG | Mandatory (Min 2, Max 8) | Recommended: 1080 x 2400 px capturing Scanner, Editor, Library, and PDF Export. |
| **7-inch Tablet Screenshots** | Min 320px, Max 3840px | JPEG or 24-bit PNG | Optional | Recommended if targeting tablet users. |
| **10-inch Tablet Screenshots** | Min 1080px, Max 7680px | JPEG or 24-bit PNG | Optional | Recommended if targeting Chromebooks / Tablets. |

---

## 4. Play Console Policy & Declarations

When completing the **App Content** questionnaires in Google Play Console:

### 1. Privacy Policy
* Provide: `https://scanly.thekubics.space/privacy.html`

### 2. Ads Declaration
* Select: **"No, my app does not contain ads"**

### 3. App Access
* Select: **"All functionality is available without special access"**

### 4. Content Ratings (IARC Questionnaire)
* Category: **Utility, Productivity, Communication, or Other**
* Violence, Sex, Language, Controlled Substances: **No to all**
* Resulting rating: **PEGI 3 / ESRB Everyone**

### 5. Target Audience & Content
* Target Age Groups: **18 and over** (or **13-15, 16-17, 18+**)
* Could your app appeal to children? **No**

### 6. Data Safety Questionnaire (Critical)
* **Does your app collect or share any user data?**
  * Select: **No**
  * *(Scanly processes all documents, OCR, and filters locally on the device. Even the optional Telegram cloud sync communicates directly between the user's phone and their private Telegram bot without routing through any developer server or collecting any personal information).*
* Google Play will display to users:
  * **"No data shared with third parties"**
  * **"No data collected"**
  * **"Data is encrypted in transit"** (via HTTPS for optional cloud sync)

### 7. Sensitive Permissions Declaration
* **Camera (`android.permission.CAMERA`):**
  * Justification: *Core feature — capture physical document pages and detect document boundaries in real time.*
* **Photos & Media (`READ_MEDIA_IMAGES`):**
  * Justification: *Allows users to import existing photos or saved documents from their device gallery into Scanly for PDF conversion and editing.*

---

## 5. Release & Upload Instructions

1. **Log in** to [Google Play Console](https://play.google.com/console).
2. Click **Create App**:
   * App name: `Scanly: PDF Document Scanner`
   * Default language: `English (United States)`
   * App or game: `App`
   * Free or paid: `Free`
   * Accept declarations and click **Create app**.
3. Complete the **Set up your app** tasks on the Dashboard (Privacy policy, Content rating, Data safety, Store listing).
4. Navigate to **Testing** -> **Internal testing** (or **Production** -> **Releases**):
   * Click **Create new release**.
   * Drag and drop the generated `Scanly-v1.1.2-release.aab` bundle.
   * Verify Release Name: `1.1.2 (4)`
   * Paste the release notes into the **Release notes** field.
   * Click **Next** -> **Save and review release**.
5. When all dashboard checks show green checkmarks, click **Start rollout to Production**!
