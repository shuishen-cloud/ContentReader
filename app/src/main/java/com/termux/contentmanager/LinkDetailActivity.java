package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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
    private TextView ratingLabel;
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
        scroll.setBackgroundColor(UiKit.C.BG);

        LinearLayout root = UiKit.vertical(this);
        int p = UiKit.dp(this, 14);
        root.setPadding(p, UiKit.dp(this, 12), p, UiKit.dp(this, 24));

        // ============================================ 卡片一：标题 + 链接 + 操作
        LinearLayout headCard = UiKit.card(this);
        LinearLayout headInner = UiKit.vertical(this);
        headInner.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
                UiKit.dp(this, 16), UiKit.dp(this, 12));
        headCard.addView(headInner);

        // 来源徽标行
        LinearLayout srcRow = UiKit.horizontal(this);
        srcRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView srcBadge = UiKit.badge(this, UiKit.C.sourceLabel(link.source),
                Color.WHITE, UiKit.C.sourceColor(link.source));
        srcRow.addView(srcBadge);
        srcRow.addView(UiKit.spacer(this, 8, 1));
        TextView colorDotView = UiKit.text(this, "●", 13f);
        colorDotView.setTextColor(link.color == 0 ? UiKit.C.TEXT_3 : link.color);
        srcRow.addView(colorDotView);
        headInner.addView(srcRow);

        // 标题
        titleView = UiKit.text(this, displayTitle(link), 18f);
        titleView.setTextColor(UiKit.C.TEXT);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setLineSpacing(UiKit.dp(this, 2), 1f);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = UiKit.dp(this, 10);
        headInner.addView(titleView, tLp);

        // 链接
        urlView = UiKit.text(this, link.url, 11.5f);
        urlView.setTextColor(UiKit.C.PRIMARY);
        urlView.setMaxLines(2);
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uLp.topMargin = UiKit.dp(this, 6);
        headInner.addView(urlView, uLp);

        // 操作按钮行（圆角 chip 风格，可横向滚动避免挤压换行）
        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = UiKit.horizontal(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = UiKit.dp(this, 12);
        actions.addView(actionChip("↗ 打开", UiKit.C.PRIMARY, v -> openLink()));
        actions.addView(UiKit.spacer(this, 8, 1));
        actions.addView(actionChip("⧉ 复制", UiKit.C.TEXT_2, v -> copyUrl()));
        actions.addView(UiKit.spacer(this, 8, 1));
        actions.addView(actionChip("⟳ 重抓", UiKit.C.ACCENT, v -> refetchTitle()));
        actions.addView(UiKit.spacer(this, 8, 1));
        actions.addView(actionChip("✎ 改标题", UiKit.C.TEXT_2, v -> editTitle()));
        actions.addView(UiKit.spacer(this, 8, 1));
        actions.addView(actionChip("◐ 颜色", UiKit.C.TEXT_2, v -> pickColor()));
        actionsScroll.addView(actions);
        headInner.addView(actionsScroll, aLp);

        root.addView(headCard);

        // ============================================ 卡片二：评分
        LinearLayout rateCard = UiKit.card(this);
        LinearLayout rateInner = UiKit.vertical(this);
        rateInner.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 12),
                UiKit.dp(this, 16), UiKit.dp(this, 12));
        rateCard.addView(rateInner);

        LinearLayout rateTitle = UiKit.horizontal(this);
        rateTitle.setGravity(Gravity.CENTER_VERTICAL);
        TextView rt = UiKit.text(this, "★ 评分", 13f);
        rt.setTextColor(UiKit.C.ACCENT);
        rt.setTypeface(null, android.graphics.Typeface.BOLD);
        rateTitle.addView(rt, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ratingLabel = UiKit.text(this, link.rating == 0 ? "未评分" : link.rating + " 星", 12f);
        ratingLabel.setTextColor(UiKit.C.TEXT_3);
        rateTitle.addView(ratingLabel);
        rateInner.addView(rateTitle);

        starRow = UiKit.starRow(this, link.rating, rating -> {
            link.rating = rating;
            store.updateCommentAndRating(link.id, textOf(commentBox), rating);
            UiKit.updateStars(starRow, rating);
            ratingLabel.setText(rating == 0 ? "未评分" : rating + " 星");
        });
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = UiKit.dp(this, 6);
        rateInner.addView(starRow, sLp);
        root.addView(rateCard, cardLp());

        // ============================================ 卡片三：标签 + 收藏夹
        LinearLayout orgCard = UiKit.card(this);
        LinearLayout orgInner = UiKit.vertical(this);
        orgInner.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 12),
                UiKit.dp(this, 16), UiKit.dp(this, 12));
        orgCard.addView(orgInner);

        // 标签
        LinearLayout tagHead = UiKit.horizontal(this);
        tagHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView th = UiKit.text(this, "# 标签", 13f);
        th.setTextColor(UiKit.C.PRIMARY);
        th.setTypeface(null, android.graphics.Typeface.BOLD);
        tagHead.addView(th, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tagHead.addView(miniChip("编辑", UiKit.C.PRIMARY, v -> editTags()));
        orgInner.addView(tagHead);

        tagsView = UiKit.text(this, "", 13.5f);
        LinearLayout.LayoutParams tgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tgLp.topMargin = UiKit.dp(this, 7);
        orgInner.addView(tagsView, tgLp);

        orgInner.addView(UiKit.divider(this), dividerLp());

        // 收藏夹
        LinearLayout colHead = UiKit.horizontal(this);
        colHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView ch = UiKit.text(this, "▤ 收藏夹", 13f);
        ch.setTextColor(UiKit.C.ACCENT);
        ch.setTypeface(null, android.graphics.Typeface.BOLD);
        colHead.addView(ch, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        colHead.addView(miniChip("选择", UiKit.C.ACCENT, v -> pickCollection()));
        orgInner.addView(colHead);

        collectionView = UiKit.text(this, "", 13.5f);
        LinearLayout.LayoutParams cgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cgLp.topMargin = UiKit.dp(this, 7);
        orgInner.addView(collectionView, cgLp);
        root.addView(orgCard, cardLp());

        // ============================================ 卡片四：评论
        LinearLayout cmCard = UiKit.card(this);
        LinearLayout cmInner = UiKit.vertical(this);
        cmInner.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 12),
                UiKit.dp(this, 16), UiKit.dp(this, 12));
        cmCard.addView(cmInner);

        LinearLayout cmHead = UiKit.horizontal(this);
        cmHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView cmh = UiKit.text(this, "💬 评论 / 笔记", 13f);
        cmh.setTextColor(UiKit.C.SUCCESS);
        cmh.setTypeface(null, android.graphics.Typeface.BOLD);
        cmHead.addView(cmh, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cmHead.addView(miniChip("保存", UiKit.C.SUCCESS, v -> saveComment()));
        cmInner.addView(cmHead);

        commentBox = UiKit.editText(this,
                link.comment == null ? "" : link.comment,
                "写下你的想法……", true);
        commentBox.setTextSize(14f);
        commentBox.setTextColor(UiKit.C.TEXT);
        commentBox.setHintTextColor(UiKit.C.TEXT_3);
        commentBox.setBackground(UiKit.roundRect(this, 0xFFF6F7FA, 10f));
        commentBox.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 10),
                UiKit.dp(this, 12), UiKit.dp(this, 10));
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.topMargin = UiKit.dp(this, 8);
        cmInner.addView(commentBox, cbLp);
        root.addView(cmCard, cardLp());

        // ============================================ 元信息
        TextView meta = UiKit.text(this,
                "来源 " + UiKit.C.sourceLabel(link.source)
                        + "   ·   添加于 " + new SimpleDateFormat("yyyy-MM-dd HH:mm",
                        Locale.getDefault()).format(new Date(link.createdAt)), 11f);
        meta.setTextColor(UiKit.C.TEXT_3);
        meta.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLp.topMargin = UiKit.dp(this, 16);
        root.addView(meta, mLp);

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

    /** 卡片之间的上间距。 */
    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiKit.dp(this, 10);
        return lp;
    }

    private LinearLayout.LayoutParams dividerLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dp(this, 0.8f)));
        lp.topMargin = UiKit.dp(this, 12);
        lp.bottomMargin = UiKit.dp(this, 12);
        return lp;
    }

    /** 操作按钮：圆角描边小胶囊。 */
    private TextView actionChip(String text, int color, View.OnClickListener l) {
        TextView t = UiKit.text(this, text, 12.5f);
        t.setTextColor(color);
        t.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 7),
                UiKit.dp(this, 12), UiKit.dp(this, 7));
        GradientDrawable g = UiKit.roundRectStroke(this, 0xFFF6F7FA, 18f,
                UiKit.C.DIVIDER, 1f);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), g,
                UiKit.roundRect(this, Color.WHITE, 18f)));
        t.setOnClickListener(l);
        return t;
    }

    /** 区块右侧的小按钮。 */
    private TextView miniChip(String text, int color, View.OnClickListener l) {
        TextView t = UiKit.text(this, text, 11.5f);
        t.setTextColor(color);
        t.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 4),
                UiKit.dp(this, 10), UiKit.dp(this, 4));
        t.setBackground(UiKit.roundRect(this, 0xFFF3F5F9, 12f));
        t.setOnClickListener(l);
        return t;
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
