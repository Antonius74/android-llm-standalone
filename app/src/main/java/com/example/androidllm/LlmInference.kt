package com.example.androidllm

/**
 * Thin Kotlin wrapper around the native llama.cpp inference backend.
 * The native side exposes [loadModel] and [generate] methods.
 */
class LlmInference {
    init {
        System.loadLibrary("llm_inference")
    }

    external fun loadModel(modelPath: String): Boolean
    external fun generate(prompt: String): String
}
