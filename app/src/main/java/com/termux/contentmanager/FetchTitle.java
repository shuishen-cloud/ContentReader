package com.termux.contentmanager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 联网抓网页标题。零依赖：HttpURLConnection + 正则。
 * 必须放到后台线程调用（下面用 Thread 包了，见 fetchAsync）。
 */
public final class FetchTitle {

    private static final Pattern TITLE = Pattern.compile(
            "<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern CHARSET = Pattern.compile(
            "charset=[\"']?([\\w-]+)",
            Pattern.CASE_INSENSITIVE);

    /** 常见的中文 HTML 实体，够用了 */
    private static final String[][] ENTITIES = {
            {"&quot;", "\""}, {"&apos;", "'"}, {"&lt;", "<"}, {"&gt;", ">"},
            {"&amp;", "&"}, {"&nbsp;", " "}, {"&#39;", "'"},
            {"&ldquo;", "“"}, {"&rdquo;", "”"}, {"&mdash;", "—"},
            {"&hellip;", "…"}, {"&middot;", "·"},
    };

    public interface Callback {
        /** title 为 null 表示抓取失败 */
        void onResult(String title);
    }

    private FetchTitle() {}

    /** 后台线程抓标题，结果回主线程由调用方处理（这里是回调直接返回，需自行切线程）。 */
    public static void fetchAsync(final String url, final Callback cb) {
        new Thread(() -> {
            String t = null;
            try {
                t = fetch(url);
            } catch (Throwable ignored) {
            }
            cb.onResult(t);
        }, "fetch-title").start();
    }

    /** 同步抓取，返回清洗后的标题，失败返回 null。 */
    public static String fetch(String url) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("Accept-Encoding", "gzip");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) return null;

            String body = readBody(conn, url);
            if (body == null) return null;

            Matcher m = TITLE.matcher(body);
            if (!m.find()) return null;

            String title = clean(m.group(1));
            if (title.isEmpty()) return null;
            return title.length() > 120 ? title.substring(0, 120) + "…" : title;
        } catch (Throwable e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn, String url) throws Exception {
        InputStream raw = conn.getInputStream();
        String enc = conn.getContentEncoding();
        InputStream in = (enc != null && enc.toLowerCase().contains("gzip"))
                ? new GZIPInputStream(raw) : raw;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > 512 * 1024) break; // 只读前 512KB，够找到 <title>
            bos.write(buf, 0, n);
            // 提前结束：已经出现 </title>
            if (total > 4096) {
                String probe = new String(bos.toByteArray(), Charset.forName("ISO-8859-1"));
                if (probe.contains("</title>")) break;
            }
        }
        in.close();

        byte[] bytes = bos.toByteArray();

        // 先按 header 猜编码
        String charset = null;
        String ct = conn.getContentType();
        if (ct != null) {
            Matcher mc = CHARSET.matcher(ct);
            if (mc.find()) charset = mc.group(1);
        }
        // header 没写就看 HTML 里的 meta charset
        if (charset == null) {
            String head = new String(bytes, Charset.forName("ISO-8859-1"));
            Matcher mc = CHARSET.matcher(head);
            if (mc.find()) charset = mc.group(1);
        }
        if (charset == null) charset = guessByHost(url);

        try {
            return new String(bytes, Charset.forName(charset));
        } catch (Exception e) {
            return new String(bytes, Charset.forName("UTF-8"));
        }
    }

    /** 已知站点默认 UTF-8；其余按 UTF-8 试 */
    private static String guessByHost(String url) {
        return "UTF-8";
    }

    /** 去 HTML 实体、多余空白、常见站点后缀 */
    static String clean(String s) {
        if (s == null) return "";
        String r = s;
        for (String[] e : ENTITIES) {
            r = r.replace(e[0], e[1]);
        }
        r = r.replaceAll("<[^>]+>", "");
        r = r.replaceAll("\\s+", " ").trim();
        // 去掉知乎/B站的尾巴
        r = r.replaceAll("\\s*[-–—|_]\\s*知乎\\s*$", "");
        r = r.replaceAll("\\s*[-–—|_]\\s*哔哩哔哩.*$", "");
        r = r.replaceAll("^知乎\\s*[-–—|_]\\s*", "");
        return r.trim();
    }
}
