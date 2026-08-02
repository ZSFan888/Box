package com.github.tvbox.osc.util;

import java.net.URI;
import java.util.List;

public final class ReleaseAssetSelector {
    private static final String REPOSITORY_PATH = "/ZSFan888/Box/releases/download/";

    private ReleaseAssetSelector() {
    }

    public static String expectedAssetName(String abi, String brand, String mode) {
        return "TVBox_release-" + abi + "-" + brand + "-" + mode + ".apk";
    }

    public static String selectAssetName(List<String> assetNames, String abi, String brand, String mode) {
        String expected = expectedAssetName(abi, brand, mode);
        if (assetNames == null) return null;
        for (String name : assetNames) {
            if (expected.equals(name)) return name;
        }
        return null;
    }

    public static int releaseVersionCode(String tagName) {
        if (tagName == null) return -1;
        int separator = tagName.lastIndexOf('.');
        if (separator < 0 || separator == tagName.length() - 1) return -1;
        try {
            return Integer.parseInt(tagName.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static boolean isTrustedDownloadUrl(String url) {
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith(REPOSITORY_PATH)
                    && uri.getPath().endsWith(".apk");
        } catch (Exception ignored) {
            return false;
        }
    }
}
