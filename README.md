<div align="center">
<img alt="FacePlugin" src="https://avatars.githubusercontent.com/u/160751046?s=200&v=4" width="200"/>
</div>

#### 🌐 Company Site - [Here](https://faceplugin.com)
#### 🤗 Hugging Face - [Here](https://huggingface.co/FacePlugin-Ltd)
#### 🛟 Help Center - [Here](https://doc.faceplugin.com)
#### 🐳 Docker Hub - [Here](https://hub.docker.com/u/faceplugin)

# FacePlugin ID Document Recognition SDK — Android (Fully On-Premise)

> Drop `documentreadersdk.aar` into `libdocsdk/` → run on a **physical** phone (~10 min after Android Studio is ready).
> Jump: [Quick Start](#quick-start) · [Get the AAR](#get-the-aar-libdocsdk) · [Run the demo](#run-the-demo) · [License](#sdk-license) · [Integrate](#setup-on-your-own-app)

## Quick Start

Use this for the **sample app** (check each box in order).

- [ ] Clone [ID-Document-Recognition-Android](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Android)
- [ ] Android Studio with **JDK 17** (`compileOptions` / `jvmTarget` are 17)
- [ ] Download `documentreadersdk.aar` — [Get the AAR](#get-the-aar-libdocsdk)
- [ ] Place it at `libdocsdk/documentreadersdk.aar` (not a nested folder)
- [ ] Open **this** folder in Android Studio
- [ ] Connect a **physical arm64** phone (emulator is not recommended)
- [ ] Keep `applicationId` `com.faceplugin.documentreader` for the demo license (until **12 Aug 2027**)
- [ ] Run the app and grant camera when asked
- [ ] Home status bar shows **Ready** → Camera / Gallery / About

> Own app? → [Setup on your own app](#setup-on-your-own-app). Full integration: [https://doc.faceplugin.com](https://doc.faceplugin.com)

## Introduction

FacePlugin **ID Document Recognition SDK for Android** is a fully on-device identity verification engine for ID cards, passports, and driver licenses. It delivers OCR, MRZ reading, barcode and QR extraction, live camera locate overlay, gallery capture (front required, optional back), document classification, image quality checks, extracted portraits and signatures, and authenticity / document liveness (security) checks.

This repository is the standalone **Android demo**. The runtime is `libdocsdk/documentreadersdk.aar` (download from Google Drive). No other FacePlugin repository is required.

All processing stays on the phone. **No** biometric data is sent to FacePlugin cloud — built for KYC, eKYC, banking, and mobile onboarding that must stay private.

Native binaries are **not** on GitHub. Download the AAR from the Drive link below.

### Main Functionalities

| Feature | Supported |
| ------- | --------- |
| ID Card, Passport, and Driver License recognition | ✓ |
| MRZ, Barcode, QR, and OCR data extraction | ✓ |
| Live camera locate overlay + Capture | ✓ |
| Gallery (front required, back optional) | ✓ |
| Result (fields, Security, images, JSON) | ✓ |
| Authenticity / Security (document liveness) | ✓ |
| About | ✓ |

### Product List

| Platform | Repository |
|----------|------------|
| **Android** | **[ID-Document-Recognition-Android](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Android)** (**this repo**) |
| iOS | [ID-Document-Recognition-iOS](https://github.com/Faceplugin-ltd/ID-Document-Recognition-iOS) |
| Windows | [ID-Document-Recognition-Windows](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Windows) |
| Linux / Docker | [ID-Document-Recognition-Docker](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Docker) |
| React Native | [ID-Document-Recognition-React-Native](https://github.com/Faceplugin-ltd/ID-Document-Recognition-React-Native) |
| Flutter | [ID-Document-Recognition-Flutter](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Flutter) |
| Ionic Capacitor | [ID-Document-Recognition-Ionic-Capacitor](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Ionic-Capacitor) |
| Ionic Cordova | [ID-Document-Recognition-Ionic-Cordova](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Ionic-Cordova) |
| Linux / Docker (Liveness-Only) | [ID-Document-Liveness-Detection-Docker](https://github.com/Faceplugin-ltd/ID-Document-Liveness-Detection-Docker) |


---

## Before you start

| Step | What you need |
| ---- | ------------- |
| 1 | Android Studio + **JDK 17** + a **physical device** (emulator is not recommended) |
| 2 | `documentreadersdk.aar` in `./libdocsdk/` — [Get the AAR](#get-the-aar-libdocsdk) |
| 3 | Demo license is already in the repo (`LICENSE_KEY` for `com.faceplugin.documentreader`, valid until **12 August 2027**). Request a new key only if you change `applicationId` — [SDK License](#sdk-license) |

Camera and Gallery unlock when the status bar shows **Ready**.

### System requirements

`documentreadersdk.aar` includes native libs for **four** ABIs. The sample app filters to `arm64-v8a` so the debug APK stays smaller — that is not an SDK limit.

| Item | Minimum | Recommended |
| ---- | ------- | ----------- |
| Android Studio | AGP **8.5.2**, Gradle **8.7**, JDK **17** | Same |
| Android | API **24** (7.0); `compileSdk` / `targetSdk` **34** | API **29** (10) or newer |
| ABI | `arm64-v8a` (demo); AAR also has `armeabi-v7a`, `x86_64`, `x86` | `arm64-v8a` phones |
| RAM | 4 GB | 6 GB or more |
| CPU | 4+ cores | Mid-range SoC from ~2019 or newer |
| Camera | Rear camera | 1080p, autofocus |
| Device | Physical device | Same; emulator is not for camera |

---

## Get the AAR (`libdocsdk`)

`libdocsdk/documentreadersdk.aar` is empty on GitHub because the binary is too large. Gradle fails fast if the file is missing.

### Where to download

**[DocumentReader-Android-App runtime (Google Drive)](https://drive.google.com/drive/folders/1nDSfvj0WtC1lZgzwFd7471ECVtk-nuYH)**

### How to place it

```bash
git clone https://github.com/Faceplugin-ltd/ID-Document-Recognition-Android.git
cd ID-Document-Recognition-Android
```

1. Download `documentreadersdk.aar` from the Drive folder.
2. Put it **here** (not in a nested folder):

```text
ID-Document-Recognition-Android/
└── libdocsdk/
    └── documentreadersdk.aar
```

---

## Run the demo

1. Open **this** folder in Android Studio.
2. Select a physical device and Run. The demo already has a valid `LICENSE_KEY` for `com.faceplugin.documentreader`.
3. Wait for the **status bar** at the bottom of Home: `Checking license…` → `Loading database…` → **Ready**. Camera and Gallery stay disabled until then.

Home tiles are one row under the title: **Camera** | **Gallery** | **About**.

- **Camera** — live document locate overlay; tap **Capture** when the score is ready (≥ 50%) to run on-device OCR, MRZ, barcode, and authenticity checks.
- **Gallery** — pick Front (required) and Back (optional), then Recognize for two-sided ID processing.
- **Result** — tabs **Result** / **Security** / **Images** / **Raw JSON** for fields, liveness, crops, and the full JSON response.

### Screenshots

<p align="center">
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/document-reader/mobile/home.png" width="240" alt="FacePlugin Document Reader — Home with Camera, Gallery, About and Recognition + Liveness"/>
&nbsp;
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/document-reader/mobile/camera.png" width="240" alt="FacePlugin Document Reader — live camera overlay and Capture for ID scanning"/>
&nbsp;
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/document-reader/mobile/gallery.png" width="240" alt="FacePlugin Document Reader — Gallery front and optional back, then Recognize"/>
</p>

<p align="center">
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/document-reader/mobile/result.png" width="240" alt="FacePlugin Document Reader — Result tab with OCR, MRZ, and barcode fields"/>
&nbsp;
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/document-reader/mobile/security.png" width="240" alt="FacePlugin Document Reader — Liveness tab with authenticity and document liveness"/>
&nbsp;
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/document-reader/mobile/images.png" width="240" alt="FacePlugin Document Reader — Images tab with portrait, signature, and document crops"/>
</p>

<p align="center">
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/document-reader/mobile/raw.png" width="240" alt="FacePlugin Document Reader — Raw JSON recognize response for integration"/>
&nbsp;
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/document-reader/mobile/about.png" width="240" alt="FacePlugin Document Reader — About with on-device Recognition + Liveness license"/>
</p>

---

## SDK License

Licenses are **offline** and bound to your `applicationId`.

The sample app already includes a valid key for `com.faceplugin.documentreader` (until **12 August 2027**). You only need a new key if you use a different `applicationId`.

### How to get a license

The code below shows how to use the license:

[https://github.com/Faceplugin-ltd/ID-Document-Recognition-Android/blob/f33138b5fb7ca69529f5ca3bdd2cf3dede084b79/app/src/main/java/com/faceplugin/documentreader/MainActivity.kt#L31-L33](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Android/blob/f33138b5fb7ca69529f5ca3bdd2cf3dede084b79/app/src/main/java/com/faceplugin/documentreader/MainActivity.kt#L31-L33)

[https://github.com/Faceplugin-ltd/ID-Document-Recognition-Android/blob/f33138b5fb7ca69529f5ca3bdd2cf3dede084b79/app/src/main/java/com/faceplugin/documentreader/MainActivity.kt#L76-L77](https://github.com/Faceplugin-ltd/ID-Document-Recognition-Android/blob/f33138b5fb7ca69529f5ca3bdd2cf3dede084b79/app/src/main/java/com/faceplugin/documentreader/MainActivity.kt#L76-L77)

Please [contact us](#contact) to get a license for **your own app**.

### License capabilities (Recognition + Liveness)

After activation, `getLicenseStatus` reports what the key unlocks. Home shows the same summary on the status bar (for example **Ready · Recognition + Liveness**). About shows **License: …**.

| Capability | Meaning |
| ---------- | ------- |
| **Recognition** | OCR, MRZ, barcode/QR, and document type classification |
| **Liveness** (authenticity) | Document authenticity: physical document, security patterns, photo origin, barcode format |

Typical labels:

- **Recognition + Liveness** — full identity verification (Result + Liveness tabs)
- **Recognition** — OCR, MRZ, and barcode only; Security stays empty / not checked
- **Liveness** — authenticity / document liveness only; OCR/MRZ/barcode stays empty / not checked
- **Not licensed** — until you activate

---

## Setup on your own app

You need `libdocsdk/` (the AAR) and `com.faceplugin.documentreadersdk.DocumentReaderSDK`. You do **not** need this demo’s `MainActivity` / `CameraActivity`.

1. Copy the `libdocsdk` folder into your project root and put `documentreadersdk.aar` inside it ([Drive](#get-the-aar-libdocsdk)).
2. `settings.gradle`: `include ':libdocsdk'`
3. `app/build.gradle`: `minSdk 24`, `implementation project(':libdocsdk')`. Optional: `abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'` (the AAR ships all four).
4. Request a license for **your** `applicationId`, not the demo’s.
5. Call `init` and all process methods **off the UI thread**.

Full wiring, permissions, and result JSON: [https://doc.faceplugin.com](https://doc.faceplugin.com)

---

## About SDK

```kotlin
import com.faceplugin.documentreadersdk.DocumentReaderSDK
```

Call order (background thread): `getMachineCode` → `setActivation` → `init` → `getLicenseStatus` → `recognize` / `locateDocument`. `0` = `SDK_SUCCESS`. Process methods return JSON.

```kotlin
Thread {
    var ret = DocumentReaderSDK.setActivation(context, "FP1.…")
    if (ret == DocumentReaderSDK.SDK_SUCCESS) {
        ret = DocumentReaderSDK.init(context)
    }
}.start()
```

| Method | Role |
| ------ | ---- |
| `getMachineCode` / `setActivation` / `init` / `deinit` | License + engine lifecycle |
| `getLicenseStatus` | Recognition / Liveness flags + label |
| `locateDocument(bitmap)` | Live corners + score (overlay, no OCR) |
| `recognize(bitmap)` | OCR / MRZ / barcode from one image |
| `recognize(front, back, authenticity)` | Front + optional back; authenticity `"normal"` or `"none"` |
| `documentRecognition(front, back?)` | OCR / MRZ / barcode only |
| `documentLiveness(front, back?)` | Authenticity / security only |

| Code | Constant | Status |
| ---- | -------- | ------ |
| 0 | `SDK_SUCCESS` | Activate / init OK |
| 1 | `SDK_LICENSE_INVALID` | Invalid license |
| 2 | `SDK_LICENSE_EXPIRED` | Expired license |
| 3 | `SDK_NOT_ACTIVATED` | Not activated |
| 4 | `SDK_INIT_FAILED` | Init failed |

---

## Contact

<div align="left">
<a target="_blank" href="mailto:info@faceplugin.com"><img src="https://img.shields.io/badge/email-info@faceplugin.com-blue.svg?logo=gmail" alt="faceplugin.com"></a>&emsp;
<a target="_blank" href="https://wa.me/+14692784822"><img src="https://img.shields.io/badge/whatsapp-faceplugin-blue.svg?logo=whatsapp" alt="faceplugin.com"></a>
</div>
