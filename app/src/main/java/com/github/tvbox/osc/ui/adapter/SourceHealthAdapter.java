package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.SourceHealthResult;

import java.util.ArrayList;

public class SourceHealthAdapter extends BaseQuickAdapter<SourceHealthResult, BaseViewHolder> {
    public SourceHealthAdapter() {
        super(R.layout.item_source_health, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, SourceHealthResult item) {
        helper.setText(R.id.tvSourceName, item.source.getName());
        helper.setText(R.id.tvSourceType, getTypeName(item.source.getType()));
        helper.setText(R.id.tvSearchState, mContext.getString(
                item.searchEnabled ? R.string.source_search_enabled : R.string.source_search_disabled));

        String status;
        int color;
        if (item.state == SourceHealthResult.TESTING) {
            status = mContext.getString(R.string.source_health_testing);
            color = Color.rgb(255, 193, 7);
        } else if (item.state == SourceHealthResult.AVAILABLE) {
            status = mContext.getString(R.string.source_health_available, item.elapsedMillis);
            color = Color.rgb(76, 175, 80);
        } else if (item.state == SourceHealthResult.FAILED) {
            status = item.detail;
            color = Color.rgb(255, 107, 107);
        } else {
            status = mContext.getString(R.string.source_health_not_tested);
            color = Color.LTGRAY;
        }
        helper.setText(R.id.tvSourceStatus, status);
        helper.setTextColor(R.id.tvSourceStatus, color);
        helper.itemView.setAlpha(item.searchEnabled ? 1.0f : 0.58f);
    }

    private String getTypeName(int type) {
        switch (type) {
            case 0:
                return "XML";
            case 1:
                return "JSON";
            case 3:
                return "Spider";
            case 4:
                return "JSON v4";
            default:
                return "Type " + type;
        }
    }
}
