# JPDB Plugin for Dokuen Japanese Reader

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-Compile_SDK_37-green.svg?style=flat&logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat)](LICENSE)
[![Ko-Fi](https://img.shields.io/badge/Ko--fi-F16061?logo=ko-fi&logoColor=white)](https://ko-fi.com/dokuenreader)

An online dictionary plugin for **Dokuen Japanese Reader** that integrates **JPDB** dictionary
lookups and spaced repetition system (SRS) deck updates.

[**Download on Google Play**](https://play.google.com/store/apps/details?id=io.github.dokuendev.dokuen.plugins.dictionary.jpdb)

<p><kbd><img src="https://github.com/user-attachments/assets/a5aa1c6b-8ff9-4a8e-b6e9-a179ea447893" width="250"></kbd>&nbsp;&nbsp;&nbsp;&nbsp;<kbd><img src="https://github.com/user-attachments/assets/822a11f9-ecbf-4d96-ab9f-53f421236ce5" width="250"></kbd>&nbsp;&nbsp;&nbsp;&nbsp;<kbd><img src="https://github.com/user-attachments/assets/dfbec5e2-1e97-4908-8449-8f4183013765" width="250"></kbd></p>

---

## Overview

This project is a lightweight, online-only dictionary plugin conforming to the
[Dokuen Dictionary Plugin SDK](https://github.com/dokuen-dev/dokuen-reader/tree/main/sdk/dictionary)
spec.

When active, it queries the public JPDB API for definitions and embeds links for deck management
within the dictionary results.

## Features

* **Online Parse Integration:** Sends dictionary lookup queries to the JPDB `/parse` API.
* **SRS Deck Updates:** Add words to your JPDB deck directly from the dictionary lookup using
  actionable inline links.
* **Clean Formatting:** Renders parts of speech, frequencies, meanings, and card action links using
  rich text spans and list blocks.

## Getting Started

> [!IMPORTANT]
> This application acts as a plugin. You must have **Dokuen Japanese Reader** installed to perform
> lookups.

### Configuration Steps

1. Download and install this plugin. If the device does not yet have Dokuen Japanese Reader
   installed, install it too.
2. Obtain your personal API key from your JPDB account settings.
3. Open **Dokuen Japanese Reader**, navigate to **Settings → Plugin Manager → Dictionary Plugins**,
   find JPDB in the list and tap **Configure**.
4. Paste your API key into the configuration field and tap **Save**.
5. Exit the Configuration page, then back in the Plugin Manager select the JPDB plugin to make it
   active.

## Development & Build

### Prerequisites

* **Android Studio** (Koala or newer recommended)
* **Android SDK** (Compile SDK 37, Target SDK 36, Min SDK 29 / Android 10)
* **JDK 17**

### Gradle Commands

Build the debug APK:

```bash
./gradlew assembleDebug
```

Run the unit tests:

```bash
./gradlew test
```

## Support

If you use this project and it helps you out, please consider supporting its development!

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/dokuenreader)

## License

This project is licensed under the Apache-2.0 License.
