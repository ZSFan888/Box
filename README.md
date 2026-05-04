# Box - Proxy App Builder

[![Build APK](https://github.com/ZSFan888/Box/actions/workflows/build.yml/badge.svg)](https://github.com/ZSFan888/Box/actions/workflows/build.yml)

## 简介
Box 是一个基于 GitHub Actions 的 Android 代理应用自动构建项目。

## 特性
- 🚀 纯 GitHub Actions 构建，无需本地环境
- 📦 预构建原生库，极低内存占用
- 🔧 禁用 R8 混淆，构建稳定快速
- 🤖 支持 `workflow_dispatch` 手动触发构建

## 使用方法

### 自动触发
每次 `push` 到 `main` 分支时，Actions 自动构建 APK。

### 手动触发
1. 进入 GitHub 仓库 **Actions** 页面
2. 选择 **Build APK** 工作流
3. 点击 **Run workflow** 手动触发

### 下载 APK
1. 构建完成后，进入 **Actions -> 最新构建**
2. 在 **Artifacts** 区域下载 `APK.zip`

## 构建配置
| 项目 | 配置 |
|------|------|
| JDK | 17 (Temurin) |
| compileSdk | 34 |
| minSdk | 26 |
| R8 混淆 | 关闭 |
| Gradle Daemon | 关闭 |
| JVM 内存 | 最大 512m |

## 项目结构
```
Box/
├── .github/workflows/build.yml   # CI 构建配置
├── app/
│   ├── src/main/jniLibs/         # 预构建原生库
│   └── build.gradle.kts          # 应用构建脚本
└── scripts/
    └── place_libs.sh             # 原生库下载脚本
```

## License
MIT
