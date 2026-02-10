cmake_minimum_required(VERSION 3.22.1)
project(llama-android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ============================================
# 编译选项
# ============================================
add_compile_options(
        -fvisibility=hidden
        -Wno-unused-function
        -Wno-unused-variable
        -Wno-deprecated-declarations
)

# ============================================
# 定义宏
# ============================================
add_definitions(
        -DGGML_USE_CPU
        -DGGML_BUILD=1
        -DLLAMA_BUILD=1
        -DGGML_VERSION="0.0"
        -DGGML_COMMIT="unknown"
)

# ============================================
# 设置基础路径
# ============================================
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

if(NOT EXISTS ${LLAMA_CPP_DIR})
    message(FATAL_ERROR "llama.cpp directory not found at: ${LLAMA_CPP_DIR}")
endif()

# ============================================
# 包含目录
# ============================================
include_directories(
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/src
        ${LLAMA_CPP_DIR}/ggml/include
        ${LLAMA_CPP_DIR}/ggml/src
        ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu
        ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/arch
        ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/arch/arm
)

# ============================================
# GGML 核心库
# ============================================
set(GGML_SOURCES "")

set(GGML_C_FILES
        ggml.c
        ggml-alloc.c
        ggml-quants.c
)

set(GGML_CPP_FILES
        ggml.cpp
        ggml-backend.cpp
        ggml-backend-reg.cpp
        ggml-opt.cpp
        ggml-threading.cpp
        gguf.cpp
)

foreach(file ${GGML_C_FILES})
    set(filepath ${LLAMA_CPP_DIR}/ggml/src/${file})
    if(EXISTS ${filepath})
        list(APPEND GGML_SOURCES ${filepath})
        message(STATUS "✅ GGML C: ${file}")
    endif()
endforeach()

foreach(file ${GGML_CPP_FILES})
    set(filepath ${LLAMA_CPP_DIR}/ggml/src/${file})
    if(EXISTS ${filepath})
        list(APPEND GGML_SOURCES ${filepath})
        message(STATUS "✅ GGML C++: ${file}")
    endif()
endforeach()

if(NOT GGML_SOURCES)
    message(FATAL_ERROR "❌ No GGML source files found!")
endif()

add_library(ggml STATIC ${GGML_SOURCES})

target_include_directories(ggml PUBLIC
        ${LLAMA_CPP_DIR}/ggml/include
        ${LLAMA_CPP_DIR}/ggml/src
)

target_compile_options(ggml PRIVATE
        -fexceptions
        -frtti
)

# ============================================
# GGML CPU 后端 - 包含所有实现文件
# ============================================
set(GGML_CPU_SOURCES "")

# 主 ggml-cpu 目录下的所有文件
set(GGML_CPU_MAIN_FILES
        ggml-cpu.c
        ggml-cpu.cpp
        binary-ops.cpp
        hbm.cpp
        ops.cpp          # ← 关键！包含所有 compute_forward 函数
        quants.c
        repack.cpp
        traits.cpp
        unary-ops.cpp
        vec.cpp
)

message(STATUS "")
message(STATUS "🔍 Adding ggml-cpu main files:")
foreach(file ${GGML_CPU_MAIN_FILES})
    set(filepath ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/${file})
    if(EXISTS ${filepath})
        list(APPEND GGML_CPU_SOURCES ${filepath})
        file(SIZE ${filepath} filesize)
        math(EXPR filesize_kb "${filesize} / 1024")
        message(STATUS "  ✅ ${file} (${filesize_kb}KB)")
    else()
        message(WARNING "  ⚠️  Missing: ${file}")
    endif()
endforeach()

# ARM 架构特定文件
set(GGML_CPU_ARM_FILES
        arch/arm/cpu-feats.cpp
        arch/arm/quants.c
        arch/arm/repack.cpp
)

message(STATUS "")
message(STATUS "🔍 Adding ARM arch files:")
foreach(file ${GGML_CPU_ARM_FILES})
    set(filepath ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/${file})
    if(EXISTS ${filepath})
        list(APPEND GGML_CPU_SOURCES ${filepath})
        file(SIZE ${filepath} filesize)
        math(EXPR filesize_kb "${filesize} / 1024")
        message(STATUS "  ✅ ${file} (${filesize_kb}KB)")
    else()
        message(WARNING "  ⚠️  Missing: ${file}")
    endif()
endforeach()

if(NOT GGML_CPU_SOURCES)
    message(FATAL_ERROR "❌ No ggml-cpu sources found!")
endif()

list(LENGTH GGML_CPU_SOURCES CPU_COUNT)
message(STATUS "")
message(STATUS "✅ Total ggml-cpu sources: ${CPU_COUNT} files")

add_library(ggml-cpu STATIC ${GGML_CPU_SOURCES})

target_include_directories(ggml-cpu PUBLIC
        ${LLAMA_CPP_DIR}/ggml/include
        ${LLAMA_CPP_DIR}/ggml/src
        ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu
        ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/arch
        ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/arch/arm
)

target_compile_options(ggml-cpu PRIVATE
        -fexceptions
        -frtti
)

# 链接 ggml
target_link_libraries(ggml-cpu PUBLIC ggml)

# ============================================
# LLAMA 库
# ============================================
file(GLOB LLAMA_SOURCES "${LLAMA_CPP_DIR}/src/llama*.cpp")
file(GLOB UNICODE_SOURCES "${LLAMA_CPP_DIR}/src/unicode*.cpp")
file(GLOB LLAMA_MODEL_SOURCES "${LLAMA_CPP_DIR}/src/models/*.cpp")

set(ALL_LLAMA_SOURCES ${LLAMA_SOURCES} ${UNICODE_SOURCES} ${LLAMA_MODEL_SOURCES})

if(NOT ALL_LLAMA_SOURCES)
    message(FATAL_ERROR "❌ No LLAMA source files found!")
endif()

list(LENGTH ALL_LLAMA_SOURCES LLAMA_COUNT)
message(STATUS "✅ Found ${LLAMA_COUNT} LLAMA source files")

add_library(llama STATIC ${ALL_LLAMA_SOURCES})

target_include_directories(llama PUBLIC
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/src
)

target_link_libraries(llama PUBLIC ggml ggml-cpu)

target_compile_options(llama PRIVATE
        -fexceptions
        -frtti
)

# ============================================
# Android JNI 库
# ============================================
set(JNI_SOURCE ${CMAKE_SOURCE_DIR}/llama-android.cpp)

if(NOT EXISTS ${JNI_SOURCE})
    message(FATAL_ERROR "❌ JNI source not found: ${JNI_SOURCE}")
endif()

message(STATUS "✅ Found JNI source: llama-android.cpp")

add_library(llama-android SHARED ${JNI_SOURCE})

target_link_libraries(llama-android
        llama
        ggml-cpu
        ggml
        android
        log
)

target_compile_options(llama-android PRIVATE
        -fexceptions
        -frtti
)

# ============================================
# 打印最终配置
# ============================================
message(STATUS "")
message(STATUS "===========================================")
message(STATUS "📦 LLAMA Android Build Configuration")
message(STATUS "===========================================")
message(STATUS "📁 LLAMA_CPP_DIR: ${LLAMA_CPP_DIR}")
list(LENGTH GGML_SOURCES GGML_COUNT)
message(STATUS "📄 GGML sources: ${GGML_COUNT} files")
list(LENGTH GGML_CPU_SOURCES GGML_CPU_COUNT)
message(STATUS "📄 GGML CPU sources: ${GGML_CPU_COUNT} files")
message(STATUS "📄 LLAMA sources: ${LLAMA_COUNT} files")
message(STATUS "✅ Target: ARM64 (aarch64)")
message(STATUS "✅ Exceptions: ENABLED")
message(STATUS "✅ RTTI: ENABLED")
message(STATUS "===========================================")
message(STATUS "")