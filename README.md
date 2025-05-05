# StreetMusicianPlayer

This Android app is designed for street musicians to play backing tracks with foot control, customizable volume, and pitch settings.

## 🛠️ Included Libraries

### 🔊 SoundTouch
Used for real-time pitch and tempo processing of audio files.  
Website: https://www.surina.net/soundtouch/  
License: **LGPL-2.1**

### 🎧 LAME (LAME Ain’t an MP3 Encoder)
Used to encode processed audio back to MP3 format after pitch or tempo changes.  
Website: https://lame.sourceforge.io/  
License: **GPL-2.0**

To comply with the LAME license, the relevant parts of the source code that interact with the LAME library (e.g., JNI bindings and processing code) are published here.

## 📄 License

This repository is published under the **GNU General Public License v2.0 (GPL-2.0)**  
See the [LICENSE](./LICENSE) file for details.

Please note: This repository contains **only** the relevant parts of the application that interact with the LAME and SoundTouch libraries, in accordance with their license requirements.
