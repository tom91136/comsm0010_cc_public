cmake_minimum_required(VERSION 3.12)
project(nonce_finder)

set(CMAKE_CXX_STANDARD 17)

set(THREADS_PREFER_PTHREAD_FLAG TRUE)


find_package(Threads REQUIRED)
find_library(libatomic libatomic.a)

add_executable(nonce_finder main.cpp sha256.cpp)

if (MSVC)
    target_compile_options(nonce_finder
            PUBLIC
            /Wp64
            /W3
            /Zc:wchar_t
            )
    set(RELEASE_OPTIONS /MT /GF /Gy /O2 /arch:AVX2 /fp:fast)
    set(DEBUG_OPTIONS /MTd)
else ()
    target_compile_options(nonce_finder
            PUBLIC
            -Wall
            -Wextra
            #            -Wshadow
            #            -Wold-style-cast
            -Wcast-align
            -Wpedantic

            -Werror=return-type
            -Wno-unused-parameter
            -Wno-ignored-attributes
            )
    set(RELEASE_OPTIONS -Ofast -funsafe-math-optimizations -march=native -g)
    set(DEBUG_OPTIONS -O2 -fno-omit-frame-pointer -fsanitize=address -march=native)
    target_link_libraries(nonce_finder PUBLIC $<$<CONFIG:DEBUG>:-Wl,-lasan>)
    target_link_libraries(nonce_finder  PRIVATE -static-libgcc -static-libstdc++ libatomic.a  Threads::Threads)
#    target_link_libraries(nonce_finder PRIVATE Threads::Threads)


endif ()

target_compile_options(nonce_finder PUBLIC "$<$<CONFIG:RELEASE>:${RELEASE_OPTIONS}>")
target_compile_options(nonce_finder PUBLIC "$<$<CONFIG:DEBUG>:${DEBUG_OPTIONS}>")
