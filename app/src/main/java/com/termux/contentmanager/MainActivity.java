package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final List<LinkStore.Link> data = new ArrayList<>();
    private LinkAdapter adapter;
    private TextView emptyView;
    private TextView countView;
    private TextView filterView;
    private EditText searchBox;
    private TextView collectionBtn;

    /** 当前筛选条件 */
    private String keyword = "";
    private String tagFilter = null;
    private int minRating = 0;
    /** null = 全部收藏夹 */
    private Long collectionFilter = null;

    private final SimpleDateFormat fmt =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = UiKit.vertical(this);

        // ---------- 第一行：计数 + 主要操作
        LinearLayout bar = UiKit.horizontal(this);
        int p = UiKit.dp(this, 8);
        bar.setPadding(p, p, p, UiKit.dp(this, 4));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        countView = UiKit.text(this, "", 14f);
        bar.addView(countView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bar.addView(UiKit.smallButton(this, "标签", v -> startActivity(
                new Intent(this, TagManagerActivity.class))));
        bar.addView(UiKit.smallButton(this, "收藏夹", v -> startActivity(
                new Intent(this, CollectionActivity.class))));
        bar.addView(UiKit.smallButton(this, "导出", v -> export()));
        bar.addView(UiKit.smallButton(this, "清空", v -> confirmClear()));
        root.addView(bar);

        // ---------- 第二行：搜索框
        LinearLayout searchRow = UiKit.horizontal(this);
        searchRow.setPadding(p, 0, p, UiKit.dp(this, 4));
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchBox = UiKit.editText(this, "", "搜索标题 / 链接 / 评论", false);
        searchBox.setTextSize(14f);
        searchBox.setPadding(UiKit.dp(this, 12), 0, UiKit.dp(this, 12), 0);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                keyword = s.toString();
                refresh();
            }
        });
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(searchRow);

        // ---------- 第三行：筛选状态
        LinearLayout filterRow = UiKit.horizontal(this);
        filterRow.setPadding(p, 0, p, UiKit.dp(this, 4));
        filterRow.setGravity(Gravity.CENTER_VERTICAL);

        filterView = UiKit.text(this, "", 12f);
        filterView.setAlpha(0.7f);
        filterRow.addView(filterView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        collectionBtn = UiKit.smallButton(this, "收藏夹筛选", v -> pickCollectionFilter());
        filterRow.addView(collectionBtn);

        filterRow.addView(UiKit.smallButton(this, "标签筛选", v -> pickTagFilter()));
        filterRow.addView(UiKit.smallButton(this, "评分", v -> pickRatingFilter()));
        filterRow.addView(UiKit.smallButton(this, "重置", v -> resetFilters()));
        root.addView(filterRow);

        // ---------- 列表
        ListView list = new ListView(this);
        adapter = new LinkAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((vp, v, pos, id) -> openDetail(data.get(pos)));
        list.setOnItemLongClickListener((vp, v, pos, id) -> {
            showItemMenu(data.get(pos));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyView = UiKit.text(this,
                "还没有内容。\n在知乎 / B站 等 App 里点「分享」→ 选择 ContentManager",
                15f);
        int ep = UiKit.dp(this, 24);
        emptyView.setPadding(ep, ep, ep, ep);
        root.addView(emptyView);

        setContentView(root);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        LinkStore store = LinkStore.get(this);
        data.clear();
        data.addAll(store.search(keyword, tagFilter, minRating, collectionFilter));

        // 载入标签 / 收藏夹用于展示
        for (LinkStore.Link l : data) {
            l.tags = store.tagsOf(l.id);
            l.collectionNames = store.collectionNamesOf(l.id);
        }

        adapter.notifyDataSetChanged();
        countView.setText("共 " + data.size() + " 条");
        emptyView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        updateFilterView();
        backfillTitles();
    }

    private void updateFilterView() {
        StringBuilder sb = new StringBuilder();
        if (collectionFilter != null) {
            String name = null;
            for (LinkStore.Collection c : LinkStore.get(this).listCollections()) {
                if (c.id == collectionFilter) { name = c.name; break; }
            }
            sb.append("收藏夹：").append(name == null ? "?" : name);
        }
        if (tagFilter != null) {
            if (sb.length() > 0) sb.append("  ");
            sb.append("#").append(tagFilter);
        }
        if (minRating > 0) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(UiKit.ratingText(minRating)).append(" 以上");
        }
        filterView.setText(sb.length() == 0 ? "无筛选" : sb.toString());
        collectionBtn.setText(collectionFilter == null ? "收藏夹筛选" : "收藏夹 ✓");
    }

    private void resetFilters() {
        tagFilter = null;
        minRating = 0;
        collectionFilter = null;
        keyword = "";
        searchBox.setText("");
        refresh();
    }

    /** 选收藏夹作为筛选条件。 */
    private void pickCollectionFilter() {
        List<LinkStore.Collection> cols = LinkStore.get(this).listCollections();
        final List<String> names = new ArrayList<>();
        names.add("全部收藏夹");
        names.add("未归类（不属于任何收藏夹）");
        for (LinkStore.Collection c : cols) names.add(c.name + "（" + c.count + "）");

        new AlertDialog.Builder(this)
                .setTitle("按收藏夹筛选")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        collectionFilter = null;
                    } else if (which == 1) {
                        collectionFilter = LinkStore.NO_COLLECTION;
                    } else {
                        collectionFilter = cols.get(which - 2).id;
                    }
                    refresh();
                })
                .show();
    }

    /** 选标签作为筛选条件。 */
    private void pickTagFilter() {
        List<LinkStore.Tag> tags = LinkStore.get(this).listTags();
        if (tags.isEmpty()) {
            Toast.makeText(this, "还没有标签，长按条目可添加", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> names = new ArrayList<>();
        names.add("全部标签");
        for (LinkStore.Tag t : tags) names.add("#" + t.name + "（" + t.count + "）");

        new AlertDialog.Builder(this)
                .setTitle("按标签筛选")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    tagFilter = which == 0 ? null : tags.get(which - 1).name;
                    refresh();
                })
                .show();
    }

    private void pickRatingFilter() {
        String[] items = {"不限", "★ 以上", "★★ 以上", "★★★ 以上", "★★★★ 以上", "★★★★★"};
        new AlertDialog.Builder(this)
                .setTitle("按评分筛选")
                .setItems(items, (d, which) -> {
                    minRating = which;
                    refresh();
                })
                .show();
    }

    // ---------------------------------------------------------------- 条目操作

    private void openDetail(LinkStore.Link l) {
        Intent i = new Intent(this, LinkDetailActivity.class);
        i.putExtra(LinkDetailActivity.EXTRA_ID, l.id);
        startActivity(i);
    }

    private void copy(LinkStore.Link l) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("url", l.url));
            Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show();
        }
    }

    /** 打开链接：优先用对应 App 的包名直接唤起（Deep Link），失败回退系统浏览器。 */
    private void openLink(LinkStore.Link l) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(l.url));

        String pkg = packageFor(l);
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
        String src = l.source == null ? "" : l.source;
        String url = l.url == null ? "" : l.url.toLowerCase();
        if (url.contains("zhihu.com") || url.contains("zhimg.com")) return "com.zhihu.android";
        if (url.contains("bilibili.com") || url.contains("b23.tv")) return "tv.danmaku.bili";
        if ("zhihu".equals(src)) return "com.zhihu.android";
        if ("bilibili".equals(src)) return "tv.danmaku.bili";
        return null;
    }

    private void showItemMenu(final LinkStore.Link l) {
        String[] items = {"查看 / 编辑详情", "打开链接", "复制链接", "快速打标签",
                "加入收藏夹", "重新获取标题", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(l.title == null ? l.url : l.title)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: openDetail(l); break;
                        case 1: openLink(l); break;
                        case 2: copy(l); break;
                        case 3: quickTag(l); break;
                        case 4: toggleCollections(l); break;
                        case 5: refetchTitle(l); break;
                        case 6:
                            LinkStore.get(this).delete(l.id);
                            refresh();
                            break;
                    }
                })
                .show();
    }

    /** 快速打标签：逗号分隔，覆盖原有标签。 */
    private void quickTag(final LinkStore.Link l) {
        List<String> cur = LinkStore.get(this).tagsOf(l.id);
        String initial = android.text.TextUtils.join(", ", cur);
        UiKit.promptText(this, "标签（逗号分隔）", initial, "如：论文, 待读", false, s -> {
            List<String> names = new ArrayList<>();
            for (String part : s.split("[,，\\s]+")) {
                if (!part.trim().isEmpty()) names.add(part.trim());
            }
            LinkStore.get(this).setTags(l.id, names);
            refresh();
        });
    }

    /** 多选：切换条目所属的收藏夹（可同时属于多个）。 */
    private void toggleCollections(final LinkStore.Link l) {
        final LinkStore store = LinkStore.get(this);
        final List<LinkStore.Collection> cols = store.listCollections();
        if (cols.isEmpty()) {
            UiKit.promptText(this, "新建收藏夹", "", "收藏夹名称", false, s -> {
                if (s.isEmpty()) return;
                long id = store.createCollection(s);
                if (id > 0) {
                    store.addToCollection(l.id, id);
                    refresh();
                }
            });
            return;
        }

        final boolean[] checked = new boolean[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            checked[i] = store.isInCollection(l.id, cols.get(i).id);
        }
        final String[] labels = new String[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            labels[i] = cols.get(i).name + "（" + cols.get(i).count + "）";
        }

        new AlertDialog.Builder(this)
                .setTitle("加入收藏夹（可多选）")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) ->
                        checked[which] = isChecked)
                .setPositiveButton("保存", (d, w) -> {
                    List<Long> ids = new ArrayList<>();
                    for (int i = 0; i < cols.size(); i++) {
                        if (checked[i]) ids.add(cols.get(i).id);
                    }
                    store.setCollections(l.id, ids);
                    refresh();
                })
                .setNeutralButton("＋ 新建", (d, w) -> {
                    UiKit.promptText(this, "新建收藏夹", "", "收藏夹名称", false, s -> {
                        if (s.isEmpty()) return;
                        long id = store.createCollection(s);
                        if (id > 0) {
                            store.addToCollection(l.id, id);
                            refresh();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refetchTitle(final LinkStore.Link l) {
        l.fetching = true;
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "正在获取（可能需要几秒）…", Toast.LENGTH_SHORT).show();
        TitleResolver.resolve(this, l.url, (title, finalUrl) -> {
            l.fetching = false;
            if (title != null && !title.trim().isEmpty()) {
                l.title = title;
                LinkStore.get(this).updateTitle(l.id, title);
                if (finalUrl != null && !finalUrl.isEmpty()
                        && !finalUrl.equals(l.url) && !finalUrl.startsWith("about:")) {
                    l.url = finalUrl;
                    l.source = LinkParser.guessSource(finalUrl);
                    LinkStore.get(this).updateUrlAndSource(l.id, finalUrl, l.source);
                }
            } else {
                Toast.makeText(this, "获取失败（该站点可能禁止抓取）",
                        Toast.LENGTH_SHORT).show();
            }
            adapter.notifyDataSetChanged();
        });
    }

    /** 标题为空时联网补抓；顺带回写短链解析后的真实 URL。 */
    private void backfillTitles() {
        for (final LinkStore.Link l : data) {
            final boolean needTitle = l.title == null || l.title.trim().isEmpty();
            final boolean maybeShort = isShortLink(l.url);
            if (!needTitle && !maybeShort) continue;
            if (l.fetching) continue;
            l.fetching = true;
            TitleResolver.resolve(this, l.url, (title, finalUrl) -> {
                l.fetching = false;
                boolean changed = false;
                if (title != null && !title.trim().isEmpty()) {
                    l.title = title;
                    LinkStore.get(this).updateTitle(l.id, title);
                    changed = true;
                }
                if (finalUrl != null && !finalUrl.isEmpty()
                        && !finalUrl.equals(l.url) && !finalUrl.startsWith("about:")) {
                    l.url = finalUrl;
                    l.source = LinkParser.guessSource(finalUrl);
                    LinkStore.get(this).updateUrlAndSource(l.id, finalUrl, l.source);
                    changed = true;
                }
                if (changed) adapter.notifyDataSetChanged();
            });
        }
    }

    private static boolean isShortLink(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("b23.tv") || u.contains("xhslink.com") || u.contains("t.cn/");
    }

    // ---------------------------------------------------------------- 导出 / 清空

    private void export() {
        if (data.isEmpty()) {
            Toast.makeText(this, "没有内容可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ContentManager 导出 ")
          .append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                  .format(new Date()))
          .append("\n\n");
        for (LinkStore.Link l : data) {
            sb.append("- [").append(l.source).append("] ")
              .append(l.title == null ? "" : l.title);
            if (l.rating > 0) sb.append("  ").append(UiKit.ratingText(l.rating));
            sb.append("\n  ").append(l.url);
            if (l.tags != null && !l.tags.isEmpty()) {
                sb.append("\n  标签：");
                for (int i = 0; i < l.tags.size(); i++) {
                    if (i > 0) sb.append(" ");
                    sb.append("#").append(l.tags.get(i));
                }
            }
            if (l.comment != null && !l.comment.trim().isEmpty()) {
                sb.append("\n  评论：").append(l.comment.trim().replace("\n", "\n        "));
            }
            sb.append("\n");
        }

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "ContentManager 导出");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "导出到"));
    }

    private void confirmClear() {
        if (data.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("清空全部？")
                .setMessage("将删除 " + data.size() + " 条记录（含标签、评论、评分），不可恢复。")
                .setPositiveButton("清空", (d, w) -> {
                    LinkStore.get(this).deleteAll();
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------------------------------------------------------- 适配器

    /** 列表项：标题 / 评分 / 标签 / 副标题（来源 · 时间）/ 评论摘要 */
    private class LinkAdapter extends BaseAdapter {
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int p) { return data.get(p); }
        @Override public long getItemId(int p) { return data.get(p).id; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout box;
            TextView title, meta, tags, comment;
            if (convert == null) {
                box = UiKit.vertical(MainActivity.this);
                box.setPadding(UiKit.dp(MainActivity.this, 16),
                        UiKit.dp(MainActivity.this, 10),
                        UiKit.dp(MainActivity.this, 16),
                        UiKit.dp(MainActivity.this, 10));

                title = UiKit.text(MainActivity.this, "", 15f);
                title.setMaxLines(2);
                box.addView(title);

                meta = UiKit.text(MainActivity.this, "", 12f);
                meta.setAlpha(0.65f);
                meta.setMaxLines(1);
                box.addView(meta);

                tags = UiKit.text(MainActivity.this, "", 12f);
                tags.setAlpha(0.85f);
                tags.setMaxLines(1);
                box.addView(tags);

                comment = UiKit.text(MainActivity.this, "", 12f);
                comment.setAlpha(0.7f);
                comment.setMaxLines(2);
                box.addView(comment);

                box.setTag(new TextView[]{title, meta, tags, comment});
            } else {
                box = (LinearLayout) convert;
                TextView[] v = (TextView[]) box.getTag();
                title = v[0]; meta = v[1]; tags = v[2]; comment = v[3];
            }

            LinkStore.Link l = data.get(pos);

            String t = (l.title == null || l.title.trim().isEmpty())
                    ? (l.fetching ? "（正在获取标题…）" : l.url)
                    : l.title;
            if (l.rating > 0) t = UiKit.ratingText(l.rating) + "  " + t;
            title.setText(t);

            StringBuilder metaSb = new StringBuilder(l.source == null ? "" : l.source)
                    .append(" · ").append(fmt.format(new Date(l.createdAt)));
            if (l.collectionNames != null && !l.collectionNames.isEmpty()) {
                metaSb.append("  ·  📁 ");
                for (int i = 0; i < l.collectionNames.size(); i++) {
                    if (i > 0) metaSb.append(" / ");
                    metaSb.append(l.collectionNames.get(i));
                }
            }
            meta.setText(metaSb.toString());

            if (l.tags == null || l.tags.isEmpty()) {
                tags.setVisibility(View.GONE);
            } else {
                tags.setVisibility(View.VISIBLE);
                StringBuilder sb = new StringBuilder();
                for (String g : l.tags) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append('#').append(g);
                }
                tags.setText(sb.toString());
            }

            if (l.comment == null || l.comment.trim().isEmpty()) {
                comment.setVisibility(View.GONE);
            } else {
                comment.setVisibility(View.VISIBLE);
                String c = l.comment.trim().replace("\n", " ");
                comment.setText("💬 " + (c.length() > 60 ? c.substring(0, 60) + "…" : c));
            }

            return box;
        }
    }
}
