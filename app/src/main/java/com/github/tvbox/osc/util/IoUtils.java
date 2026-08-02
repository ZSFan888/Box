package com.github.tvbox.osc.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class IoUtils {
    private static final int BUFFER_SIZE = 8192;

    private IoUtils() {
    }

    static byte[] readFully(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) {
                int value = input.read();
                if (value == -1) {
                    break;
                }
                output.write(value);
            } else {
                output.write(buffer, 0, count);
            }
        }
    }
}
