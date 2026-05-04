#include "../core_bridge.h"

#include <string>

namespace singbox {

class SingboxCore : public CoreBridge {
public:
    int start(const char* config_path, int tun_fd) override {
        LOGI("singbox_core_stub: start
");
        return 0;
    }

    int stop() override {
        LOGI("singbox_core_stub: stop
");
        return 0;
    }

    int reload(const char* config_path) override {
        LOGI("singbox_core_stub: reload
");
        return 0;
    }

    void get_traffic(long* up, long* down, long* total_up, long* total_down, int* conns) override {
        *up        = 1500000;
        *down      = 1300000;
        *total_up  = 3000000;
        *total_down= 2500000;
        *conns     = 20;
    }

    void set_log_callback(void (*cb)(const char*)) override {
        LOGI("singbox_core_stub: set_log_callback
");
    }

    const char* version() override {
        return "singbox-stub 1.0.0";
    }
};

static SingboxCore singbox_core;

} // namespace singbox

extern "C" {

JNIEXPORT jint JNICALL
Java_com_proxymax_core_SingboxEngine_nativeStart(
        JNIEnv* env, jclass clazz,
        jstring config_,
        jint tun_fd)
{
    const char* config = env->GetStringUTFChars(config_, nullptr);
    singbox::SingboxCore* core = reinterpret_cast<singbox::SingboxCore*>(create_core("singbox"));
    if (!core) return -1;
    int ret = core->start(config, tun_fd);
    env->ReleaseStringUTFChars(config_, config);
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_proxymax_core_SingboxEngine_nativeStop(
        JNIEnv* env, jclass clazz)
{
    singbox::SingboxCore* core = reinterpret_cast<singbox::SingboxCore*>(create_core("singbox"));
    if (core) return core->stop();
    else return -1;
}

JNIEXPORT void JNICALL
Java_com_proxymax_core_SingboxEngine_nativeQueryStats(
        JNIEnv* env, jclass clazz,
        jstring tag)
{
    LOGI("singbox_core_stub: queryStats
");
}

JNIEXPORT void JNICALL
Java_com_proxymax_core_SingboxEngine_nativeSetLogCallback(
        JNIEnv* env, jclass clazz,
        jobject cbObj)
{
    jclass clazz_cb = env->GetObjectClass(cbObj);
    jmethodID mid = env->GetMethodID(clazz_cb, "invoke", "(Ljava/lang/String;)V");
    LOGI("singbox_core_stub: set_log_callback
");
}

JNIEXPORT jstring JNICALL
Java_com_proxymax_core_SingboxEngine_nativeVersion(
        JNIEnv* env, jclass clazz)
{
    singbox::SingboxCore* core = reinterpret_cast<singbox::SingboxCore*>(create_core("singbox"));
    if (!core) return nullptr;
    return env->NewStringUTF(core->version());
}

} // extern "C"

CoreBridge* create_core(const char* type) {
    if (std::string(type) == "singbox") {
        return &singbox::singbox_core;
    }
    return nullptr;
}

void destroy_core(CoreBridge* core) {
    // 单例，不销毁
}
