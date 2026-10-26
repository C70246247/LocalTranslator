# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

project(OCR_Native)

# PaddleLite 路径
set(PaddleLite_DIR "${CMAKE_CURRENT_SOURCE_DIR}/include")
include_directories(${PaddleLite_DIR})

# OpenCV 路径
set(OpenCV_INCLUDE_DIRS "${CMAKE_CURRENT_SOURCE_DIR}/opencv/${ANDROID_ABI}/include")
include_directories(${OpenCV_INCLUDE_DIRS})

# 手动链接 OpenCV 静态库 (避开 find_package 崩溃问题)
function(import_opencv_lib name)
    add_library(${name} STATIC IMPORTED)
    set_target_properties(${name} PROPERTIES IMPORTED_LOCATION
        "${CMAKE_CURRENT_SOURCE_DIR}/opencv/${ANDROID_ABI}/libs/${name}.a")
endfunction()

# 导入 OpenCV 3rdparty 库
function(import_3rdparty_lib name)
    add_library(${name} STATIC IMPORTED)
    set_target_properties(${name} PROPERTIES IMPORTED_LOCATION
        "${CMAKE_CURRENT_SOURCE_DIR}/opencv/${ANDROID_ABI}/3rdparty/libs/${name}.a")
endfunction()

import_opencv_lib(libopencv_core)
import_opencv_lib(libopencv_imgproc)
import_opencv_lib(libopencv_imgcodecs)
import_opencv_lib(libopencv_objdetect)
import_opencv_lib(libopencv_photo)
import_opencv_lib(libopencv_video)
import_opencv_lib(libopencv_ml)
import_opencv_lib(libopencv_flann)
import_opencv_lib(libopencv_features2d)
import_opencv_lib(libopencv_calib3d)
import_opencv_lib(libopencv_stitching)
import_opencv_lib(libopencv_videoio)
import_opencv_lib(libopencv_highgui)

import_3rdparty_lib(libtegra_hal)
import_3rdparty_lib(libcpufeatures)
import_3rdparty_lib(liblibjpeg-turbo)
import_3rdparty_lib(liblibpng)
import_3rdparty_lib(liblibwebp)
import_3rdparty_lib(liblibtiff)
import_3rdparty_lib(libtbb)
import_3rdparty_lib(libIlmImf)
import_3rdparty_lib(liblibjasper)

set(OpenCV_LIBS 
    libopencv_stitching libopencv_objdetect libopencv_calib3d libopencv_features2d 
    libopencv_video libopencv_photo libopencv_ml libopencv_flann 
    libopencv_highgui libopencv_videoio libopencv_imgcodecs libopencv_imgproc libopencv_core
    libtegra_hal libcpufeatures liblibjpeg-turbo liblibpng liblibwebp liblibtiff libtbb
    libIlmImf liblibjasper)

set(CMAKE_CXX_FLAGS
    "${CMAKE_CXX_FLAGS} -ffast-math -Ofast -Os -DNDEBUG -fno-exceptions -fomit-frame-pointer -fno-asynchronous-unwind-tables -fno-unwind-tables"
)
set(CMAKE_CXX_FLAGS
    "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden -fdata-sections -ffunction-sections -fexceptions"
)
set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-z,nocopyreloc")

add_library(
  # Sets the name of the library.
  OCR_Native
  # Sets the library as a shared library.
  SHARED
  # Provides a relative path to your source file(s).
  Native.cc pipeline.cc utils.cc cls_process.cc rec_process.cc det_process.cc db_post_process.cc clipper.cpp)

find_library(
  # Sets the name of the path variable.
  log-lib
  # Specifies the name of the NDK library that you want CMake to locate.
  log)

# 引用 PaddleLite 库
add_library(
  paddle_light_api_shared
  SHARED
  IMPORTED)

set_target_properties(
  paddle_light_api_shared
  PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}/libpaddle_light_api_shared.so
)

target_link_libraries(
  OCR_Native
  paddle_light_api_shared
  ${OpenCV_LIBS}
  z
  GLESv2
  EGL
  jnigraphics
  ${log-lib})
