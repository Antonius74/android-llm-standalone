#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <android/log.h>

#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "LLMInference", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LLMInference", __VA_ARGS__)

static std::unique_ptr<llama_model, decltype(&llama_free_model)> g_model{nullptr, llama_free_model};
static std::unique_ptr<llama_context, decltype(&llama_free)> g_ctx{nullptr, llama_free};
static int n_ctx = 2048;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_androidllm_LlmInference_loadModel(JNIEnv *env, jobject /*thiz*/, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    std::string model_path(path);
    env->ReleaseStringUTFChars(jpath, path);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only on Android for broad compatibility

    llama_model *model = llama_load_model_from_file(model_path.c_str(), mparams);
    if (!model) {
        LOGE("Failed to load model from %s", model_path.c_str());
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx;
    cparams.n_batch = 512;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    llama_context *ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        llama_free_model(model);
        LOGE("Failed to create context");
        return JNI_FALSE;
    }

    g_model.reset(model);
    g_ctx.reset(ctx);
    LOGI("Model loaded: %s", model_path.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_androidllm_LlmInference_generate(JNIEnv *env, jobject /*thiz*/, jstring jprompt) {
    if (!g_model || !g_ctx) {
        return env->NewStringUTF("[Model not loaded]");
    }

    const char *prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    const llama_vocab *vocab = llama_get_vocab(g_model.get());

    // Tokenize prompt
    std::vector<llama_token> tokens;
    tokens.resize(prompt.size() + 16);
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), static_cast<int>(prompt.size()),
                                  tokens.data(), static_cast<int>(tokens.size()), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt.c_str(), static_cast<int>(prompt.size()),
                                  tokens.data(), static_cast<int>(tokens.size()), true, true);
    }
    tokens.resize(n_tokens);

    if (llama_decode(g_ctx.get(), llama_batch_get_one(tokens.data(), tokens.size())) != 0) {
        return env->NewStringUTF("[Decode error]");
    }

    std::string result;
    const int max_tokens = 256;
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    for (int i = 0; i < max_tokens; ++i) {
        llama_token new_token_id = llama_sampler_sample(sampler, g_ctx.get(), -1);
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        if (llama_decode(g_ctx.get(), llama_batch_get_one(&new_token_id, 1)) != 0) {
            break;
        }
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(result.c_str());
}
