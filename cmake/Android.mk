LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libmihomo
LOCAL_SRC_FILES := cpp/mihomo/mihomo_bridge.cpp
LOCAL_LDLIBS := -llog -landroid
LOCAL_CFLAGS := -std=c++17
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libv2ray
LOCAL_SRC_FILES := cpp/xray/v2ray_bridge.cpp
LOCAL_LDLIBS := -llog -landroid
LOCAL_CFLAGS := -std=c++17
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libsingbox
LOCAL_SRC_FILES := cpp/singbox/singbox_bridge.cpp
LOCAL_LDLIBS := -llog -landroid
LOCAL_CFLAGS := -std=c++17
include $(BUILD_SHARED_LIBRARY)
