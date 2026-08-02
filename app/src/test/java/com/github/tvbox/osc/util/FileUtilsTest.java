package com.github.tvbox.osc.util;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writeSimpleReplacesFileWithCompleteContent() throws Exception {
        File destination = temporaryFolder.newFile("cache-entry");
        assertTrue(FileUtils.writeSimple("old".getBytes(StandardCharsets.UTF_8), destination));

        byte[] expected = new byte[24_000];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i % 251);
        }

        assertTrue(FileUtils.writeSimple(expected, destination));
        assertArrayEquals(expected, FileUtils.readSimple(destination));
    }

    @Test
    public void cacheExpiresAtBoundary() {
        assertFalse(FileUtils.isCacheExpired(101, 100));
        assertTrue(FileUtils.isCacheExpired(100, 100));
        assertTrue(FileUtils.isCacheExpired(99, 100));
    }
}
