package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.SourceHealthResult;
import com.github.tvbox.osc.ui.adapter.SourceHealthAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.SearchHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SourceHealthDialog extends BaseDialog {
    private static final long CHECK_TIMEOUT_MILLIS = 12_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SourceHealthResult> results = new ArrayList<>();
    private final SourceHealthAdapter adapter = new SourceHealthAdapter();
    private final AtomicInteger remaining = new AtomicInteger();
    private final TextView summary;
    private ExecutorService executor;
    private int generation;

    public SourceHealthDialog(@NonNull Context context) {
        super(context);
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setCanceledOnTouchOutside(false);
        setContentView(R.layout.dialog_source_health);

        summary = findViewById(R.id.tvHealthSummary);
        TvRecyclerView list = findViewById(R.id.mGridView);
        list.setLayoutManager(new V7LinearLayoutManager(context, 1, false));
        list.setAdapter(adapter);

        HashMap<String, String> enabledSources = SearchHelper.getSourcesForSearch();
        for (SourceBean source : ApiConfig.get().getSourceBeanList()) {
            if (!source.isSearchable()) {
                continue;
            }
            results.add(new SourceHealthResult(source,
                    enabledSources == null || enabledSources.containsKey(source.getKey())));
        }
        adapter.setNewData(results);
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter baseQuickAdapter, View view, int position) {
                SourceHealthResult result = results.get(position);
                result.searchEnabled = !result.searchEnabled;
                SearchHelper.putCheckedSource(result.source.getKey(), result.searchEnabled);
                adapter.notifyItemChanged(position);
                updateSummary();
            }
        });

        findViewById(R.id.btnStartHealthCheck).setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            startChecks();
        });
        findViewById(R.id.btnDisableFailed).setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            int disabled = 0;
            for (int i = 0; i < results.size(); i++) {
                SourceHealthResult result = results.get(i);
                if (result.state == SourceHealthResult.FAILED && result.searchEnabled) {
                    result.searchEnabled = false;
                    SearchHelper.putCheckedSource(result.source.getKey(), false);
                    adapter.notifyItemChanged(i);
                    disabled++;
                }
            }
            updateSummary();
            Toast.makeText(getContext(), getContext().getString(R.string.source_health_disabled_count, disabled), Toast.LENGTH_SHORT).show();
        });
        updateSummary();
    }

    private void startChecks() {
        stopChecks();
        generation++;
        int currentGeneration = generation;
        executor = Executors.newFixedThreadPool(4);
        remaining.set(results.size());
        for (SourceHealthResult result : results) {
            result.state = SourceHealthResult.TESTING;
            result.elapsedMillis = 0;
            result.detail = "";
        }
        adapter.notifyDataSetChanged();
        updateSummary();

        for (SourceHealthResult result : results) {
            executor.execute(() -> checkSource(result, currentGeneration));
        }
        handler.postDelayed(() -> finishTimedOutChecks(currentGeneration), CHECK_TIMEOUT_MILLIS);
    }

    private void checkSource(SourceHealthResult result, int currentGeneration) {
        long start = System.currentTimeMillis();
        boolean available = false;
        String detail = getContext().getString(R.string.source_health_failed);
        try {
            if (result.source.getType() == 3) {
                Spider spider = ApiConfig.get().getCSP(result.source);
                String content = spider.homeContent(false);
                available = !TextUtils.isEmpty(content);
            } else {
                HttpUrl baseUrl = HttpUrl.parse(result.source.getApi());
                if (baseUrl == null) {
                    detail = getContext().getString(R.string.source_health_invalid_url);
                } else {
                    HttpUrl.Builder url = baseUrl.newBuilder().addQueryParameter("wd", "1");
                    if (result.source.getType() == 1 || result.source.getType() == 4) {
                        url.addQueryParameter("ac", "detail");
                    }
                    OkHttpClient client = OkGoHelper.getDefaultClient().newBuilder()
                            .connectTimeout(8, TimeUnit.SECONDS)
                            .readTimeout(8, TimeUnit.SECONDS)
                            .callTimeout(10, TimeUnit.SECONDS)
                            .build();
                    Request request = new Request.Builder().url(url.build()).get().build();
                    try (Response response = client.newCall(request).execute()) {
                        available = response.isSuccessful();
                        if (!available) {
                            detail = "HTTP " + response.code();
                        }
                    }
                }
            }
        } catch (Throwable error) {
            String message = error.getMessage();
            if (!TextUtils.isEmpty(message)) {
                detail = message.length() > 36 ? message.substring(0, 36) : message;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        boolean finalAvailable = available;
        String finalDetail = detail;
        handler.post(() -> {
            if (generation != currentGeneration || result.state != SourceHealthResult.TESTING) {
                return;
            }
            result.state = finalAvailable ? SourceHealthResult.AVAILABLE : SourceHealthResult.FAILED;
            result.elapsedMillis = elapsed;
            result.detail = finalDetail;
            adapter.notifyItemChanged(results.indexOf(result));
            remaining.decrementAndGet();
            updateSummary();
        });
    }

    private void finishTimedOutChecks(int currentGeneration) {
        if (generation != currentGeneration) {
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            SourceHealthResult result = results.get(i);
            if (result.state == SourceHealthResult.TESTING) {
                result.state = SourceHealthResult.FAILED;
                result.detail = getContext().getString(R.string.source_health_timeout);
                adapter.notifyItemChanged(i);
            }
        }
        remaining.set(0);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        updateSummary();
    }

    private void updateSummary() {
        int available = 0;
        int failed = 0;
        int enabled = 0;
        for (SourceHealthResult result : results) {
            if (result.state == SourceHealthResult.AVAILABLE) available++;
            if (result.state == SourceHealthResult.FAILED) failed++;
            if (result.searchEnabled) enabled++;
        }
        summary.setText(getContext().getString(R.string.source_health_summary,
                results.size(), available, failed, enabled));
    }

    private void stopChecks() {
        handler.removeCallbacksAndMessages(null);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public void dismiss() {
        generation++;
        stopChecks();
        super.dismiss();
    }
}
