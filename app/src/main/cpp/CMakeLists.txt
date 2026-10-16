# snn_engine CMakeLists.txt
# Configures the C SNN core + JNI wrapper as a shared library for Android NDK.

cmake_minimum_required(VERSION 3.18)

# Requires Android NDK via ndkBuild or Android Gradle Plugin
# Example app/build.gradle:
#   externalNativeBuild {
#       cmake {
#           path "src/main/cpp/CMakeLists.txt"
#       }
#   }

# Project name
project(snn_engine C)

# Optional: set target ABI and minimum API level from host env
# (typically set by Android Gradle Plugin's externalNativeBuild)
if(DEFINED ANDROID_ABI)
    message(STATUS "Android ABI: ${ANDROID_ABI}")
endif()
if(DEFINED ANDROID_PLATFORM)
    message(STATUS "Android platform: ${ANDROID_PLATFORM}")
endif()

# Create shared library
add_library(snn_engine SHARED

    # SNN core source files
    snn_core.h
    snn_core.c
    snn_jni.c

)

# Bundle headers for consumers (e.g., JNI calls from Kotlin/Java)
target_include_directories(snn_engine
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link against log for debugging (optional)
find_library(log_lib log)
if(log_lib)
    target_link_libraries(snn_engine
        PRIVATE
            ${log_lib}
    )
endif()

# Strip debug symbols in release builds to reduce APK size
set_target_properties(snn_engine PROPERTIES
    LINK_INTERFACE_LANGUAGES "C"
    BUILD_WITH_INSTALL_RPATH FALSE
)

# Ensure the library is treated as a standalone C99 module
set_target_properties(snn_engine PROPERTIES
    C_STANDARD 99
    C_STANDARD_REQUIRED ON
)

# Install target for NDK build (optional)
install(TARGETS snn_engine
    LIBRARY DESTINATION lib
)
