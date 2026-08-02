package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.Movie;

import java.util.Locale;

public final class SearchResultHelper {
    private SearchResultHelper() {
    }

    public static String groupKey(Movie.Video video) {
        String name = video == null || video.name == null ? "" : video.name;
        String normalized = name.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
        int year = video == null ? 0 : video.year;
        return normalized + "#" + year;
    }

    public static boolean sameResult(Movie.Video first, Movie.Video second) {
        if (first == null || second == null) return false;
        return equals(first.sourceKey, second.sourceKey) && equals(first.id, second.id);
    }

    private static boolean equals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}
