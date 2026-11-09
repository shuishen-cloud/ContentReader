package com.termux.contentmanager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * 抓标题的统一入口：先 HTTP（快），失败再上 WebView（能过 JS 反爬）。
 *
 * 策略：
 *   1. HttpURLConnection 抓 <title>      —— 快（<1s），B站等普通站点够用
 *   2. 失败 → 隐藏 WebView 渲染后取 title —— 慢（3~8s），能过知乎 zh-zse-ck 挑战
 *
 * 顺带：WebView 跟随重定向后可拿到真实 URL，用于解析 b23.tv 之类的短链。
 */
public final class TitleResolver {

    public interface Callback {
        /** @param title 标题，失败为 null；@param finalUrl 重定向后的真实 URL */
        void onResult(String title, String finalUrl);
    }

    private final Context appCtx;
    private final Handler main = new Handler(Looper.getMainLooper());

    private TitleResolver(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
    }

    /** 任意线程可调，结果回主线程。 */
    public static void resolve(Context ctx, final String url, final Callback cb) {
        final TitleResolver r = new TitleResolver(ctx);
        r.main.post(() -> r.step1(url, cb));
    }

    /** 第一步：普通 HTTP 抓取（后台线程，避免阻塞主线程）。 */
    private void step1(final String url, final Callback cb) {
        new Thread(() -> {
            String t = null;
            try {
                t = FetchTitle.fetch(url);
            } catch (Throwable ignored) {
            }
            final String httpTitle = t;
            main.post(() -> {
                if (httpTitle != null && !httpTitle.trim().isEmpty()) {
                    cb.onResult(httpTitle, url);
                } else {
                    step2(url, cb);   // 交给 WebView
                }
            });
        }, "title-http").start();
    }

    /** 第二步：WebView 兜底。必须在主线程。 */
    private void step2(final String url, final Callback cb) {
        try {
            WebTitleFetcher.fetch(appCtx, url, cb::onResult);
        } catch (Throwable t) {
            cb.onResult(null, url);
        }
    }
}
