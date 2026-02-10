# LifeQuest - Android 端侧大模型任务管理器

基于 llama.cpp 和 NDK 开发的 Android 本地大语言模型应用，无需联网即可体验 AI 驱动的任务管理器。
白话：这是一个管理todo list的应用，每个todo有优先级和奖励制度。

项目简介
本项目利用 llama.cpp 在手机端本地部署量化后的 LLM（大语言模型），所有计算完全在端侧（On-Device）完成，保护隐私且无需网络。用户通过与大模型对话，即可自动创建待办和获得任务建议。

核心特性
端侧 AI 推理：集成 `llama.cpp` 库，在 Android 设备上直接运行 GGUF 格式的大模型。
高性能 NDK 开发：通过 JNI/NDK 桥接 Java 与 C++，针对移动端 CPU/GPU 进行推理优化。
隐私优先：无需上传数据到云端，所有对话和决策在本地完成。

截图演示
<img src="screenshots/demo1.jpg" width="300" /> <img src="screenshots/demo2.jpg" width="300" />

技术栈
- 语言: Kotlin (UI/Logic), C++ (Inference Engine)
- 核心库: [llama.cpp](https://github.com/ggerganov/llama.cpp)
- 架构: MVVM
- 构建工具: Gradle, CMake (Native Build)
- 模型格式: GGUF (Quantized Models)

如何运行
由于模型文件较大且涉及版权，未包含在 Git 仓库中，请按以下步骤配置：
1. 请下载 GGUF 格式的模型（推荐 Q4_K_M 量化版本，约 3-4GB）
   推荐模型：TinyLlama-1.1B 或 Qwen1.5-1.8B-Chat (适合手机端)
2. 将下载的 .gguf 文件重命名为 model.gguf (或者你在代码里指定的名字)，并放入设备存储路径，或项目的 app/src/main/assets/ 目录中
3. 编译与运行
   使用 Android Studio 打开项目
   等待 Gradle 和 CMake 同步完成
   连接真机（建议配备 8GB+ 内存的 Android 手机）点击 Run

性能表现
测试机型：荣耀Magic V Flip2
模型：Qwen2.5-1.5B-Q4
推理速度：10.75 tokens/s
