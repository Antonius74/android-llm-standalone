# Android LLM — Standalone Gemma 4B

A native Android application (Kotlin + Jetpack Compose + `llama.cpp`) that runs a small LLM **entirely offline on the device**. The app loads a Gemma 4B Instruct model in GGUF format, loads it into memory, and provides a simple chat interface for text generation with no network required after the model is downloaded.

> **Note about `gemma4:latest`**: that identifier is an Ollama tag. Ollama does not run natively on Android. This project instead downloads a **Gemma 4B Instruct GGUF** model and executes it directly with `llama.cpp` compiled for Android. It is therefore truly standalone.

---

## Table of Contents

- [Overview](#overview)
- [What is in this repository](#what-is-in-this-repository)
- [Architecture](#architecture)
- [How it works](#how-it-works)
- [Models used](#models-used)
- [Supported devices](#supported-devices)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Build and run](#build-and-run)
- [Model download](#model-download)
- [Project structure](#project-structure)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)
- [Future improvements](#future-improvements)
- [License](#license)

---

## Overview

The goal of this project is to demonstrate a self-contained LLM client on Android. The model weights live on the device storage, inference happens in native code via `llama.cpp`, and the UI is written in Kotlin with Jetpack Compose.

Key characteristics:

- **Offline / standalone**: no cloud API, no remote server, no internet needed after the model is downloaded.
- **Native inference**: `llama.cpp` is compiled into the APK as a native library (`libllm_inference.so`) and exposes a thin JNI bridge.
- **Minimal chat UI**: send a prompt, receive a generated reply.
- **CPU-first**: the default build uses CPU inference (`n_gpu_layers = 0`) for maximum device compatibility.

---

## What is in this repository

- Android Studio project with Gradle build files.
- CMake configuration that fetches and builds `llama.cpp` at a pinned revision.
- A C++ JNI bridge (`native-lib.cpp`) that wraps model loading and greedy text generation.
- Kotlin classes:
  - `MainActivity.kt` — the Compose UI and the chat screen.
  - `LlmInference.kt` — loads the native library and declares JNI methods.
  - `ModelDownloadWorker.kt` — downloads the GGUF file using `DownloadManager` / WorkManager.
- README with full setup instructions.

---

## Architecture

```text
┌──────────────────────────────────────┐
│  Android UI (Jetpack Compose)        │
│  ChatScreen / MainActivity           │
└────────────┬─────────────────────────┘
             │ Kotlin
             ▼
┌──────────────────────────────────────┐
│  LlmInference (JNI wrapper)            │
│  loadModel(path) / generate(prompt)  │
└────────────┬─────────────────────────┘
             │ JNI
             ▼
┌──────────────────────────────────────┐
│  native-lib.cpp (C++)                │
│  llama_load_model_from_file          │
│  llama_tokenize / llama_decode       │
│  llama_sampler_sample                │
└────────────┬─────────────────────────┘
             │ links statically
             ▼
┌──────────────────────────────────────┐
│  llama.cpp + ggml                    │
└──────────────────────────────────────┘
```

---

## How it works

1. On startup the app checks whether `gemma-4b-it.gguf` exists in the app's private `filesDir`.
2. If the file exists, the native `loadModel()` function is called:
   - It loads the model with `llama_load_model_from_file`.
   - It creates a context with `n_ctx = 2048`, `n_threads = 4`.
3. The user types a prompt and presses **Send**.
4. `generate()` tokenizes the prompt, runs `llama_decode`, then samples up to 256 new tokens using a greedy sampler.
5. Tokens are converted back to text and returned to the UI as a single string.

The chat history is kept only in memory (single-turn generation). The native context is reused across generations to avoid reloading the model every time.

---

## Models used

This app does **not** bundle a model in the repository because model weights are large (several GB).

You should download a Gemma 4B Instruct GGUF from Hugging Face. Recommended sources:

- `bartowski/gemma-4b-it-GGUF`
- `lmstudio-community/gemma-4b-it-GGUF`

Recommended quantization for Android phones:

| Quantization | Size (approx.) | Use case |
| --- | --- | --- |
| `Q4_K_M` | ~2.6 GB | Good balance of speed and quality on 6–8 GB RAM devices |
| `Q5_K_M` | ~3.1 GB | Slightly better quality, needs more RAM |
| `Q4_0` | ~2.3 GB | Fastest, lowest quality, for weak devices |

The app looks for a file named exactly `gemma-4b-it.gguf` by default (configurable in `MainActivity.kt`).

---

## Supported devices

- **Architectures**: `arm64-v8a`, `x86_64`.
- **Minimum SDK**: 26 (Android 8.0 Oreo).
- **Target SDK**: 34 (Android 14).
- **RAM**: at least 6 GB recommended for Gemma 4B Q4_K_M. 8 GB or more is safer.
- **CPU**: inference is CPU-only in the default build. Modern ARM SoCs with fast big cores give the best experience.

Emulators work but are much slower than real devices.

---

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later.
- Android NDK r25c or later (install via SDK Manager).
- CMake 3.22.1 (Android Studio can download it automatically).
- JDK 17.
- A physical Android device or emulator with enough RAM.
- A GitHub account (to publish the repository).
- `git` and optionally the `gh` CLI installed on your machine.

---

## Installation

Clone this repository:

```bash
git clone https://github.com/YOUR_USERNAME/android-llm-standalone.git
cd android-llm-standalone
```

Open the `android_llm_app` folder in Android Studio.

Wait for the first Gradle sync. During the first sync CMake will fetch `llama.cpp` from GitHub and build it. This can take several minutes depending on your connection and CPU.

---

## Build and run

### From Android Studio

1. Select a device or emulator in the toolbar.
2. Click **Run** (or press `Shift+F10`).
3. The first build compiles `llama.cpp`, so be patient.

### From the command line

```bash
cd android_llm_app
./gradlew assembleDebug
```

The APK will be at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install manually:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Model download

The model is downloaded separately because it is too large to include in the repository.

### Recommended method: `adb push`

Download a Gemma 4B Instruct GGUF (for example `gemma-4b-it-Q4_K_M.gguf`) and push it to the device:

```bash
adb push /path/to/gemma-4b-it-Q4_K_M.gguf \
  /sdcard/Android/data/com.example.androidllm/files/gemma-4b-it.gguf
```

Then move or copy it into the app's private `filesDir` if needed, or change `modelFile` in `MainActivity.kt` to point to the SD card path.

### Alternative: in-app download

The main screen has a **"Download demo model"** button. You can pass a direct `.gguf` URL to `ModelDownloadWorker`, which uses `DownloadManager` to save the file to `filesDir/gemma-4b-it.gguf`.

> For models larger than ~2 GB, `adb push` or a desktop download manager is usually faster and more reliable.

---

## Project structure

```
android_llm_app/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   ├── CMakeLists.txt            # fetches llama.cpp and builds libllm_inference
│       │   └── native-lib.cpp            # JNI bridge for llama.cpp
│       ├── java/com/example/androidllm/
│       │   ├── MainActivity.kt           # Compose chat UI and ViewModel
│       │   ├── LlmInference.kt           # JNI wrapper class
│       │   ├── ModelDownloadWorker.kt    # WorkManager downloader
│       │   ├── LlmApplication.kt
│       │   └── ui/theme/
│       │       ├── Color.kt
│       │       ├── Theme.kt
│       │       └── Type.kt
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
└── README.md
```

---

## Configuration

### Native parameters

Edit `app/src/main/cpp/native-lib.cpp`:

```cpp
static int n_ctx = 2048;        // max context length
const int max_tokens = 256;     // max generated tokens per request
llama_sampler_init_greedy();    // greedy sampling
```

To enable GPU inference you would need to change the `llama.cpp` build options in `CMakeLists.txt` and `n_gpu_layers` in `loadModel()`.

### Model file name and path

Edit `MainActivity.kt`:

```kotlin
private val modelFile: File = File(application.filesDir, "gemma-4b-it.gguf")
```

### Build variants

By default only `arm64-v8a` and `x86_64` are built:

```kotlin
ndk {
    abiFilters += listOf("arm64-v8a", "x86_64")
}
```

---

## Troubleshooting

### Gradle sync is very slow

`llama.cpp` is downloaded and compiled during the first sync. This is expected. Subsequent builds are incremental.

### App crashes on model load

- Verify that the file is named exactly `gemma-4b-it.gguf` (or update the path in `MainActivity.kt`).
- Check that the model is a valid GGUF file for `llama.cpp`.
- Make sure the device has enough free RAM. Gemma 4B needs 4–6 GB at runtime.

### `OutOfMemoryError`

Reduce the model size (use `Q4_0` or `Q4_K_M`) or reduce `n_ctx` in `native-lib.cpp`.

### Build fails with NDK not found

Install the NDK in Android Studio: **Tools > SDK Manager > SDK Tools > NDK (Side by side)**.

### No GPU acceleration

The default build uses CPU only. GPU support requires extra setup:
- Vulkan backend with `GGML_VULKAN=ON`
- Qualcomm QNN backend
- MediaTek / Apple backends (not applicable on Android)

This is intentionally left disabled to keep the project simple and compatible.

---

## Limitations

- Single-turn generation. Chat history is not fed back into the context.
- No token streaming; the full reply is shown at once.
- CPU inference only in the default configuration.
- No chat template is applied; raw user input is tokenized as-is. For best results with instruction-tuned models, you may want to wrap the prompt manually or add a template in `native-lib.cpp`.
- Large models (7B+) are not practical on most phones without aggressive quantization.

---

## Future improvements

Possible next steps:

- Add multi-turn chat history and a chat template.
- Implement token-by-token streaming to the UI.
- Enable Vulkan or QNN GPU backend for faster inference.
- Support smaller models (Phi-3-mini, MobileLLM) for low-end devices.
- Add a model manager UI to choose between multiple downloaded GGUFs.

---

## License

This project is provided as a starting point / proof of concept. The code in this repository may be used under the MIT License unless otherwise noted.

`llama.cpp` is subject to its own license (MIT). Gemma model weights are subject to the Google Gemma Terms of Use. Make sure you comply with the license of any model you download.
