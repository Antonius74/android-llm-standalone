# iOS Standalone LLM — Design Notes

This document describes how the same offline, on-device LLM concept used in the Android app can be ported to **iOS / iPadOS**.

> **Status**: design and proof-of-concept scaffolding. The repository currently contains the fully working Android project plus these iOS planning files. A real iOS Xcode project can be added later if needed.

---

## Is it possible?

**Yes.** Running a small LLM locally on an iPhone or iPad is technically feasible and has already been demonstrated by projects such as:

- [llama.cpp](https://github.com/ggerganov/llama.cpp) (core inference engine)
- [MLC LLM](https://llm.mlc.ai/)
- [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai)
- [Local AI](https://github.com/lzw-lzw/LocalAI)
- [exllamav2 / aya]

The main differences compared to Android are:

1. **Build system**: Xcode + Swift/SwiftUI instead of Android Studio + Kotlin/Gradle.
2. **Metal GPU acceleration**: iOS devices have a unified memory architecture and very fast GPU/Neural Engine. llama.cpp supports Metal via `GGML_METAL`, which gives a massive speed-up over CPU-only inference.
3. **Sandboxing**: an iOS app can only read files inside its own container (`Documents`, `Library/Application Support`, etc.). A GGUF model must be bundled at build time, downloaded into the app container, or shared through File Provider / AirDrop.
4. **App Store rules**: apps that download and execute large models may be questioned during review. The safest approach is to ship the model inside the app bundle or make the user import it explicitly as a document.
5. **Binary size limits**: App Store requires app slicing / on-demand resources for very large binaries. A multi-GB model cannot be embedded as a normal app resource; use **On-Demand Resources (ODR)** or ask the user to download it after install.

---

## Architecture for iOS

```text
┌──────────────────────────────────────┐
│  SwiftUI Chat UI                     │
│  ChatView / ViewModel                │
└────────────┬─────────────────────────┘
             │ Swift
             ▼
┌──────────────────────────────────────┐
│  LlmBridge (ObservableObject)        │
│  loadModel(path) / generate(prompt)  │
└────────────┬─────────────────────────┘
             │ Objective-C++ wrapper (or pure C++)
             ▼
┌──────────────────────────────────────┐
│  C++ inference backend               │
│  llama.cpp compiled with GGML_METAL  │
│  llama_load_model_from_file          │
│  llama_decode / sample               │
└──────────────────────────────────────┘
```

---

## Technology stack

| Layer | Technology |
| --- | --- |
| UI | SwiftUI |
| State / lifecycle | `ObservableObject`, `@Published`, `Task` |
| Bridge | Objective-C++ `.mm` file or a C wrapper callable from Swift |
| Inference engine | `llama.cpp` compiled with `GGML_METAL=ON` |
| Model storage | App `Documents` directory or On-Demand Resources |
| Minimum target | iOS 15+ (or iOS 16+ for modern SwiftUI features) |

---

## Build overview

1. Create a new **Xcode iOS App project** (SwiftUI interface).
2. Add `llama.cpp` as a Git submodule or fetch it with the Swift Package Manager / CMake.
3. Add the llama.cpp source files to the Xcode project:
   - `llama.cpp`, `llama.h`
   - `ggml*.c`, `ggml*.cpp`, `ggml*.h`
   - `ggml-metal.m`, `ggml-metal.metal` (when Metal is enabled)
4. Add the required Xcode build settings:
   - **Other Linker Flags**: `-framework Foundation`, `-framework Metal`, `-framework MetalKit`
   - **Preprocessor macros**: `GGML_USE_METAL=1`
   - **Header Search Paths**: path to `llama.cpp` root and `common`
5. Create an Objective-C++ class `LlmBridge.mm` that imports `llama.h` and exposes:
   - `-(BOOL)loadModel:(NSString *)path`
   - `-(NSString *)generate:(NSString *)prompt`
6. Expose it to Swift with a bridging header or `@objc`.
7. Build a SwiftUI chat screen that calls the bridge.

---

## File layout (proposed)

```
ios_llm_app/
├── ios_llm_app.xcodeproj/
├── ios_llm_app/
│   ├── ios_llm_appApp.swift
│   ├── ContentView.swift          # chat UI
│   ├── LlmBridge.h              # Objective-C header
│   ├── LlmBridge.mm             # Objective-C++ implementation
│   ├── ios_llm_app-Bridging-Header.h
│   └── Assets.xcassets/
└── llama.cpp/                   # submodule or CMake fetched
```

---

## Recommended model for iOS

On modern iPhones (A15/A16/A17 Pro, M1/M2 iPad) Gemma 4B works well.

| Device class | Suggested quantization | Approx. size | Expected speed |
| --- | --- | --- | --- |
| iPhone 15 Pro / M2 iPad | Q5_K_M | ~3.1 GB | usable, fast with Metal |
| iPhone 14 / iPhone 15 | Q4_K_M | ~2.6 GB | usable |
| Older iPhone (A13/A14) | Q4_0 / Q3_K_M | ~2.0 GB | slow but possible |

Models to download from Hugging Face:

- `bartowski/gemma-4b-it-GGUF`
- `lmstudio-community/gemma-4b-it-GGUF`

The file should be named `gemma-4b-it.gguf` and stored in the app `Documents` directory.

---

## Key iOS-specific considerations

### 1. Metal performance shaders

Enable Metal in the llama.cpp build:

```bash
cmake -DLLAMA_METAL=ON ...
```

In Xcode, make sure the `.metal` shader file is compiled and bundled. The runtime calls:

```cpp
llama_model_params mparams = llama_model_default_params();
mparams.n_gpu_layers = 999; // offload all layers to Metal GPU
```

### 2. Sandboxing and file access

Use the app container:

```swift
let docDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
let modelURL = docDir.appendingPathComponent("gemma-4b-it.gguf")
```

To let the user import a model, add `UISupportsDocumentBrowser = YES` to `Info.plist` and implement `UIDocumentPickerViewController`.

### 3. Memory warnings

iOS may terminate the app if memory usage spikes. Gemma 4B can consume several GB. Recommendations:

- Keep `n_ctx` modest (1024–2048).
- Offload as many layers as possible to Metal (unified memory helps).
- Use smaller quantized models on devices with 4 GB RAM.

### 4. App Store binary size

If the model is bundled, the IPA will be huge. Options:

- **On-Demand Resources (ODR)**: host the model on App Store servers and download on first launch.
- **In-app download**: download the GGUF from your own CDN after installation, but be prepared to justify this to App Review.
- **User-imported model**: the smallest initial download, but worse UX.

### 5. Background execution

Long inference runs block the main thread if not handled carefully. Run the bridge on a background `Task` / `DispatchQueue` and update `@Published` properties on the main actor.

---

## Sample SwiftUI outline

```swift
import SwiftUI

@main
struct IosLlmApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class ChatViewModel: ObservableObject {
    @Published var messages: [Message] = []
    @Published var isLoading = false
    @Published var status = "Model not loaded"

    private let bridge = LlmBridge()
    private let modelURL = FileManager.default
        .urls(for: .documentDirectory, in: .userDomainMask)
        .first!
        .appendingPathComponent("gemma-4b-it.gguf")

    func load() {
        Task {
            await MainActor.run { status = "Loading model..." }
            let ok = bridge.loadModel(modelURL.path)
            await MainActor.run { status = ok ? "Model loaded" : "Failed to load" }
        }
    }

    func send(_ text: String) {
        messages.append(Message(text: text, isUser: true))
        isLoading = true
        Task {
            let reply = bridge.generate(text)
            await MainActor.run {
                messages.append(Message(text: reply, isUser: false))
                isLoading = false
            }
        }
    }
}

struct ContentView: View {
    @StateObject private var vm = ChatViewModel()
    @State private var input = ""

    var body: some View {
        VStack {
            Text(vm.status)
            List(vm.messages) { msg in
                Text(msg.text)
                    .foregroundStyle(msg.isUser ? .blue : .primary)
            }
            HStack {
                TextField("Prompt", text: $input)
                Button("Send") {
                    vm.send(input)
                    input = ""
                }
                .disabled(vm.isLoading)
            }
        }
        .onAppear { vm.load() }
    }
}

struct Message: Identifiable {
    let id = UUID()
    let text: String
    let isUser: Bool
}
```

---

## Objective-C++ bridge example

```objc
// LlmBridge.h
#import <Foundation/Foundation.h>

@interface LlmBridge : NSObject
- (BOOL)loadModel:(NSString *)path;
- (NSString *)generate:(NSString *)prompt;
@end
```

```objc
// LlmBridge.mm
#import "LlmBridge.h"
#include "llama.h"
#include <string>

@interface LlmBridge () {
    llama_model *model;
    llama_context *ctx;
}
@end

@implementation LlmBridge

- (instancetype)init {
    self = [super init];
    model = nullptr;
    ctx = nullptr;
    return self;
}

- (BOOL)loadModel:(NSString *)path {
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 999;

    model = llama_load_model_from_file([path UTF8String], mparams);
    if (!model) return NO;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_threads = 4;
    ctx = llama_new_context_with_model(model, cparams);
    return ctx != nullptr;
}

- (NSString *)generate:(NSString *)prompt {
    // tokenize, decode, sample (same logic as Android native-lib.cpp)
    return @"TODO: implement generation loop";
}

@end
```

---

## Comparison Android vs iOS

| Aspect | Android | iOS |
| --- | --- | --- |
| IDE / build | Android Studio + Gradle + CMake | Xcode + Swift/SwiftUI |
| GPU backend | Vulkan / OpenCL / QNN (complex) | Metal (well supported) |
| Memory | separate CPU/GPU memory | unified memory, faster Metal |
| Model import | adb push / DownloadManager | AirDrop / Files app / ODR |
| App Store size | Play Store also limits bundle size | ODR is mature |
| Default performance | CPU-only recommended | GPU/Metal strongly recommended |

---

## Next steps to make this real

1. Add an Xcode project under a new `ios_llm_app/` folder.
2. Add `llama.cpp` as a Git submodule and wire it into the Xcode build.
3. Implement `LlmBridge.mm` with the full generation loop.
4. Build a SwiftUI chat screen matching the Android UX.
5. Add a document picker so the user can import a GGUF.
6. Test on a real device (simulator has no Metal and very different memory behavior).
7. Add CI with GitHub Actions for building the iOS app.

---

## License note

The iOS port would reuse the same `llama.cpp` backend and the same model weights, therefore the same licensing considerations apply: MIT for `llama.cpp`, Google Gemma Terms of Use for the model weights.
