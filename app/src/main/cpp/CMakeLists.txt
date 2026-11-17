cmake_minimum_required(VERSION 3.22.1)
project(trifer_whisper LANGUAGES C CXX)

# Build debug AGP memakai -O0, yang membuat inferensi puluhan kali lebih lambat. Paksa optimasi di semua varian.
set(CMAKE_C_FLAGS_DEBUG "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")

# whisper.cpp (MIT) dibangun statis, hanya backend CPU.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp whisper_build)

add_library(trifer_whisper SHARED whisper_jni.cpp)
target_include_directories(trifer_whisper PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp/ggml/include)
target_link_libraries(trifer_whisper PRIVATE whisper ggml log android)
