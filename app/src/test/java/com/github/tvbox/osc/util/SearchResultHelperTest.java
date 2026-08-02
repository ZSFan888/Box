package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.Movie;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchResultHelperTest {
    @Test
    public void groupKeyIgnoresCaseSpacingAndPunctuation() {
        Movie.Video first = video("The Movie: Part 1", 2024, "source-a", "1");
        Movie.Video second = video("the movie part 1", 2024, "source-b", "2");

        assertEquals(SearchResultHelper.groupKey(first), SearchResultHelper.groupKey(second));
    }

    @Test
    public void groupKeyKeepsDifferentYearsSeparate() {
        Movie.Video first = video("Movie", 2023, "source-a", "1");
        Movie.Video second = video("Movie", 2024, "source-b", "2");

        assertFalse(SearchResultHelper.groupKey(first).equals(SearchResultHelper.groupKey(second)));
    }

    @Test
    public void sameResultUsesSourceAndId() {
        Movie.Video first = video("Movie", 2024, "source-a", "1");
        assertTrue(SearchResultHelper.sameResult(first, video("Other name", 2020, "source-a", "1")));
        assertFalse(SearchResultHelper.sameResult(first, video("Movie", 2024, "source-b", "1")));
    }

    private Movie.Video video(String name, int year, String source, String id) {
        Movie.Video video = new Movie.Video();
        video.name = name;
        video.year = year;
        video.sourceKey = source;
        video.id = id;
        return video;
    }
}
