package com.github.tvbox.osc.event;

import com.github.tvbox.osc.bean.AbsXml;

public class SearchResultEvent {
    public final long requestId;
    public final String sourceKey;
    public final AbsXml data;

    public SearchResultEvent(long requestId, String sourceKey, AbsXml data) {
        this.requestId = requestId;
        this.sourceKey = sourceKey;
        this.data = data;
    }
}
