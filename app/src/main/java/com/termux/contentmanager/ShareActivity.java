package com.termux.contentmanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 接收其它 App 分享的文本，抽取其中的链接入库。
 * 处理完立即 finish，用户感知为"一个短暂的提示"。
 */
public class ShareActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        if (intent == null) { finish(); return; }

        String text = readText(intent);
        // 标题可能单独放在 SUBJECT / TITLE 里（部分 App 的分享习惯）
        String sharedTitle = readSharedTitle(intent);

        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "没有收到文本内容", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        List<String> urls = LinkParser.extract(text);
        if (urls.isEmpty()) {
            Toast.makeText(this, "没有找到链接", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        LinkStore store = LinkStore.get(this);
        int added = 0;
        int dup = 0;
        for (final String url : urls) {
            String title = LinkParser.guessTitle(text, url);
            if (title == null && sharedTitle != null) title = sharedTitle;
            long id = store.insert(url, title, LinkParser.guessSource(url));
            if (id == -1) {
                dup++;
            } else {
                added++;
                // 文案里没标题（如知乎只发裸链接）→ 后台抓网页标题
                maybeFetchTitle(store, id, url, title);
            }
        }

        String msg;
        if (added > 0) {
            msg = "已保存 " + added + " 条链接";
            if (dup > 0) msg += "（" + dup + " 条已存在）";
        } else {
            msg = "已经是保存过的链接";
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** 标题缺失时后台抓取（HTTP 优先，失败用 WebView）；顺带回写短链解析结果。 */
    private void maybeFetchTitle(final LinkStore store, final long id,
                                 final String url, String title) {
        if (title != null && !title.trim().isEmpty()) return;
        TitleResolver.resolve(this, url, (t, finalUrl) -> {
            if (t != null && !t.trim().isEmpty()) {
                store.updateTitle(id, t);
            }
            if (finalUrl != null && !finalUrl.isEmpty()
                    && !finalUrl.equals(url) && !finalUrl.startsWith("about:")) {
                store.updateUrlAndSource(id, finalUrl, LinkParser.guessSource(finalUrl));
            }
        });
    }

    /**
     * 依次尝试各个字段取出正文文本。
     * 知乎：只给 EXTRA_TEXT（裸链接）；部分 App 会额外给 HTML。
     */
    private static String readText(Intent intent) {
        String[] keys = {Intent.EXTRA_TEXT};
        for (String k : keys) {
            CharSequence cs = intent.getCharSequenceExtra(k);
            if (cs != null && cs.toString().trim().length() > 0) return cs.toString();
        }
        // ClipData（部分 App 只往这塞）
        ClipData cd = intent.getClipData();
        if (cd != null && cd.getItemCount() > 0) {
            ClipData.Item it = cd.getItemAt(0);
            if (it.getText() != null && it.getText().toString().trim().length() > 0) {
                return it.getText().toString();
            }
            if (it.getHtmlText() != null && it.getHtmlText().trim().length() > 0) {
                return it.getHtmlText();
            }
        }
        // 兜底：裸 URI
        if (intent.getDataString() != null) return intent.getDataString();
        return null;
    }

    /** 从 SUBJECT / TITLE / HTML_TEXT 里找标题，找不到返回 null。 */
    private static String readSharedTitle(Intent intent) {
        String[] candidates = {
                Intent.EXTRA_SUBJECT,
                Intent.EXTRA_TITLE,
                "android.intent.extra.TITLE",
        };
        for (String k : candidates) {
            CharSequence cs = intent.getCharSequenceExtra(k);
            if (cs != null) {
                String s = cs.toString().trim();
                if (!s.isEmpty()) return s;
            }
        }
        // HTML 里可能有 <title> 或 og:title
        CharSequence html = intent.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT);
        if (html != null) {
            String t = extractHtmlTitle(html.toString());
            if (t != null) return t;
        }
        ClipData cd = intent.getClipData();
        if (cd != null && cd.getItemCount() > 0) {
            String h = cd.getItemAt(0).getHtmlText();
            if (h != null) {
                String t = extractHtmlTitle(h);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static String extractHtmlTitle(String html) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<title[^>]*>(.*?)</title>",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                                | java.util.regex.Pattern.DOTALL)
                .matcher(html);
        if (m.find()) {
            String s = m.group(1).replaceAll("<[^>]+>", "").trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }
}
