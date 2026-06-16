#import "LlmBridge.h"
#include "llama.h"
#include <string>
#include <sstream>

@interface LlmBridge () {
    llama_model *model;
    llama_context *ctx;
}
@end

@implementation LlmBridge

static const int kContextLength = 2048;
static const int kMaxTokens = 256;
static const int kThreads = 4;

- (instancetype)init {
    self = [super init];
    if (self) {
        model = nullptr;
        ctx = nullptr;
    }
    return self;
}

- (void)dealloc {
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_free_model(model);
        model = nullptr;
    }
}

- (BOOL)loadModel:(NSString *)path {
    if (model) {
        llama_free_model(model);
        model = nullptr;
    }
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }

    llama_model_params mparams = llama_model_default_params();
    // Offload as many layers as possible to the Metal GPU.
    mparams.n_gpu_layers = 999;

    model = llama_load_model_from_file([path UTF8String], mparams);
    if (!model) {
        return NO;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = kContextLength;
    cparams.n_batch = 512;
    cparams.n_threads = kThreads;
    cparams.n_threads_batch = kThreads;

    ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        llama_free_model(model);
        model = nullptr;
        return NO;
    }

    return YES;
}

- (NSString *)generate:(NSString *)prompt {
    if (!model || !ctx) {
        return @"[Model not loaded]";
    }

    const char *promptCStr = [prompt UTF8String];
    const llama_vocab *vocab = llama_get_vocab(model);

    // Tokenize the prompt.
    std::vector<llama_token> tokens(prompt.length() + 16);
    int n_tokens = llama_tokenize(vocab,
                                  promptCStr,
                                  static_cast<int>(strlen(promptCStr)),
                                  tokens.data(),
                                  static_cast<int>(tokens.size()),
                                  true,
                                  true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab,
                                  promptCStr,
                                  static_cast<int>(strlen(promptCStr)),
                                  tokens.data(),
                                  static_cast<int>(tokens.size()),
                                  true,
                                  true);
    }
    tokens.resize(n_tokens);

    if (llama_decode(ctx, llama_batch_get_one(tokens.data(), tokens.size())) != 0) {
        return @"[Decode error]";
    }

    std::string result;
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    for (int i = 0; i < kMaxTokens; ++i) {
        llama_token new_token_id = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        if (llama_decode(ctx, llama_batch_get_one(&new_token_id, 1)) != 0) {
            break;
        }
    }

    llama_sampler_free(sampler);
    return [NSString stringWithUTF8String:result.c_str()];
}

@end
