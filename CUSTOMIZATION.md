# 快捷自定义打包

打开仓库的 **Actions > Build and Release APKs > Run workflow**，填写以下参数：

- `app_name`：安装后显示的应用名称，最多 30 个字符。
- `version_prefix`：版本前缀，例如 `1.0`、`2.5` 或 `2.5.1`。
- `icon_url`：可选，直接指向 PNG、JPEG 或 WebP 图片的 URL。
- `banner_url`：可选，直接指向 PNG、JPEG 或 WebP 图片的 URL。

图片 URL 必须直接返回图片文件内容，不能是包含图片的网页地址。图片会自动居中裁剪并缩放：应用图标输出为 `512x512` PNG，电视横幅输出为 `640x360` PNG。单张图片不能超过 10 MB。留空时使用仓库中的现有图片：

- `app/src/main/res/drawable/app_icon.png`
- `app/src/main/res/drawable/app_banner.png`

每次运行会自动使用递增的 GitHub Actions 运行号作为 Android `versionCode`，并创建唯一的 GitHub Release。例如版本前缀为 `2.5`，第 12 次运行会创建标签 `v2.5.12`。

固定签名密钥保持不变，因此从固定签名版本开始，后续生成的 APK 可以覆盖升级。
