# iOS LLM — Standalone Gemma 4B

A native iOS proof-of-concept application (SwiftUI + Objective-C++ + `llama.cpp`) that runs a small LLM **entirely offline on the device**. It loads a Gemma 4B Instruct model in GGUF format and provides a simple chat interface for on-device text generation.

> **Status**: design and scaffolding. The project currently contains the SwiftUI UI and the Objective-C++ bridge skeleton. The final step is to wire `llama.cpp` into the Xcode project so the C++ backend actually compiles.

> **Note about `gemma4:latest`**: that identifier is an Ollama tag. Ollama does not run natively on iOS. This project instead downloads a **Gemma 4B Instruct GGUF** model and executes it directly with `llama.cpp` compiled for iOS. It is therefore truly standalone.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [What is in this folder](#what-is-in-this-folder)
- [Technology stack](#technology-stack)
- [Supported devices](#supported-devices)
- [Prerequisites](#prerequisites)
- [How to wire llama.cpp](#how-to-wire-llamacpp)
- [Build and run](#build-and-run)
- [Model download](#model-download)
- [Configuration](#configuration)
- [Project structure](#project-structure)
- [iOS-specific considerations](#ios-specific-considerations)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)
- [Future improvements](#future-improvements)
- [License](#license)

---

## Overview

The goal is to port the same offline LLM concept from the Android sibling project to iOS / iPadOS. The model weights live inside the app container, inference happens in C++ via `llama.cpp`, and the UI is written in SwiftUI.

Key characteristics:

- **Offline / standalone**: no cloud API, no remote server.
- **Metal GPU acceleration**: iOS devices have unified memory and fast GPUs. The bridge is designed to offload all layers to Metal by default.
- **Minimal chat UI**: send a prompt, receive a generated reply.
- **User-imported model**: the app expects `gemma-4b-it.gguf` in the `Documents` directory.

---

## Architecture

```text
┌──────────────────────────────────────┐
│  SwiftUI Chat UI                     │
│  ContentView / ChatViewModel         │
└────────────┬─────────────────────────┘
             │ Swift
             ▼
┌──────────────────────────────────────┐
│  LlmBridge (ObservableObject)        │
│  loadModel(path) / generate(prompt)  │
└────────────┬─────────────────────────┘
             │ Objective-C++ wrapper
             ▼
┌──────────────────────────────────────┐
│  C++ inference backend               │
│  llama.cpp compiled with GGML_METAL  │
│  llama_load_model_from_file          │
│  llama_decode / sample               │
└──────────────────────────────────────┘
```

---

## What is in this folder

- `ios_llm_appApp.swift` — app entry point.
- `ContentView.swift` — SwiftUI chat screen and `ChatViewModel`.
- `LlmBridge.h` / `LlmBridge.mm` — Objective-C++ class that wraps model loading and greedy generation.
- `ios_llm_app-Bridging-Header.h` — exposes `LlmBridge` to Swift.
- `Info.plist` — enables file sharing and document browser access.
- `README.md` — this file.

The actual `llama.cpp` sources are **not** bundled here. You must either:

1. Add `llama.cpp` as a Git submodule, or
2. Copy the relevant source files into the Xcode project, or
3. Build `llama.cpp` with CMake and link the resulting static library.

---

## Technology stack

| Layer | Technology |
| --- | --- |
| UI | SwiftUI |
| State / concurrency | `ObservableObject`, `@Published`, `Task`, `MainActor` |
| Language bridge | Objective-C++ `.mm` with a Swift bridging header |
| Inference engine | `llama.cpp` compiled with `GGML_METAL=ON` |
| Model storage | App `Documents` directory or On-Demand Resources |
| Minimum target | iOS 15+ (iOS 16+ recommended for modern SwiftUI) |

---

## Supported devices

- **Architectures**: `arm64` (Apple Silicon / A-series chips).
- **Minimum target**: iOS 15.
- **Recommended**: iPhone 12 or later, iPad with A14 / M1 or later.
- **RAM**: at least 4 GB free RAM. Gemma 4B Q4_K_M needs ~2.6 GB of weights plus context overhead.
- **GPU**: Metal-capable device strongly recommended.

Older iPhones (A13/A14) can run smaller quantized models (Q4_0 / Q3_K_M) but will be slower.

---

## Prerequisites

- macOS with Xcode 15 or later.
- An Apple Developer account or free personal team (to run on a real device).
- A physical iOS device for realistic testing. The iOS Simulator does **not** support Metal and has very different memory behavior.
- Git submodules or a local copy of `llama.cpp`.
- A Gemma 4B Instruct GGUF model file.

---

## How to wire llama.cpp

The repository does not include `llama.cpp` to avoid duplicating a large third-party codebase. Follow one of the methods below.

### Option A — Git submodule (recommended)

```bash
cd ios_llm_app
git submodule add https://github.com/ggerganov/llama.cpp.git llama.cpp
git submodule update --init --recursive
```

### Option B — Copy source files into Xcode

1. Clone `llama.cpp` separately.
2. Drag the following files into the Xcode project:
   - `llama.cpp`, `llama.h`
   - All `ggml*.c`, `ggml*.cpp`, `ggml*.h` files
   - `ggml-metal.m`, `ggml-metal.metal` (when Metal is enabled)
   - `common/*.cpp`, `common/*.h` if you use helpers
3. Make sure the files are added to the app target.

### Option C — CMake build

Build `llama.cpp` as a static library for iOS and link it in Xcode:

```bash
cmake -B build-ios \
  -DLLAMA_METAL=ON \
  -DCMAKE_SYSTEM_NAME=iOS \
  -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=15.0 \
  -DCMAKE_BUILD_TYPE=Release

cmake --build build-ios --config Release
```

Then add the produced `libllama.a` and `libggml_static.a` to Xcode.

---

## Build and run

1. Open `ios_llm_app/ios_llm_app.xcodeproj` in Xcode.
2. Wire `llama.cpp` using one of the methods above.
3. Select a real iOS device or the Simulator (only for UI testing; inference will be CPU and very slow).
4. Build and run (`Cmd+R`).

The first build compiles `llama.cpp`, which may take several minutes.

---

## Model download

The model is not bundled because it is several GB.

### Recommended sources

- `bartowski/gemma-4b-it-GGUF` on Hugging Face
- `lmstudio-community/gemma-4b-it-GGUF` on Hugging Face

Recommended quantization:

| Device class | Quantization | Approx. size |
| --- | --- | --- |
| iPhone 15 Pro / M-series iPad | Q5_K_M | ~3.1 GB |
| iPhone 14 / iPhone 15 | Q4_K_M | ~2.6 GB |
| Older iPhone / 4 GB RAM devices | Q4_0 / Q3_K_M | ~2.0 GB |

### How to place the model on the device

The app looks for:

```
Documents/gemma-4b-it.gguf
```

inside the app container.

#### Method 1 — File Sharing in Finder / iTunes

1. Build and run the app once on the device.
2. Connect the device to a Mac.
3. Open Finder, select the device, go to the **Files** tab, find `iOS LLM`.
4. Drag `gemma-4b-it.gguf` into the app folder.

#### Method 2 — AirDrop or Files app

AirDrop the `.gguf` file to the iPhone, then open it with the `iOS LLM` app. The app extension handler (not yet implemented) would copy it to `Documents`.

#### Method 3 — In-app download

Add an `URLSession` download task that saves the file to `Documents`. Be aware of App Store review implications for apps that download and execute large ML models.

---

## Configuration

Edit `LlmBridge.mm` to tune inference:

```objc
static const int kContextLength = 2048;   // max context length
static const int kMaxTokens = 256;        // max generated tokens per request
static const int kThreads = 4;            // CPU threads used for non-GPU ops
```

To control GPU offloading:

```cpp
llama_model_params mparams = llama_model_default_params();
mparams.n_gpu_layers = 999;   // offload everything to Metal
```

Edit `ContentView.swift` to change the expected filename:

```swift
.appendingPathComponent("gemma-4b-it.gguf")
```

---

## Project structure

```
ios_llm_app/
├── ios_llm_app/
│   ├── ios_llm_appApp.swift
│   ├── ContentView.swift
│   ├── LlmBridge.h
│   ├── LlmBridge.mm
│   ├── ios_llm_app-Bridging-Header.h
│   ├── Info.plist
│   └── Assets.xcassets/
├── llama.cpp/                  # add as submodule or copy here
└── README.md
```

---

## iOS-specific considerations

### Metal performance

iOS devices share memory between CPU and GPU, so model weights can stay in one place while the GPU computes. This makes inference much faster than Android phones running CPU-only.

To use Metal:

- Build `llama.cpp` with `GGML_METAL=ON`.
- Make sure the `.metal` shader file is included in the app bundle.
- Set `n_gpu_layers` to a high value (e.g., `999`).

### Sandboxing and file access

iOS apps cannot access arbitrary files. The model must be inside the app container. The `Info.plist` already declares:

- `UISupportsDocumentBrowser`
- `UIFileSharingEnabled`
- `LSSupportsOpeningDocumentsInPlace`

### App Store binary size

If you bundle the model inside the app:

- The IPA will be very large.
- Use **On-Demand Resources (ODR)** to host the model on App Store servers and download it on first launch.
- Alternatively, make the user import the model after installation.

### App Store review

Apps that download and execute large machine-learning models may receive extra scrutiny. Be prepared to explain:

- The model source and license.
- That inference happens locally.
- Why the app needs to download large files.

### Background execution

Inference can take seconds. Always run the bridge off the main actor:

```swift
Task {
    let reply = bridge.generate(text)
    await MainActor.run {
        // update UI
    }
}
```

### Memory warnings

iOS may kill apps that use too much memory. Reduce `n_ctx` and the model size on older devices.

---

## Troubleshooting

### Build fails because `llama.h` is not found

Make sure the `llama.cpp` header directory is in **Header Search Paths** and that the source files are added to the target.

### Model load returns NO

- Verify the file exists at `Documents/gemma-4b-it.gguf`.
- Check that the file is a valid GGUF model.
- Ensure the device has enough free RAM.

### App is killed during generation

The model may be too large for the device. Try a smaller quantization or reduce `kContextLength`.

### Simulator is extremely slow

The Simulator does not use Metal and runs CPU inference through Rosetta on Apple Silicon Macs. Always test on a real device for realistic performance.

---

## Limitations

- Single-turn generation. Chat history is not fed back into the context.
- No token streaming; the full reply is returned at once.
- `llama.cpp` is not included in the repository; it must be added manually.
- No document import UI is implemented yet; use Finder file sharing.
- No chat template is applied to the prompt.

---

## Future improvements

- Wire `llama.cpp` via a Git submodule and a working Xcode project.
- Add a document picker / share extension for importing GGUFs.
- Implement token-by-token streaming to the UI.
- Add multi-turn chat history and a chat template.
- Add On-Demand Resources support for App Store distribution.
- Add CI with GitHub Actions for iOS builds.

---

## License

This iOS proof-of-concept is provided under the same terms as the Android sibling project. `llama.cpp` is MIT licensed. Gemma model weights are subject to the Google Gemma Terms of Use.
