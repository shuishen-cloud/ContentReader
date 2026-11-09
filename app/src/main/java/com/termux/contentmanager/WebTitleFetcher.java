package com.termux.contentmanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 用隐藏 WebView 抓网页标题。
 *
 * 为什么必须用 WebView：
 *   知乎 / 部分站点有 JS 反爬挑战（如 zh-zse-ck），纯 HTTP 请求只会得到 403 挑战页。
 *   WebView 会真实执行 JS、拿到 Cookie 并渲染出页面，之后 document.title 就是正确标题。
 *   顺带还能跟随重定向，把 b23.tv 之类的短链解析成真实 URL。
 *
 * 必须在主线程调用（WebView 限制）。
 */
public final class WebTitleFetcher {

    public interface Callback {
        /**
         * @param title       页面标题，失败为 null
         * @param finalUrl    重定向后的最终 URL，未知则返回传入的 url
         */
        void onResult(String title, String finalUrl);
    }

    private static final long TIMEOUT_MS = 20000L;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView webView;
    private boolean done;

    private WebTitleFetcher(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** 主线程调用。 */
    public static void fetch(Context ctx, String url, Callback cb) {
        new WebTitleFetcher(ctx).start(url, cb);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void start(final String url, final Callback cb) {
        try {
            webView = new WebView(ctx);
        } catch (Throwable t) {
            cb.onResult(null, url);
            return;
        }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(false);   // 不加载图片，省流量
        s.setBlockNetworkImage(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        // 超时保护
        main.postDelayed(() -> finish(null, url, cb), TIMEOUT_MS);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                // 保持在应用内加载，跟随后续跳转
                return false;
            }

            @Override
            public void onPageFinished(WebView v, String pageUrl) {
                // 页面加载完，等一下让 JS 挑战把内容渲染出来，再取标题
                main.postDelayed(() -> readTitle(v, pageUrl, cb), 1200);
            }
        });

        try {
            webView.loadUrl(url);
        } catch (Throwable t) {
            finish(null, url, cb);
        }
    }

    private void readTitle(final WebView v, final String pageUrl, final Callback cb) {
        if (done) return;
        try {
            v.evaluateJavascript(
                    "(function(){"
                            + "var t=document.title||'';"
                            + "if(!t){"
                            + "  var m=document.querySelector('meta[property=\"og:title\"]');"
                            + "  if(m) t=m.getAttribute('content')||'';"
                            + "}"
                            + "return t;"
                            + "})()",
                    value -> {
                        String title = unquote(value);
                        if (title != null && !title.trim().isEmpty() && !looksLikeChallenge(title)) {
                            finish(title, v.getUrl() == null ? pageUrl : v.getUrl(), cb);
                        } else {
                            // 可能是 JS 挑战还没过，再等一轮
                            main.postDelayed(() -> {
                                if (done) return;
                                v.evaluateJavascript("document.title || ''", v2 -> {
                                    String t2 = unquote(v2);
                                    if (t2 != null && !t2.trim().isEmpty()
                                            && !looksLikeChallenge(t2)) {
                                        finish(t2, v.getUrl() == null ? pageUrl : v.getUrl(), cb);
                                    } else {
                                        finish(null, v.getUrl() == null ? pageUrl : v.getUrl(), cb);
                                    }
                                });
                            }, 2500);
                        }
                    });
        } catch (Throwable t) {
            finish(null, pageUrl, cb);
        }
    }

    /** evaluateJavascript 返回的是 JSON 字符串（带引号且转义），需要还原。 */
    private static String unquote(String json) {
        if (json == null) return null;
        String s = json.trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    char n = s.charAt(++i);
                    switch (n) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'u':
                            if (i + 4 < s.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(
                                            s.substring(i + 1, i + 5), 16));
                                    i += 4;
                                } catch (Exception e) {
                                    sb.append(n);
                                }
                            }
                            break;
                        default: sb.append(n);
                    }
                } else {
                    sb.append(c);
                }
            }
            s = sb.toString();
        }
        s = s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? null : s;
    }

    /** 判断是不是反爬挑战页的标题。 */
    private static boolean looksLikeChallenge(String t) {
        String s = t.trim();
        if (s.isEmpty()) return true;
        return s.contains("安全验证")
                || s.contains("请稍候")
                || s.contains("Just a moment")
                || s.equals("知乎，让每一次点击都充满意义 —— 欢迎来到知乎，发现问题背后的世界。");
    }

    private void finish(String title, String finalUrl, Callback cb) {
        if (done) return;
        done = true;
        main.removeCallbacksAndMessages(null);
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
            } catch (Throwable ignored) {
            }
            webView = null;
        }
        cb.onResult(title == null ? null : FetchTitle.clean(title), finalUrl);
    }
}
