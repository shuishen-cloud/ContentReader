package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
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
    private final SimpleDateFormat fmt =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 顶部工具栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));

        countView = new TextView(this);
        countView.setTextSize(14f);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(countView, countLp);

        bar.addView(makeButton("导出", v -> export()));
        bar.addView(makeButton("清空", v -> confirmClear()));
        root.addView(bar);

        // 列表
        ListView list = new ListView(this);
        adapter = new LinkAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> openLink(data.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            showItemMenu(data.get(pos));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 空状态
        emptyView = new TextView(this);
        emptyView.setText("还没有内容。\n在知乎 / B站 等 App 里点「分享」→ 选择 ContentManager");
        emptyView.setTextSize(15f);
        emptyView.setPadding(dp(24), dp(24), dp(24), dp(24));
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
        data.clear();
        data.addAll(LinkStore.get(this).listAll());
        adapter.notifyDataSetChanged();
        countView.setText("共 " + data.size() + " 条");
        emptyView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        backfillTitles();
    }

    private Button makeButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13f);
        b.setOnClickListener(l);
        return b;
    }

    private void copy(LinkStore.Link l) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("url", l.url));
            Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 打开链接：优先用对应 App 的包名直接唤起（Deep Link）。
     * 找不到 App 时回退到系统浏览器。
     */
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

    /** 按来源给出目标包名；未知来源返回 null 让系统选。 */
    private static String packageFor(LinkStore.Link l) {
        String src = l.source == null ? "" : l.source;
        String url = l.url == null ? "" : l.url.toLowerCase();
        if (url.contains("zhihu.com") || url.contains("zhimg.com")) return "com.zhihu.android";
        if (url.contains("bilibili.com") || url.contains("b23.tv")) return "tv.danmaku.bili";
        if ("zhihu".equals(src)) return "com.zhihu.android";
        if ("bilibili".equals(src)) return "tv.danmaku.bili";
        return null;
    }

    /** 标题为空时联网补抓，抓到就写库并刷列表；同时回写短链解析后的真实 URL。 */
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

    /** 短链：需要 WebView 跟随重定向才能拿到真实地址。 */
    private static boolean isShortLink(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("b23.tv") || u.contains("xhslink.com") || u.contains("t.cn/");
    }

    private void showItemMenu(LinkStore.Link l) {
        String[] items = {"打开链接", "复制链接", "修改标题", "重新获取标题", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(l.title == null ? l.url : l.title)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            openLink(l);
                            break;
                        case 1:
                            copy(l);
                            break;
                        case 2:
                            editTitle(l);
                            break;
                        case 3:
                            refetchTitle(l);
                            break;
                        case 4:
                            LinkStore.get(this).delete(l.id);
                            refresh();
                            break;
                    }
                })
                .show();
    }

    /** 手动改标题（知乎反爬抓不到时用）。 */
    private void editTitle(final LinkStore.Link l) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(l.title == null ? "" : l.title);
        input.setHint("输入标题");
        input.setSingleLine(false);
        int pad = dp(16);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad, pad, pad);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("修改标题")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String t = input.getText().toString().trim();
                    l.title = t;
                    LinkStore.get(this).updateTitle(l.id, t);
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 强制重新联网抓标题（失败提示原因）。 */
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
                Toast.makeText(this,
                        "获取失败（该站点可能禁止抓取）", Toast.LENGTH_SHORT).show();
            }
            adapter.notifyDataSetChanged();
        });
    }

    /** 导出：优先写到公共 Download 目录；失败则用系统分享。 */
    private void export() {
        if (data.isEmpty()) {
            Toast.makeText(this, "没有内容可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ContentManager 导出 ")
          .append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()))
          .append("\n\n");
        for (LinkStore.Link l : data) {
            sb.append("- [").append(l.source).append("] ")
              .append(l.title == null ? "" : l.title).append("\n  ")
              .append(l.url).append("\n");
        }
        String content = sb.toString();

        // 思路：通过 ACTION_SEND 交给用户选择保存位置（不申请存储权限也能导出）
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "ContentManager 导出");
        i.putExtra(Intent.EXTRA_TEXT, content);
        startActivity(Intent.createChooser(i, "导出到"));
    }

    private void confirmClear() {
        if (data.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("清空全部？")
                .setMessage("将删除 " + data.size() + " 条记录，不可恢复。")
                .setPositiveButton("清空", (d, w) -> {
                    LinkStore.get(this).deleteAll();
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 简单列表适配器：标题 + 副标题（来源 · 时间） */
    private class LinkAdapter extends BaseAdapter {
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int p) { return data.get(p); }
        @Override public long getItemId(int p) { return data.get(p).id; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout box;
            TextView title, sub;
            if (convert == null) {
                box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(16), dp(10), dp(16), dp(10));

                title = new TextView(MainActivity.this);
                title.setTextSize(15f);
                title.setMaxLines(2);
                box.addView(title);

                sub = new TextView(MainActivity.this);
                sub.setTextSize(12f);
                sub.setAlpha(0.6f);
                sub.setMaxLines(1);
                box.addView(sub);
                box.setTag(new TextView[]{title, sub});
            } else {
                box = (LinearLayout) convert;
                TextView[] tags = (TextView[]) box.getTag();
                title = tags[0];
                sub = tags[1];
            }

            LinkStore.Link l = data.get(pos);
            String t = (l.title == null || l.title.trim().isEmpty())
                    ? (l.fetching ? "（正在获取标题…）" : l.url)
                    : l.title;
            title.setText(t);
            sub.setText(l.source + " · " + fmt.format(new Date(l.createdAt)));
            return box;
        }
    }
}
