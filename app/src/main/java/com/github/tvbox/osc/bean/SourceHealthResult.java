package com.github.tvbox.osc.bean;

public class SourceHealthResult {
    public static final int IDLE = 0;
    public static final int TESTING = 1;
    public static final int AVAILABLE = 2;
    public static final int FAILED = 3;

    public final SourceBean source;
    public int state = IDLE;
    public long elapsedMillis;
    public String detail = "";
    public boolean searchEnabled;

    public SourceHealthResult(SourceBean source, boolean searchEnabled) {
        this.source = source;
        this.searchEnabled = searchEnabled;
    }
}
