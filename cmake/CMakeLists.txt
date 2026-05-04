cmake_minimum_required(VERSION 3.22.1)

project("ProxyMaxNative")

set(CMAKE_CXX_STANDARD 17)

# 预编译头文件
set(CMAKE_PCH_INSTANTIATE_TEMPLATES ON)

# Mihomo 桥接
add_library(mihomo_bridge SHARED cpp/mihomo/mihomo_bridge.cpp)
target_link_libraries(mihomo_bridge log android)

# Xray 桥接
add_library(v2ray_bridge SHARED cpp/xray/v2ray_bridge.cpp)
target_link_libraries(v2ray_bridge log android)

# sing-box 桥接
add_library(singbox_bridge SHARED cpp/singbox/singbox_bridge.cpp)
target_link_libraries(singbox_bridge log android)
