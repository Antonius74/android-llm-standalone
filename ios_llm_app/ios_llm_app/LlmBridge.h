#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Objective-C wrapper around a llama.cpp C++ inference backend.
 *
 * This class is meant to be called from Swift to load a GGUF model
 * and run text generation entirely on the device.
 */
@interface LlmBridge : NSObject

/**
 * Loads a GGUF model from the given absolute file path.
 *
 * @param path Absolute path to the .gguf file inside the app container.
 * @return YES if the model and context were created successfully.
 */
- (BOOL)loadModel:(NSString *)path;

/**
 * Generates text from a prompt using the currently loaded model.
 *
 * The generation loop uses greedy sampling and stops at end-of-generation
 * tokens or after a fixed maximum number of tokens.
 *
 * @param prompt The user prompt as plain text.
 * @return The generated reply as a string.
 */
- (NSString *)generate:(NSString *)prompt;

@end

NS_ASSUME_NONNULL_END
