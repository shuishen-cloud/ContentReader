package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 标签管理：查看使用次数、重命名、删除。 */
public class TagManagerActivity extends Activity {

    private final List<LinkStore.Tag> data = new ArrayList<>();
    private TagAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.widget.LinearLayout root = UiKit.vertical(this);
        int p = UiKit.dp(this, 8);

        TextView hint = UiKit.text(this, "标签用于检索。长按可重命名或删除。", 12f);
        hint.setAlpha(0.65f);
        hint.setPadding(p, p, p, p);
        root.addView(hint);

        ListView list = new ListView(this);
        adapter = new TagAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((vp, v, pos, id) -> showUsedLinks(data.get(pos)));
        list.setOnItemLongClickListener((vp, v, pos, id) -> {
            showMenu(data.get(pos));
            return true;
        });
        root.addView(list, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyView = UiKit.text(this, "还没有标签。\n在条目上长按 → 快速打标签。", 15f);
        int ep = UiKit.dp(this, 24);
        emptyView.setPadding(ep, ep, ep, ep);
        root.addView(emptyView);

        setContentView(root);
        setTitle("标签管理");
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        data.clear();
        data.addAll(LinkStore.get(this).listTags());
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showMenu(final LinkStore.Tag t) {
        String[] items = {"重命名", "删除标签"};
        new AlertDialog.Builder(this)
                .setTitle("#" + t.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        UiKit.promptText(this, "重命名标签", t.name, "新名称", false, s -> {
                            if (s.isEmpty()) return;
                            LinkStore.get(this).renameTag(t.id, s);
                            refresh();
                        });
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("删除标签 #" + t.name + "？")
                                .setMessage("仅从所有条目上移除该标签，不删除条目本身。")
                                .setPositiveButton("删除", (dd, w) -> {
                                    LinkStore.get(this).deleteTag(t.id);
                                    refresh();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    /** 点标签看含该标签的条目。 */
    private void showUsedLinks(LinkStore.Tag t) {
        List<LinkStore.Link> links = LinkStore.get(this)
                .search(null, t.name, 0, null);
        if (links.isEmpty()) {
            Toast.makeText(this, "没有条目使用该标签", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> names = new ArrayList<>();
        for (LinkStore.Link l : links) {
            String title = (l.title == null || l.title.trim().isEmpty()) ? l.url : l.title;
            if (title.length() > 50) title = title.substring(0, 50) + "…";
            names.add(title);
        }
        new AlertDialog.Builder(this)
                .setTitle("#" + t.name + "（" + links.size() + " 条）")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    android.content.Intent i = new android.content.Intent(
                            this, LinkDetailActivity.class);
                    i.putExtra(LinkDetailActivity.EXTRA_ID, links.get(which).id);
                    startActivity(i);
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private class TagAdapter extends BaseAdapter {
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int p) { return data.get(p); }
        @Override public long getItemId(int p) { return data.get(p).id; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            android.widget.LinearLayout box;
            TextView name, count;
            if (convert == null) {
                box = UiKit.horizontal(TagManagerActivity.this);
                box.setGravity(Gravity.CENTER_VERTICAL);
                int p = UiKit.dp(TagManagerActivity.this, 16);
                box.setPadding(p, UiKit.dp(TagManagerActivity.this, 12), p,
                        UiKit.dp(TagManagerActivity.this, 12));

                name = UiKit.text(TagManagerActivity.this, "", 15f);
                box.addView(name, new android.widget.LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                count = UiKit.text(TagManagerActivity.this, "", 12f);
                count.setAlpha(0.6f);
                box.addView(count);
                box.setTag(new TextView[]{name, count});
            } else {
                box = (android.widget.LinearLayout) convert;
                TextView[] v = (TextView[]) box.getTag();
                name = v[0]; count = v[1];
            }
            LinkStore.Tag t = data.get(pos);
            name.setText("#" + t.name);
            count.setText(t.count + " 条");
            return box;
        }
    }
}
