package com.github.tvbox.osc.util;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;

public class IoUtilsTest {
    @Test
    public void readFullyHandlesPartialReads() throws IOException {
        byte[] expected = "a complete response split across reads".getBytes(StandardCharsets.UTF_8);
        InputStream input = new ByteArrayInputStream(expected) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(length, 3));
            }
        };

        assertArrayEquals(expected, IoUtils.readFully(input));
    }

    @Test
    public void copyHandlesEmptyIntermediateReads() throws IOException {
        byte[] expected = "stream data".getBytes(StandardCharsets.UTF_8);
        InputStream input = new ByteArrayInputStream(expected) {
            private boolean returnedEmptyRead;

            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                if (!returnedEmptyRead) {
                    returnedEmptyRead = true;
                    return 0;
                }
                return super.read(buffer, offset, length);
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        IoUtils.copy(input, output);

        assertArrayEquals(expected, output.toByteArray());
    }
}
