package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;

import java.io.Serializable;
import java.util.Locale;

public final class WatchProgressManager {
    private static final String META_SUFFIX = "_watch_meta";

    private WatchProgressManager() {
    }

    public static void save(String progressKey, long position, long duration) {
        if (progressKey == null || progressKey.isEmpty() || position < 0) return;
        Meta meta = new Meta();
        meta.position = position == 0 && duration > 0 ? duration : position;
        meta.duration = Math.max(duration, 0);
        meta.updatedAt = System.currentTimeMillis();
        meta.completed = duration > 0 && (position == 0 || position >= duration * 0.95);
        CacheManager.save(metaKey(progressKey), meta);
    }

    public static Meta get(VodInfo info) {
        String progressKey = progressKey(info);
        if (progressKey == null) return new Meta();
        Object cached = CacheManager.getCache(metaKey(progressKey));
        Meta meta = cached instanceof Meta ? (Meta) cached : new Meta();
        Object legacyPosition = CacheManager.getCache(MD5.string2MD5(progressKey));
        if (legacyPosition instanceof Long && meta.position <= 0) {
            meta.position = (Long) legacyPosition;
        }
        if (meta.updatedAt <= 0) meta.updatedAt = info.recordTime;
        return meta;
    }

    public static void markComplete(VodInfo info) {
        String progressKey = progressKey(info);
        if (progressKey == null) return;
        Meta meta = get(info);
        meta.completed = true;
        meta.updatedAt = System.currentTimeMillis();
        if (meta.duration > 0) meta.position = meta.duration;
        CacheManager.save(metaKey(progressKey), meta);
    }

    public static void clear(String progressKey) {
        if (progressKey == null || progressKey.isEmpty()) return;
        CacheManager.delete(metaKey(progressKey), new Meta());
    }

    public static void remove(VodInfo info) {
        String progressKey = progressKey(info);
        if (progressKey == null) return;
        clear(progressKey);
        CacheManager.delete(MD5.string2MD5(progressKey), 0L);
    }

    public static String describe(VodInfo info) {
        Meta meta = get(info);
        String episode = info.playNote == null ? "" : info.playNote.trim();
        String status;
        if (meta.completed) {
            status = episode.isEmpty() ? "Watched" : episode + "  Watched";
            return appendTime(status, meta.updatedAt);
        }
        if (meta.duration > 0) {
            int percent = (int) Math.min(99, meta.position * 100 / meta.duration);
            status = episode.isEmpty() ? percent + "%" : episode + "  " + percent + "%";
            return appendTime(status, meta.updatedAt);
        }
        if (meta.position > 0) {
            long minutes = meta.position / 60000;
            status = episode.isEmpty() ? String.format(Locale.US, "%d min", minutes)
                    : String.format(Locale.US, "%s  %d min", episode, minutes);
            return appendTime(status, meta.updatedAt);
        }
        return appendTime(episode, meta.updatedAt);
    }

    public static int percent(VodInfo info) {
        Meta meta = get(info);
        if (meta.completed) return 100;
        if (meta.duration <= 0) return 0;
        return (int) Math.max(0, Math.min(99, meta.position * 100 / meta.duration));
    }

    public static String progressKey(VodInfo info) {
        if (info == null || info.sourceKey == null || info.id == null || info.playFlag == null) return null;
        return info.sourceKey + info.id + info.playFlag + info.getplayIndex();
    }

    private static String metaKey(String progressKey) {
        return MD5.string2MD5(progressKey + META_SUFFIX);
    }

    private static String appendTime(String status, long updatedAt) {
        if (updatedAt <= 0) return status;
        long elapsed = Math.max(0, System.currentTimeMillis() - updatedAt);
        String time;
        if (elapsed < 60 * 60 * 1000L) time = Math.max(1, elapsed / 60000) + "m ago";
        else if (elapsed < 24 * 60 * 60 * 1000L) time = elapsed / (60 * 60 * 1000L) + "h ago";
        else time = elapsed / (24 * 60 * 60 * 1000L) + "d ago";
        return status == null || status.isEmpty() ? time : status + " | " + time;
    }

    public static class Meta implements Serializable {
        private static final long serialVersionUID = 1L;
        public long position;
        public long duration;
        public long updatedAt;
        public boolean completed;
    }
}
