package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 条目详情：标题、链接、评分、标签、收藏夹、评论。
 * 所有改动即时写库，返回时列表会自动刷新（onResume）。
 */
public class LinkDetailActivity extends Activity {

    public static final String EXTRA_ID = "link_id";

    private LinkStore store;
    private LinkStore.Link link;

    private TextView titleView;
    private TextView urlView;
    private LinearLayout starRow;
    private TextView tagsView;
    private TextView collectionView;
    private EditText commentBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = LinkStore.get(this);

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (id <= 0) { finish(); return; }

        link = findById(id);
        if (link == null) {
            Toast.makeText(this, "条目已不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = UiKit.vertical(this);
        int p = UiKit.dp(this, 16);
        root.setPadding(p, p, p, p);

        // ---------- 标题
        titleView = UiKit.text(this, displayTitle(link), 18f);
        titleView.setOnClickListener(v -> editTitle());
        root.addView(titleView);

        TextView hint = UiKit.text(this, "（点击标题可修改）", 11f);
        hint.setAlpha(0.5f);
        root.addView(hint);

        // ---------- 链接
        urlView = UiKit.text(this, link.url, 12f);
        urlView.setAlpha(0.75f);
        urlView.setPadding(0, UiKit.dp(this, 6), 0, 0);
        urlView.setOnClickListener(v -> openLink());
        root.addView(urlView);

        // ---------- 操作条
        LinearLayout actions = UiKit.horizontal(this);
        actions.setPadding(0, UiKit.dp(this, 6), 0, 0);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(UiKit.smallButton(this, "打开", v -> openLink()));
        actions.addView(UiKit.smallButton(this, "复制", v -> copyUrl()));
        actions.addView(UiKit.smallButton(this, "重新获取标题", v -> refetchTitle()));
        actions.addView(UiKit.smallButton(this, "颜色", v -> pickColor()));
        root.addView(actions);

        addDivider(root);

        // ---------- 评分
        root.addView(sectionLabel("评分（点击星星）"));
        starRow = UiKit.starRow(this, link.rating, rating -> {
            link.rating = rating;
            store.updateCommentAndRating(link.id, textOf(commentBox), rating);
            UiKit.updateStars(starRow, rating);
        });
        root.addView(starRow);

        addDivider(root);

        // ---------- 标签
        LinearLayout tagHeader = UiKit.row(this, "标签");
        tagHeader.addView(UiKit.smallButton(this, "编辑", v -> editTags()));
        root.addView(tagHeader);

        tagsView = UiKit.text(this, "", 14f);
        tagsView.setPadding(0, UiKit.dp(this, 4), 0, 0);
        root.addView(tagsView);

        addDivider(root);

        // ---------- 收藏夹
        LinearLayout colHeader = UiKit.row(this, "收藏夹");
        colHeader.addView(UiKit.smallButton(this, "选择", v -> pickCollection()));
        root.addView(colHeader);

        collectionView = UiKit.text(this, "", 14f);
        collectionView.setPadding(0, UiKit.dp(this, 4), 0, 0);
        root.addView(collectionView);

        addDivider(root);

        // ---------- 评论
        root.addView(sectionLabel("评论 / 笔记"));
        commentBox = UiKit.editText(this,
                link.comment == null ? "" : link.comment,
                "写下你的想法……", true);
        commentBox.setTextSize(14f);
        root.addView(commentBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout commentActions = UiKit.horizontal(this);
        commentActions.setPadding(0, UiKit.dp(this, 8), 0, 0);
        commentActions.addView(UiKit.smallButton(this, "保存评论", v -> saveComment()));
        root.addView(commentActions);

        addDivider(root);

        // ---------- 元信息
        TextView meta = UiKit.text(this, "来源：" + link.source
                + "\n添加时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm",
                Locale.getDefault()).format(new Date(link.createdAt)), 12f);
        meta.setAlpha(0.6f);
        meta.setPadding(0, UiKit.dp(this, 8), 0, 0);
        root.addView(meta);

        scroll.addView(root);
        setContentView(scroll);

        bindTags();
        bindCollection();

        setTitle("详情");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (link == null) return;
        // 其他界面可能改过数据，重新载入
        LinkStore.Link fresh = findById(link.id);
        if (fresh == null) { finish(); return; }
        link = fresh;
        bindTags();
        bindCollection();
    }

    @Override
    public void onBackPressed() {
        // 退出时兜底保存评论，避免用户忘点保存
        saveCommentQuietly();
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- 数据

    private LinkStore.Link findById(long id) {
        for (LinkStore.Link l : store.query(null)) {
            if (l.id == id) return l;
        }
        return null;
    }

    private void bindTags() {
        List<String> tags = store.tagsOf(link.id);
        link.tags = tags;
        if (tags.isEmpty()) {
            tagsView.setText("（无标签，点「编辑」添加）");
            tagsView.setAlpha(0.5f);
        } else {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) sb.append("  ");
                String t = tags.get(i);
                int start = sb.length();
                sb.append("#").append(t);
                int col = store.tagColor(t);
                if (col != 0) {
                    sb.setSpan(new android.text.style.ForegroundColorSpan(col),
                            start, sb.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            tagsView.setText(sb);
            tagsView.setAlpha(1f);
        }
    }

    private void bindCollection() {
        List<String> names = store.collectionNamesOf(link.id);
        link.collectionNames = names;
        if (names.isEmpty()) {
            collectionView.setText("（不属于任何收藏夹，点「选择」添加）");
            collectionView.setAlpha(0.5f);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append("📁 ").append(names.get(i));
        }
        collectionView.setText(sb.toString());
        collectionView.setAlpha(1f);
    }

    // ---------------------------------------------------------------- 编辑

    private void editTitle() {
        UiKit.promptText(this, "修改标题",
                link.title == null ? "" : link.title, "输入标题", false, s -> {
                    link.title = s;
                    store.updateTitle(link.id, s);
                    titleView.setText(displayTitle(link));
                });
    }

    private void editTags() {
        List<String> cur = store.tagsOf(link.id);
        UiKit.promptText(this, "标签（逗号分隔）",
                android.text.TextUtils.join(", ", cur),
                "如：论文, 待读, 灵感", false, s -> {
                    List<String> names = new ArrayList<>();
                    for (String part : s.split("[,，\\s]+")) {
                        if (!part.trim().isEmpty()) names.add(part.trim());
                    }
                    store.setTags(link.id, names);
                    bindTags();
                });
    }

    /** 多选收藏夹：一个条目可同时属于多个收藏夹。 */
    private void pickCollection() {
        final List<LinkStore.Collection> cols = store.listCollections();
        if (cols.isEmpty()) {
            UiKit.promptText(this, "新建收藏夹", "", "收藏夹名称", false, s -> {
                if (s.isEmpty()) return;
                long id = store.createCollection(s);
                if (id > 0) store.addToCollection(link.id, id);
                bindCollection();
            });
            return;
        }

        final boolean[] checked = new boolean[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            checked[i] = store.isInCollection(link.id, cols.get(i).id);
        }
        String[] labels = new String[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            labels[i] = cols.get(i).name + "（" + cols.get(i).count + "）";
        }

        new AlertDialog.Builder(this)
                .setTitle("选择收藏夹（可多选）")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) ->
                        checked[which] = isChecked)
                .setPositiveButton("保存", (d, w) -> {
                    List<Long> ids = new ArrayList<>();
                    for (int i = 0; i < cols.size(); i++) {
                        if (checked[i]) ids.add(cols.get(i).id);
                    }
                    store.setCollections(link.id, ids);
                    bindCollection();
                })
                .setNeutralButton("＋ 新建", (d, w) ->
                        UiKit.promptText(this, "新建收藏夹", "", "收藏夹名称", false, s -> {
                            if (s.isEmpty()) return;
                            long id = store.createCollection(s);
                            if (id > 0) store.addToCollection(link.id, id);
                            bindCollection();
                        }))
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveComment() {
        store.updateCommentAndRating(link.id, textOf(commentBox), link.rating);
        link.comment = textOf(commentBox);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
    }

    private void saveCommentQuietly() {
        if (commentBox == null) return;
        String now = textOf(commentBox);
        String old = link.comment == null ? "" : link.comment;
        if (!now.equals(old)) {
            store.updateCommentAndRating(link.id, now, link.rating);
            link.comment = now;
        }
    }

    private static String textOf(EditText e) {
        return e == null ? "" : e.getText().toString();
    }

    // ---------------------------------------------------------------- 链接操作

    private void openLink() {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(link.url));
        String pkg = packageFor(link);
        if (pkg != null) {
            Intent direct = new Intent(view);
            direct.setPackage(pkg);
            if (direct.resolveActivity(getPackageManager()) != null) {
                try {
                    startActivity(direct);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        try {
            startActivity(view);
        } catch (Exception e) {
            Toast.makeText(this, "没有能打开该链接的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private static String packageFor(LinkStore.Link l) {
        String url = l.url == null ? "" : l.url.toLowerCase();
        if (url.contains("zhihu.com") || url.contains("zhimg.com")) return "com.zhihu.android";
        if (url.contains("bilibili.com") || url.contains("b23.tv")) return "tv.danmaku.bili";
        return null;
    }

    private void copyUrl() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("url", link.url));
            Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show();
        }
    }

    /** 设置条目标记色。 */
    private void pickColor() {
        UiKit.pickColor(this, "标记颜色", link.color, color -> {
            link.color = color;
            store.setLinkColor(link.id, color);
            Toast.makeText(this, "已设为" + UiKit.colorName(color), Toast.LENGTH_SHORT).show();
        });
    }

    private void refetchTitle() {
        Toast.makeText(this, "正在获取（可能需要几秒）…", Toast.LENGTH_SHORT).show();
        TitleResolver.resolve(this, link.url, (title, finalUrl) -> {
            if (title != null && !title.trim().isEmpty()) {
                link.title = title;
                store.updateTitle(link.id, title);
                titleView.setText(displayTitle(link));
                Toast.makeText(this, "标题已更新", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "获取失败（该站点可能禁止抓取）",
                        Toast.LENGTH_SHORT).show();
            }
            if (finalUrl != null && !finalUrl.isEmpty()
                    && !finalUrl.equals(link.url) && !finalUrl.startsWith("about:")) {
                link.url = finalUrl;
                link.source = LinkParser.guessSource(finalUrl);
                store.updateUrlAndSource(link.id, finalUrl, link.source);
                urlView.setText(link.url);
            }
        });
    }

    // ---------------------------------------------------------------- 小工具

    private static String displayTitle(LinkStore.Link l) {
        return (l.title == null || l.title.trim().isEmpty()) ? l.url : l.title;
    }

    private TextView sectionLabel(String s) {
        TextView t = UiKit.text(this, s, 13f);
        t.setAlpha(0.7f);
        t.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 4));
        return t;
    }

    private void addDivider(LinearLayout root) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 1));
        lp.topMargin = UiKit.dp(this, 10);
        lp.bottomMargin = UiKit.dp(this, 10);
        android.view.View v = new android.view.View(this);
        v.setBackgroundColor(0x22000000);
        root.addView(v, lp);
    }
}
