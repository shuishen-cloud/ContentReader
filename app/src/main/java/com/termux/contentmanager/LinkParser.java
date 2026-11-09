package com.termux.contentmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从分享文本里抽取链接。
 * 知乎： 标题 - 知乎 https://www.zhihu.com/question/xxx/answer/yyy
 * B站：  【标题】 https://b23.tv/xxxx
 */
public final class LinkParser {

    // 匹配 http(s)://... ，遇到空白/中文标点/引号停止
    private static final Pattern URL = Pattern.compile(
            "https?://[^\\s\"'<>()\\[\\]{}，。、；：？！（）【】《》「」]+",
            Pattern.CASE_INSENSITIVE);

    private LinkParser() {}

    /** 抽取文本中全部链接（去重、保序）。没有链接返回空列表。 */
    public static List<String> extract(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = URL.matcher(text);
        while (m.find()) {
            String u = stripTrailing(m.group());
            if (!u.isEmpty() && !out.contains(u)) out.add(u);
        }
        return out;
    }

    /** 去掉 URL 末尾可能被误捕获的标点。 */
    private static String stripTrailing(String url) {
        int end = url.length();
        while (end > 0 && ".,;:!?、。，；：！？".indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }

    /** 推测来源：zhihu / bilibili / 其他 */
    public static String guessSource(String url) {
        String u = url.toLowerCase();
        if (u.contains("zhihu.com") || u.contains("zhimg.com")) return "zhihu";
        if (u.contains("bilibili.com") || u.contains("b23.tv")) return "bilibili";
        return "other";
    }

    /**
     * 从分享文案里推标题。
     * 返回 null 表示文案里没有可用标题（例如知乎只发裸链接），
     * 调用方应改用 FetchTitle 联网抓取。
     */
    public static String guessTitle(String rawText, String url) {        if (rawText != null) {
            int idx = rawText.indexOf(url);
            if (idx > 0) {
                String before = rawText.substring(0, idx).trim();
                before = before.replaceAll("^[\\s\\-–—|:：,，]+", "")
                               .replaceAll("[\\s\\-–—|:：,，]+$", "");
                if (!before.isEmpty()) {
                    return before.length() > 80 ? before.substring(0, 80) + "…" : before;
                }
            }
            // 链接在后面没文案，尝试取链接之后的
            if (idx >= 0) {
                String after = rawText.substring(idx + url.length()).trim();
                if (!after.isEmpty()) {
                    return after.length() > 80 ? after.substring(0, 80) + "…" : after;
                }
            }
        }
        return null;
    }
}
