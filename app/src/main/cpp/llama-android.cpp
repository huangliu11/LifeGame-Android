#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <android/log.h>
#include <bits/sysconf.h>
#include "llama.h"

#define LOG_TAG "LlamaAndroid"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

//// 手动声明 llama.cpp 的导出函数
//extern "C" {
//// 这是一个标准 API，即使头文件里没写，只要链接了 libllama.so/.a 就能用
//bool llama_kv_cache_clear(struct llama_context * ctx);
//}

struct LlamaWrapper {
    llama_model* model;
    llama_context* ctx;
    llama_sampler* sampler;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_lifequest_ai_LlamaInference_nativeInit(
        JNIEnv* env, jobject, jstring model_path_jstr) {

    LOGI("========================================");
    LOGI("=== nativeInit START ===");
    LOGI("========================================");

    const char* model_path = env->GetStringUTFChars(model_path_jstr, nullptr);
    LOGI("Model path: %s", model_path);

    // 检查文件是否存在
    FILE* file = fopen(model_path, "rb");
    if (!file) {
        LOGE("❌ Cannot open model file: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0;
    }

    // 获取文件大小
    fseek(file, 0, SEEK_END);
    long file_size = ftell(file);
    fclose(file);

    LOGI("✅ Model file exists, size: %ld bytes (%.2f MB)",
         file_size, file_size / 1024.0 / 1024.0);

    // 初始化后端
    LOGI("Initializing llama backend...");
    llama_backend_init();
    LOGI("✅ Backend initialized");

    // 模型参数
    LOGI("Setting up model params...");
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU only

    // ⭐ 关键：启用 mmap
    model_params.use_mmap = true;
    model_params.use_mlock = false;

    LOGI("Model params: n_gpu_layers=%d, use_mmap=%d, use_mlock=%d",
         model_params.n_gpu_layers, model_params.use_mmap, model_params.use_mlock);

    // 加载模型
    LOGI("⏳ Loading model (this may take 10-30 seconds)...");
    auto load_start = std::chrono::high_resolution_clock::now();

    llama_model* model = llama_model_load_from_file(model_path, model_params);

    auto load_end = std::chrono::high_resolution_clock::now();
    auto load_duration = std::chrono::duration_cast<std::chrono::milliseconds>(
            load_end - load_start
    ).count();

    env->ReleaseStringUTFChars(model_path_jstr, model_path);

    if (!model) {
        LOGE("❌ Failed to load model (took %lld ms)", load_duration);
        llama_backend_free();
        return 0;
    }

    LOGI("✅ Model loaded successfully in %lld ms (%.2f s)",
         load_duration, load_duration / 1000.0f);

    // 上下文参数
    LOGI("Setting up context params...");
    llama_context_params ctx_params = llama_context_default_params();

    // ⭐ 关键优化
    ctx_params.n_ctx = 2048;           // 从 2048 降到 512
    ctx_params.n_batch = 512;         // 批处理大小
    ctx_params.n_threads = 4;         // 线程数
    ctx_params.n_threads_batch = 4;

    LOGI("Context params: n_ctx=%d, n_batch=%d, n_threads=%d",
         ctx_params.n_ctx, ctx_params.n_batch, ctx_params.n_threads);

    // 创建上下文
    LOGI("⏳ Creating context...");
    auto ctx_start = std::chrono::high_resolution_clock::now();

    llama_context* ctx = llama_init_from_model(model, ctx_params);

    auto ctx_end = std::chrono::high_resolution_clock::now();
    auto ctx_duration = std::chrono::duration_cast<std::chrono::milliseconds>(
            ctx_end - ctx_start
    ).count();

    if (!ctx) {
        LOGE("❌ Failed to create context (took %lld ms)", ctx_duration);
        llama_model_free(model);
        llama_backend_free();
        return 0;
    }

    LOGI("✅ Context created in %lld ms", ctx_duration);

    // 创建 sampler
    LOGI("Creating sampler...");
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    LOGI("✅ Sampler created");

    // 创建 wrapper
    auto* wrapper = new LlamaWrapper{model, ctx, sampler};

    LOGI("========================================");
    LOGI("=== Model Initialized Successfully ===");
    LOGI("========================================");
    LOGI("Total init time: %lld ms", load_duration + ctx_duration);
    LOGI("Wrapper pointer: %p", wrapper);
    LOGI("Model pointer: %p", model);
    LOGI("Context pointer: %p", ctx);
    LOGI("Sampler pointer: %p", sampler);
    LOGI("========================================");

    return reinterpret_cast<jlong>(wrapper);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_lifequest_ai_LlamaInference_nativeGenerate(
        JNIEnv* env, jobject, jlong handle, jstring prompt_jstr, jint max_tokens) {

    LOGI("========================================");
    LOGI("=== nativeGenerate START ===");
    LOGI("========================================");
    LOGI("Timestamp: %lld", (long long)time(nullptr));
    LOGI("Handle: %lld", (long long)handle);
    LOGI("Max tokens: %d", max_tokens);

    // 1. 检查 wrapper
    LOGI("[1/8] Checking wrapper...");
    auto* wrapper = reinterpret_cast<LlamaWrapper*>(handle);
    if (!wrapper) {
        LOGE("❌ wrapper is NULL!");
        return env->NewStringUTF("");
    }
    LOGI("✅ wrapper OK: %p", wrapper);

    // 2. 检查 model
    LOGI("[2/8] Checking model...");
    if (!wrapper->model) {
        LOGE("❌ wrapper->model is NULL!");
        return env->NewStringUTF("");
    }
    LOGI("✅ model OK: %p", wrapper->model);
    // 3. 检查 ctx
    // ⭐ 重建 context（清空 KV cache）
    LOGI("🔄 Recreating context...");

    if (wrapper->ctx) {
        llama_free(wrapper->ctx);
        wrapper->ctx = nullptr;
        LOGI("✅ Old context freed");
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    wrapper->ctx = llama_new_context_with_model(wrapper->model, ctx_params);
    if (!wrapper->ctx) {
        LOGE("❌ Failed to recreate context");
        return env->NewStringUTF("上下文重建失败");
    }

    LOGI("✅ Context recreated: n_ctx=%d, n_batch=%d",
         llama_n_ctx(wrapper->ctx), llama_n_batch(wrapper->ctx));

    // 4. 获取 prompt
    LOGI("[4/8] Getting prompt...");
    const char* prompt = env->GetStringUTFChars(prompt_jstr, nullptr);
    if (!prompt) {
        LOGE("❌ Failed to get prompt string!");
        return env->NewStringUTF("");
    }
    size_t prompt_len = strlen(prompt);
    LOGI("✅ Prompt length: %zu", prompt_len);
    LOGI("Prompt content: %.100s%s", prompt, prompt_len > 100 ? "..." : "");

    // 5. 获取 vocab
    LOGI("[5/8] Getting vocab...");
    const llama_vocab* vocab = llama_model_get_vocab(wrapper->model);
    if (!vocab) {
        LOGE("❌ vocab is NULL!");
        env->ReleaseStringUTFChars(prompt_jstr, prompt);
        return env->NewStringUTF("");
    }
    LOGI("✅ vocab OK: %p", vocab);

    // 6. Tokenize (第一次调用获取长度)
    LOGI("[6/8] Tokenizing (getting length)...");
    LOGI("Calling llama_tokenize with nullptr to get length...");

    const int n_prompt_tokens = -llama_tokenize(
            vocab,
            prompt,
            prompt_len,
            nullptr,  // 获取长度
            0,
            true,     // add_bos
            true      // special
    );

    LOGI("Tokenization length result: %d", n_prompt_tokens);

    if (n_prompt_tokens <= 0) {
        LOGE("❌ Failed to tokenize prompt, result: %d", n_prompt_tokens);
        env->ReleaseStringUTFChars(prompt_jstr, prompt);
        return env->NewStringUTF("");
    }

    LOGI("✅ Need %d tokens", n_prompt_tokens);

    // 检查上下文长度
    int n_ctx = llama_n_ctx(wrapper->ctx);
    LOGI("Context size: %d", n_ctx);
    if (n_prompt_tokens + max_tokens > n_ctx) {
        LOGI("⚠️ Prompt + max_tokens (%d + %d = %d) > context size (%d)",
             n_prompt_tokens, max_tokens, n_prompt_tokens + max_tokens, n_ctx);
        max_tokens = n_ctx - n_prompt_tokens - 10;
        LOGI("Adjusted max_tokens to: %d", max_tokens);
    }

    // 7. Tokenize (实际 tokenize)
    LOGI("[7/8] Tokenizing (actual)...");
    std::vector<llama_token> tokens_list(n_prompt_tokens);

    LOGI("Calling llama_tokenize to fill tokens...");
    int actual_tokens = llama_tokenize(
            vocab,
            prompt,
            prompt_len,
            tokens_list.data(),
            tokens_list.size(),
            true,
            true
    );

    LOGI("Actual tokenization result: %d", actual_tokens);

    if (actual_tokens != n_prompt_tokens) {
        LOGI("⚠️ Token count mismatch: expected %d, got %d", n_prompt_tokens, actual_tokens);
    }

    env->ReleaseStringUTFChars(prompt_jstr, prompt);
    LOGI("✅ Prompt tokenized: %d tokens", n_prompt_tokens);

    // 打印前几个 token
    LOGI("First 5 tokens: [%d, %d, %d, %d, %d]",
         tokens_list.size() > 0 ? tokens_list[0] : -1,
         tokens_list.size() > 1 ? tokens_list[1] : -1,
         tokens_list.size() > 2 ? tokens_list[2] : -1,
         tokens_list.size() > 3 ? tokens_list[3] : -1,
         tokens_list.size() > 4 ? tokens_list[4] : -1);

    // 8. Decode prompt
    LOGI("[8/8] Decoding prompt...");
    LOGI("Creating batch with %zu tokens...", tokens_list.size());

    llama_batch batch = llama_batch_get_one(tokens_list.data(), tokens_list.size());
    LOGI("Batch created: n_tokens=%d, n_seq_id=%d", batch.n_tokens, batch.n_seq_id);
//
//    // ⭐ 在 llama_decode 之前添加检查----------------------------------------------------------------
//    // ⭐ 安全检查
//    int ctx_size = llama_n_ctx(wrapper->ctx);
//    int prompt_tokens = tokens_list.size();
//
//    LOGI("📊 Context size: %d", ctx_size);
//    LOGI("📊 Prompt tokens: %d", prompt_tokens);
//    LOGI("📊 Max gen tokens: %d", max_tokens);
//    LOGI("📊 Total needed: %d", prompt_tokens + max_tokens);
//
//// ⭐ 如果会溢出，自动调整 max_tokens
//    if (prompt_tokens + max_tokens > ctx_size) {
//        LOGE("⚠️ OVERFLOW RISK: need %d but ctx only %d",
//             prompt_tokens + max_tokens, ctx_size);
//
//        max_tokens = ctx_size - prompt_tokens - 10; // 留 10 个缓冲
//
//        if (max_tokens < 1) {
//            return env->NewStringUTF("错误：输入文本过长，请缩短后重试");
//        }
//
//        LOGI("✅ Auto-adjusted max_tokens to %d", max_tokens);
//    }
    //检查结束---------------------------------------------------------------------------------------
    LOGI("⏳ Calling llama_decode (this may take a while)...");
    auto decode_start = std::chrono::high_resolution_clock::now();

    int decode_result = llama_decode(wrapper->ctx, batch);

    auto decode_end = std::chrono::high_resolution_clock::now();
    auto decode_duration = std::chrono::duration_cast<std::chrono::milliseconds>(
            decode_end - decode_start
    ).count();

    LOGI("llama_decode returned: %d (took %lld ms)", decode_result, decode_duration);

    if (decode_result != 0) {
        LOGE("❌ Failed to decode prompt, error code: %d", decode_result);
        return env->NewStringUTF("");
    }

    LOGI("✅ Prompt decoded successfully in %lld ms", decode_duration);

    // 9. 生成循环
    LOGI("========================================");
    LOGI("=== Starting Generation Loop ===");
    LOGI("========================================");

    std::string result;
    result.reserve(max_tokens * 4);
    int n_decoded = 0;

    auto gen_start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < max_tokens; i++) {
        // 每10个token打印一次进度
        if (i % 10 == 0) {
            LOGI("Progress: %d/%d tokens generated", i, max_tokens);
        }

        // Sample
        llama_token new_token_id = llama_sampler_sample(wrapper->sampler, wrapper->ctx, -1);

        // 检查 EOS
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("✅ EOS token reached at position %d", i);
            break;
        }

        // Token to piece
        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);

        if (n < 0) {
            LOGE("❌ Failed to convert token to piece at position %d", i);
            break;
        }

        if (n > 0) {
            result.append(buf, n);
        }

        // Decode next token
        batch = llama_batch_get_one(&new_token_id, 1);

        if (llama_decode(wrapper->ctx, batch) != 0) {
            LOGE("❌ Failed to decode token at position %d", i);
            break;
        }

        n_decoded++;
    }

    auto gen_end = std::chrono::high_resolution_clock::now();
    auto gen_duration = std::chrono::duration_cast<std::chrono::milliseconds>(
            gen_end - gen_start
    ).count();

    float tokens_per_sec = n_decoded * 1000.0f / (gen_duration > 0 ? gen_duration : 1);

    LOGI("========================================");
    LOGI("=== Generation Complete ===");
    LOGI("========================================");
    LOGI("Generated tokens: %d", n_decoded);
    LOGI("Generation time: %lld ms (%.2f s)", gen_duration, gen_duration / 1000.0f);
    LOGI("Speed: %.2f tokens/s", tokens_per_sec);
    LOGI("Result length: %zu characters", result.length());
    LOGI("Result preview: %.100s%s", result.c_str(), result.length() > 100 ? "..." : "");

    // 性能评估
    if (tokens_per_sec < 1.0f) {
        LOGE("❌ VERY SLOW: %.2f tokens/s", tokens_per_sec);
    } else if (tokens_per_sec < 3.0f) {
        LOGI("⚠️ SLOW: %.2f tokens/s", tokens_per_sec);
    } else if (tokens_per_sec < 8.0f) {
        LOGI("✅ ACCEPTABLE: %.2f tokens/s", tokens_per_sec);
    } else {
        LOGI("✅ GOOD: %.2f tokens/s", tokens_per_sec);
    }

    LOGI("========================================");
    LOGI("=== nativeGenerate END ===");
    LOGI("========================================");

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_lifequest_ai_LlamaInference_nativeDestroy(
        JNIEnv* env, jobject, jlong handle) {

    auto* wrapper = reinterpret_cast<LlamaWrapper*>(handle);
    if (!wrapper) return;

    LOGI("Destroying llama model");

    if (wrapper->sampler) {
        llama_sampler_free(wrapper->sampler);
        wrapper->sampler = nullptr;
    }

    if (wrapper->ctx) {
        llama_free(wrapper->ctx);
        wrapper->ctx = nullptr;
    }

    if (wrapper->model) {
        llama_model_free(wrapper->model);
        wrapper->model = nullptr;
    }

    delete wrapper;
    llama_backend_free();

    LOGI("Model destroyed successfully");
}