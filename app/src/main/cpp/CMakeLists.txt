cmake_minimum_required(VERSION 3.22.1)
project("llm_inference")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Fetch llama.cpp at a known-good tag.
include(FetchContent)
FetchContent_Declare(
    llama.cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG        b3561
)

# Disable unneeded llama.cpp targets.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama.cpp)

add_library(
    llm_inference
    SHARED
    native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(
    llm_inference
    llama
    common
    ${log-lib}
)
