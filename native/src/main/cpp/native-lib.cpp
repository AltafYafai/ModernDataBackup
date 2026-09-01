#include <jni.h>
#include <string>
extern "C" JNIEXPORT jstring JNICALL Java_com_xayah_modernnative_ZstdWrapper_stringFromJNI(JNIEnv* env, jobject) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C" JNIEXPORT jint JNICALL Java_com_xayah_modernnative_ZstdWrapper_compress(JNIEnv* env, jobject, jbyteArray src) {
    return 0;
}
