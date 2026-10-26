// Copyright (c) 2019 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "Native.h"
#include "pipeline.h"
#include <android/log.h>
#include <android/bitmap.h>

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_baidu_paddle_lite_demo_ocr_db_crnn_Native
 * Method:    nativeInit
 * Signature:
 * (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_buildsession_localtranslator_ocr_OcrManager_nativeInit(
    JNIEnv *env, jclass thiz, jstring jDetModelPath, jstring jClsModelPath,
    jstring jRecModelPath, jstring jConfigPath, jstring jLabelPath,
    jint cpuThreadNum, jstring jCPUPowerMode) {
  std::string detModelPath = jstring_to_cpp_string(env, jDetModelPath);
  std::string clsModelPath = jstring_to_cpp_string(env, jClsModelPath);
  std::string recModelPath = jstring_to_cpp_string(env, jRecModelPath);
  std::string configPath = jstring_to_cpp_string(env, jConfigPath);
  std::string labelPath = jstring_to_cpp_string(env, jLabelPath);
  std::string cpuPowerMode = jstring_to_cpp_string(env, jCPUPowerMode);

  return reinterpret_cast<jlong>(
      new Pipeline(detModelPath, clsModelPath, recModelPath, cpuPowerMode,
                   cpuThreadNum, configPath, labelPath));
}

/*
 * Class:     com_baidu_paddle_lite_demo_ocr_db_crnn_Native
 * Method:    nativeRelease
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_buildsession_localtranslator_ocr_OcrManager_nativeRelease(JNIEnv *env,
                                                                 jclass thiz,
                                                                 jlong ctx) {
  if (ctx == 0) {
    return JNI_FALSE;
  }
  Pipeline *pipeline = reinterpret_cast<Pipeline *>(ctx);
  delete pipeline;
  return JNI_TRUE;
}

/*
 * Class:     com_baidu_paddle_lite_demo_ocr_db_crnn_Native
 * Method:    nativeProcess
 * Signature: (JIIIILjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_buildsession_localtranslator_ocr_OcrManager_nativeProcess(
    JNIEnv *env, jclass thiz, jlong ctx, jint inTextureId, jint outTextureId,
    jint textureWidth, jint textureHeight, jstring jsavedImagePath) {
  if (ctx == 0) {
    return JNI_FALSE;
  }
  std::string savedImagePath = jstring_to_cpp_string(env, jsavedImagePath);
  Pipeline *pipeline = reinterpret_cast<Pipeline *>(ctx);
  return pipeline->Process_val(inTextureId, outTextureId, textureWidth,
                               textureHeight, savedImagePath);
}

JNIEXPORT jobjectArray JNICALL
Java_com_buildsession_localtranslator_ocr_OcrManager_nativeDetectBitmap(
    JNIEnv *env, jclass thiz, jlong ctx, jobject jbitmap) {
  if (ctx == 0 || jbitmap == nullptr) {
    return nullptr;
  }

  Pipeline *pipeline = reinterpret_cast<Pipeline *>(ctx);

  AndroidBitmapInfo info;
  if (AndroidBitmap_getInfo(env, jbitmap, &info) < 0) {
    return nullptr;
  }

  if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
    return nullptr;
  }

  void *pixels;
  if (AndroidBitmap_lockPixels(env, jbitmap, &pixels) < 0) {
    return nullptr;
  }

  cv::Mat rgbaImage(info.height, info.width, CV_8UC4, pixels, info.stride);
  
  std::vector<OCRPredictResult> results;
  try {
      results = pipeline->Predict(rgbaImage);
  } catch (const std::exception& e) {
      LOGE("OCR Predict Exception: %s", e.what());
      AndroidBitmap_unlockPixels(env, jbitmap);
      return nullptr;
  } catch (...) {
      LOGE("OCR Predict Unknown Exception");
      AndroidBitmap_unlockPixels(env, jbitmap);
      return nullptr;
  }

  AndroidBitmap_unlockPixels(env, jbitmap);

  // Prepare Java classes
  jclass ocrResultClass =
      env->FindClass("com/buildsession/localtranslator/ocr/OcrResult");
  jmethodID ocrResultInit = env->GetMethodID(
      ocrResultClass, "<init>", "(Ljava/lang/String;FLjava/util/List;)V");
  jclass pointClass = env->FindClass("android/graphics/Point");
  jmethodID pointInit = env->GetMethodID(pointClass, "<init>", "(II)V");
  jclass arrayListClass = env->FindClass("java/util/ArrayList");
  jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
  jmethodID arrayListAdd =
      env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

  jobjectArray jResults =
      env->NewObjectArray(results.size(), ocrResultClass, nullptr);

  for (size_t i = 0; i < results.size(); ++i) {
    // Create points list
    jobject jPointsList = env->NewObject(arrayListClass, arrayListInit);
    for (const auto &pt : results[i].box) {
      jobject jPoint = env->NewObject(pointClass, pointInit, pt[0], pt[1]);
      env->CallBooleanMethod(jPointsList, arrayListAdd, jPoint);
      env->DeleteLocalRef(jPoint);
    }

    jstring jText = cpp_string_to_jstring(env, results[i].text);
    jobject jOcrResult = env->NewObject(ocrResultClass, ocrResultInit, jText,
                                        results[i].score, jPointsList);

    env->SetObjectArrayElement(jResults, i, jOcrResult);

    env->DeleteLocalRef(jText);
    env->DeleteLocalRef(jPointsList);
    env->DeleteLocalRef(jOcrResult);
  }

  return jResults;
}

#ifdef __cplusplus
}
#endif
