package com.github.tvbox.osc.util;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReleaseAssetSelectorTest {
    @Test
    public void selectsOnlyExactVariant() {
        String selected = ReleaseAssetSelector.selectAssetName(Arrays.asList(
                "TVBox_release-arm64-generic-python.apk",
                "TVBox_release-arm64-generic-java.apk",
                "TVBox_release-armeabi-generic-java.apk"), "arm64", "generic", "java");
        assertEquals("TVBox_release-arm64-generic-java.apk", selected);
        assertNull(ReleaseAssetSelector.selectAssetName(Arrays.asList(
                "TVBox_release-arm64-generic-python.apk"), "arm64", "generic", "java"));
    }

    @Test
    public void parsesWorkflowRunNumberFromTag() {
        assertEquals(17, ReleaseAssetSelector.releaseVersionCode("v1.1.17"));
        assertEquals(306, ReleaseAssetSelector.releaseVersionCode("v2.5.1.306"));
        assertEquals(-1, ReleaseAssetSelector.releaseVersionCode("latest"));
    }

    @Test
    public void acceptsOnlyExpectedRepositoryApkUrls() {
        assertTrue(ReleaseAssetSelector.isTrustedDownloadUrl(
                "https://github.com/ZSFan888/Box/releases/download/v1.2.18/TVBox_release-arm64-generic-java.apk"));
        assertFalse(ReleaseAssetSelector.isTrustedDownloadUrl(
                "http://github.com/ZSFan888/Box/releases/download/v1.2.18/a.apk"));
        assertFalse(ReleaseAssetSelector.isTrustedDownloadUrl(
                "https://github.com/other/Box/releases/download/v1/a.apk"));
        assertFalse(ReleaseAssetSelector.isTrustedDownloadUrl(
                "https://github.com/ZSFan888/Box/releases/download/v1/a.zip"));
    }
}
