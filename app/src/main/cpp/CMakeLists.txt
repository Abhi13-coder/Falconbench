cmake_minimum_required(VERSION 3.22.1)
project(falconbench LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# armeabi-v7a ONLY — flags from llama.cpp #5702 (Android armv7)
message(STATUS "FalconBench ABI=${ANDROID_ABI}")

if(NOT ANDROID_ABI STREQUAL "armeabi-v7a")
    message(FATAL_ERROR "armeabi-v7a only. Got ${ANDROID_ABI}")
endif()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon-vfpv4 -mfloat-abi=softfp -mno-unaligned-access -funsafe-math-optimizations -fno-math-errno -fomit-frame-pointer")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon-vfpv4 -mfloat-abi=softfp -mno-unaligned-access -funsafe-math-optimizations -fno-math-errno -fomit-frame-pointer")

set(LLAMA_DIR "${CMAKE_CURRENT_LIST_DIR}/third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Run: bash scripts/fetch-llama.sh")
endif()

set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} ${CMAKE_BINARY_DIR}/llama EXCLUDE_FROM_ALL)

add_library(falconbench SHARED falcon_jni.cpp cpu_ctrl.cpp)

target_include_directories(falconbench PRIVATE
    ${LLAMA_DIR}
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/ggml/src
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(falconbench llama ${log-lib} ${android-lib})
if(TARGET ggml)
    target_link_libraries(falconbench ggml)
elseif(TARGET ggml-base)
    target_link_libraries(falconbench ggml-base)
endif()
