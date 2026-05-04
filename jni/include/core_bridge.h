#ifndef PROXYMAX_CORE_BRIDGE_H
#define PROXYMAX_CORE_BRIDGE_H

#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "ProxyMaxNative"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 统一接口，所有核心必须实现
class CoreBridge {
public:
    virtual ~CoreBridge() = default;
    virtual int  start(const char* config_path, int tun_fd) = 0;
    virtual int  stop() = 0;
    virtual int  reload(const char* config_path) = 0;
    virtual void get_traffic(long* up, long* down, long* total_up, long* total_down, int* conns) = 0;
    virtual void set_log_callback(void (*cb)(const char*)) = 0;
    virtual const char* version() = 0;
};

// 工厂函数
extern "C" {
    CoreBridge* create_core(const char* type);
    void        destroy_core(CoreBridge* core);
}
#endif
