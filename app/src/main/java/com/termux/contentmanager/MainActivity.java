package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
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
import android.widget.HorizontalScrollView;
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
    private LinearLayout chipRow;
    private EditText searchBox;

    /** 当前筛选条件 */
    private String keyword = "";
    private String tagFilter = null;
    private int minRating = 0;
    /** null = 全部收藏夹 */
    private Long collectionFilter = null;
    /** 排序：0=时间 1=评分 2=标题 */
    private int sortBy = 0;

    private final SimpleDateFormat fmt =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = UiKit.vertical(this);
        root.setBackgroundColor(UiKit.C.BG);

        final int p = UiKit.dp(this, 14);

        // ================================================== 顶部彩色工具条
        LinearLayout header = UiKit.horizontal(this);
        header.setBackgroundColor(UiKit.C.SURFACE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(p, UiKit.dp(this, 10), UiKit.dp(this, 8), UiKit.dp(this, 10));
        header.setElevation(UiKit.dp(this, 2));

        // 品牌标记（彩色圆角方块）
        TextView logo = UiKit.text(this, "◈", 17f);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        int logoBox = UiKit.dp(this, 32);
        logo.setLayoutParams(new LinearLayout.LayoutParams(logoBox, logoBox));
        logo.setBackground(UiKit.roundRect(this, UiKit.C.PRIMARY, 9f));

        LinearLayout titleBox = UiKit.vertical(this);
        titleBox.setPadding(UiKit.dp(this, 10), 0, 0, 0);
        TextView appTitle = UiKit.text(this, "ContentManager", 16f);
        appTitle.setTextColor(UiKit.C.TEXT);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        countView = UiKit.text(this, "", 11.5f);
        countView.setTextColor(UiKit.C.TEXT_3);
        titleBox.addView(appTitle);
        titleBox.addView(countView);

        header.addView(logo);
        header.addView(titleBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 彩色圆形图标按钮
        header.addView(UiKit.circleIcon(this, "#", 34, UiKit.C.PRIMARY,
                UiKit.C.PRIMARY_SOFT, v -> startActivity(
                        new Intent(this, TagManagerActivity.class))));
        header.addView(UiKit.spacer(this, 6, 1));
        header.addView(UiKit.circleIcon(this, "▤", 34, UiKit.C.ACCENT,
                UiKit.C.ACCENT_SOFT, v -> startActivity(
                        new Intent(this, CollectionActivity.class))));
        header.addView(UiKit.spacer(this, 6, 1));
        header.addView(UiKit.circleIcon(this, "⤴", 34, UiKit.C.SUCCESS,
                0xFFE5F7EE, v -> export()));
        header.addView(UiKit.spacer(this, 6, 1));
        header.addView(UiKit.circleIcon(this, "⋮", 34, UiKit.C.TEXT_2,
                0xFFF0F2F5, v -> showOverflowMenu()));
        root.addView(header);

        // ================================================== 搜索框
        LinearLayout searchWrap = UiKit.horizontal(this);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setPadding(p, UiKit.dp(this, 12), p, UiKit.dp(this, 6));
        searchWrap.setBackgroundColor(UiKit.C.SURFACE);
        searchWrap.addView(makeSearchBox(), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(searchWrap);

        // ================================================== 筛选 chips（可横向滚动）
        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setBackgroundColor(UiKit.C.SURFACE);
        chipsScroll.setPadding(p, UiKit.dp(this, 2), p, UiKit.dp(this, 12));

        chipRow = UiKit.horizontal(this);
        chipsScroll.addView(chipRow);
        root.addView(chipsScroll);

        // ================================================== 列表
        ListView list = new ListView(this);
        list.setAdapter(adapter = new LinkAdapter());
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(p, UiKit.dp(this, 10), p, UiKit.dp(this, 10));
        list.setOnItemClickListener((vp, v, pos, id) -> openDetail(data.get(pos)));
        list.setOnItemLongClickListener((vp, v, pos, id) -> {
            showItemMenu(data.get(pos));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ================================================== 空状态
        emptyView = UiKit.text(this,
                "◌\n\n还没有内容\n在任意 App 里「分享」链接到这里\n就能收集、分类、构建想法",
                14f);
        emptyView.setTextColor(UiKit.C.TEXT_3);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setLineSpacing(UiKit.dp(this, 3), 1f);
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

        // 排序
        if (sortBy == 1) {
            java.util.Collections.sort(data, (a, b) -> Integer.compare(b.rating, a.rating));
        } else if (sortBy == 2) {
            java.util.Collections.sort(data, (a, b) -> {
                String ta = a.title == null ? a.url : a.title;
                String tb = b.title == null ? b.url : b.title;
                return ta.compareToIgnoreCase(tb);
            });
        }

        // 载入标签 / 收藏夹用于展示
        for (LinkStore.Link l : data) {
            l.tags = store.tagsOf(l.id);
            l.collectionNames = store.collectionNamesOf(l.id);
        }

        adapter.notifyDataSetChanged();
        countView.setText(data.size() + " 条内容");
        emptyView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        buildChips();
        backfillTitles();
    }

    /** 圆角灰底搜索框，带放大镜前缀。 */
    private View makeSearchBox() {
        LinearLayout box = UiKit.horizontal(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(UiKit.roundRect(this, 0xFFF1F3F7, 22f));
        box.setPadding(UiKit.dp(this, 14), 0, UiKit.dp(this, 14), 0);

        TextView icon = UiKit.text(this, "🔍", 13f);
        icon.setAlpha(0.55f);
        box.addView(icon);
        box.addView(UiKit.spacer(this, 8, 1));

        searchBox = UiKit.editText(this, "", "搜索标题 / 链接 / 评论", false);
        searchBox.setTextSize(14f);
        searchBox.setTextColor(UiKit.C.TEXT);
        searchBox.setHintTextColor(UiKit.C.TEXT_3);
        searchBox.setBackground(null);
        searchBox.setSingleLine(true);
        searchBox.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 10));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                keyword = s.toString();
                refresh();
            }
        });
        box.addView(searchBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 清除按钮
        TextView clear = UiKit.text(this, "✕", 12f);
        clear.setTextColor(UiKit.C.TEXT_3);
        clear.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 6),
                UiKit.dp(this, 6), UiKit.dp(this, 6));
        clear.setOnClickListener(v -> {
            searchBox.setText("");
            keyword = "";
            refresh();
        });
        box.addView(clear);

        return box;
    }

    /** 右上角 ⋮ 溢出菜单。 */
    private void showOverflowMenu() {
        String[] items = {"排序：按时间", "排序：按评分", "排序：按标题", "清空全部记录"};
        new AlertDialog.Builder(this)
                .setTitle("更多")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: sortBy = 0; refresh(); break;
                        case 1: sortBy = 1; refresh(); break;
                        case 2: sortBy = 2; refresh(); break;
                        case 3: confirmClear(); break;
                    }
                })
                .show();
    }

    /** 重建筛选 chips（每次 refresh 调用，反映当前选中态）。 */
    private void buildChips() {
        if (chipRow == null) return;
        chipRow.removeAllViews();
        chipRow.setGravity(Gravity.CENTER_VERTICAL);

        chipRow.addView(UiKit.chip(this, "全部", isNoFilter(), v -> resetFilters()
        ));
        chipRow.addView(UiKit.spacer(this, 7, 1));

        chipRow.addView(UiKit.chip(this,
                collectionFilter == null ? "收藏夹" : "收藏夹 ·" + shortCollectionName(),
                collectionFilter != null, v -> pickCollectionFilter()));
        chipRow.addView(UiKit.spacer(this, 7, 1));

        chipRow.addView(UiKit.chip(this,
                tagFilter == null ? "标签" : "#" + tagFilter,
                tagFilter != null, v -> pickTagFilter()));
        chipRow.addView(UiKit.spacer(this, 7, 1));

        chipRow.addView(UiKit.chip(this,
                minRating == 0 ? "评分" : UiKit.ratingText(minRating),
                minRating > 0, v -> pickRatingFilter()));

        if (!isNoFilter()) {
            chipRow.addView(UiKit.spacer(this, 7, 1));
            TextView reset = UiKit.chip(this, "重置 ✕", false, v -> resetFilters());
            reset.setTextColor(UiKit.C.DANGER);
            chipRow.addView(reset);
        }
    }

    private boolean isNoFilter() {
        return tagFilter == null && minRating == 0 && collectionFilter == null;
    }

    private String shortCollectionName() {
        if (collectionFilter == null) return "";
        if (collectionFilter == LinkStore.NO_COLLECTION) return "未归类";
        for (LinkStore.Collection c : LinkStore.get(this).listCollections()) {
            if (c.id == collectionFilter) return c.name;
        }
        return "?";
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
                "加入收藏夹", "设置颜色", "重新获取标题", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(l.title == null ? l.url : l.title)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: openDetail(l); break;
                        case 1: openLink(l); break;
                        case 2: copy(l); break;
                        case 3: quickTag(l); break;
                        case 4: toggleCollections(l); break;
                        case 5: pickLinkColor(l); break;
                        case 6: refetchTitle(l); break;
                        case 7:
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

    /** 设置条目标记色。 */
    private void pickLinkColor(final LinkStore.Link l) {
        UiKit.pickColor(this, "标记颜色", l.color, color -> {
            l.color = color;
            LinkStore.get(this).setLinkColor(l.id, color);
            adapter.notifyDataSetChanged();
        });
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

    /** 卡片式列表项：来源徽标 / 标题 / 星级 / 标签 / 评论摘要 / 更多。 */
    private class LinkAdapter extends BaseAdapter {
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int p) { return data.get(p); }
        @Override public long getItemId(int p) { return data.get(p).id; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            final LinearLayout wrap;      // 外层：上下留白，负责卡片间距
            View colorBar;
            LinearLayout body, headerRow, tagRow, footerRow;
            TextView srcBadge, title, starRow, meta, tags, comment, more;

            final LinkStore.Link l = data.get(pos);

            if (convert == null) {
                wrap = new LinearLayout(MainActivity.this);
                wrap.setOrientation(LinearLayout.HORIZONTAL);
                wrap.setPadding(0, 0, 0, UiKit.dp(MainActivity.this, 9));

                // 卡片本体
                body = new LinearLayout(MainActivity.this);
                body.setOrientation(LinearLayout.VERTICAL);
                body.setBackground(UiKit.roundRect(MainActivity.this,
                        UiKit.C.SURFACE, 14f));
                body.setElevation(UiKit.dp(MainActivity.this, 1.5f));
                body.setClipToOutline(true);
                wrap.addView(body, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                // 色条（卡片顶部细条，无色则隐藏）
                colorBar = new View(MainActivity.this);
                body.addView(colorBar, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        UiKit.dp(MainActivity.this, 3)));

                LinearLayout content = new LinearLayout(MainActivity.this);
                content.setOrientation(LinearLayout.VERTICAL);
                content.setPadding(UiKit.dp(MainActivity.this, 14),
                        UiKit.dp(MainActivity.this, 9),
                        UiKit.dp(MainActivity.this, 8),
                        UiKit.dp(MainActivity.this, 9));
                body.addView(content);

                // 第一行：来源徽标 + 星级 + 更多
                headerRow = new LinearLayout(MainActivity.this);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);

                srcBadge = UiKit.badge(MainActivity.this, "", Color.WHITE,
                        UiKit.C.PRIMARY);
                srcBadge.setTextSize(10f);
                srcBadge.setPadding(UiKit.dp(MainActivity.this, 6),
                        UiKit.dp(MainActivity.this, 1),
                        UiKit.dp(MainActivity.this, 6),
                        UiKit.dp(MainActivity.this, 1));
                headerRow.addView(srcBadge);
                headerRow.addView(UiKit.spacer(MainActivity.this, 8, 1));

                starRow = UiKit.text(MainActivity.this, "", 12.5f);
                starRow.setTextColor(UiKit.C.ACCENT);
                headerRow.addView(starRow);

                View headerSpacer = new View(MainActivity.this);
                headerRow.addView(headerSpacer, new LinearLayout.LayoutParams(
                        0, 1, 1f));

                more = UiKit.text(MainActivity.this, "⋮", 16f);
                more.setTextColor(UiKit.C.TEXT_3);
                more.setGravity(Gravity.CENTER);
                more.setPadding(UiKit.dp(MainActivity.this, 10),
                        UiKit.dp(MainActivity.this, 0),
                        UiKit.dp(MainActivity.this, 2),
                        UiKit.dp(MainActivity.this, 0));
                headerRow.addView(more);

                content.addView(headerRow);

                // 标题
                title = UiKit.text(MainActivity.this, "", 15f);
                title.setTextColor(UiKit.C.TEXT);
                title.setMaxLines(2);
                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                titleLp.topMargin = UiKit.dp(MainActivity.this, 6);
                content.addView(title, titleLp);

                // 标签行
                tagRow = new LinearLayout(MainActivity.this);
                tagRow.setOrientation(LinearLayout.HORIZONTAL);
                tagRow.setGravity(Gravity.CENTER_VERTICAL);
                tagRow.setPadding(0, UiKit.dp(MainActivity.this, 7), 0, 0);
                tagRow.setVisibility(View.GONE);
                tags = UiKit.text(MainActivity.this, "", 11.5f);
                tagRow.addView(tags);
                content.addView(tagRow);

                // 评论摘要
                comment = UiKit.text(MainActivity.this, "", 12f);
                comment.setTextColor(UiKit.C.TEXT_2);
                comment.setVisibility(View.GONE);
                comment.setMaxLines(2);
                comment.setLineSpacing(UiKit.dp(MainActivity.this, 1.5f), 1f);
                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                cLp.topMargin = UiKit.dp(MainActivity.this, 6);
                content.addView(comment, cLp);

                // 底部：时间 + 收藏夹
                footerRow = new LinearLayout(MainActivity.this);
                footerRow.setOrientation(LinearLayout.HORIZONTAL);
                footerRow.setGravity(Gravity.CENTER_VERTICAL);
                footerRow.setPadding(0, UiKit.dp(MainActivity.this, 5), 0, 0);
                meta = UiKit.text(MainActivity.this, "", 11f);
                meta.setTextColor(UiKit.C.TEXT_3);
                meta.setMaxLines(1);
                footerRow.addView(meta, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                content.addView(footerRow);

                wrap.setTag(new Object[]{colorBar, srcBadge, starRow, title,
                        tagRow, tags, comment, meta, more, footerRow});
            } else {
                wrap = (LinearLayout) convert;
                Object[] v = (Object[]) wrap.getTag();
                colorBar = (View) v[0];
                srcBadge = (TextView) v[1];
                starRow = (TextView) v[2];
                title = (TextView) v[3];
                tagRow = (LinearLayout) v[4];
                tags = (TextView) v[5];
                comment = (TextView) v[6];
                meta = (TextView) v[7];
                more = (TextView) v[8];
                footerRow = (LinearLayout) v[9];
            }

            // ---------- 渲染 ----------

            // 色条
            if (l.color == 0) {
                colorBar.setVisibility(View.GONE);
            } else {
                colorBar.setVisibility(View.VISIBLE);
                colorBar.setBackgroundColor(l.color);
            }

            // 来源徽标
            srcBadge.setText(UiKit.C.sourceLabel(l.source));
            srcBadge.setBackground(UiKit.roundRect(MainActivity.this,
                    UiKit.C.sourceColor(l.source), 6f));

            // 星级（点击就地评分）
            if (l.rating > 0) {
                starRow.setText(UiKit.ratingText(l.rating));
                starRow.setVisibility(View.VISIBLE);
            } else {
                starRow.setText("");
                starRow.setVisibility(View.GONE);
            }

            // 标题
            String t = (l.title == null || l.title.trim().isEmpty())
                    ? (l.fetching ? "正在获取标题…" : l.url)
                    : l.title;
            title.setText(t);
            title.setAlpha(l.fetching ? 0.5f : 1f);

            // 标签（每个标签自带颜色）
            LinkStore store = LinkStore.get(MainActivity.this);
            if (l.tags == null || l.tags.isEmpty()) {
                tagRow.setVisibility(View.GONE);
            } else {
                tagRow.setVisibility(View.VISIBLE);
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
                for (int i = 0; i < l.tags.size(); i++) {
                    if (i > 0) sb.append("  ");
                    String g = l.tags.get(i);
                    int start = sb.length();
                    sb.append("#").append(g);
                    int col = store.tagColor(g);
                    sb.setSpan(new android.text.style.ForegroundColorSpan(
                                    col != 0 ? col : UiKit.C.PRIMARY),
                            start, sb.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                tags.setText(sb);
            }

            // 评论
            if (l.comment == null || l.comment.trim().isEmpty()) {
                comment.setVisibility(View.GONE);
            } else {
                comment.setVisibility(View.VISIBLE);
                String cm = l.comment.trim().replace("\n", " ");
                comment.setText("💬 " + (cm.length() > 52 ? cm.substring(0, 52) + "…" : cm));
            }

            // 底部元信息
            String time = fmt.format(new Date(l.createdAt));
            StringBuilder metaSb = new StringBuilder(time);
            if (l.collectionNames != null && !l.collectionNames.isEmpty()) {
                metaSb.append("  ·  📁 ");
                for (int i = 0; i < l.collectionNames.size(); i++) {
                    if (i > 0) metaSb.append(" / ");
                    metaSb.append(l.collectionNames.get(i));
                }
            }
            meta.setText(metaSb.toString());
            footerRow.setVisibility(View.VISIBLE);

            // 交互：点卡片进详情，点 ⋮ 出菜单
            final int p2 = pos;
            more.setOnClickListener(v -> showItemMenu(data.get(p2)));

            return wrap;
        }
    }
}
