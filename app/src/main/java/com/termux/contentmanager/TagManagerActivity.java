package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
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
        root.setBackgroundColor(UiKit.C.BG);
        int p = UiKit.dp(this, 14);

        TextView hint = UiKit.text(this, "#  标签用于检索   长按可重命名 / 改色 / 删除", 12f);
        hint.setTextColor(UiKit.C.TEXT_3);
        hint.setPadding(p, UiKit.dp(this, 12), p, UiKit.dp(this, 8));
        root.addView(hint);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(UiKit.dp(this, 14), 0, UiKit.dp(this, 14), UiKit.dp(this, 14));
        list.setClipToPadding(false);
        adapter = new TagAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((vp, v, pos, id) -> showUsedLinks(data.get(pos)));
        list.setOnItemLongClickListener((vp, v, pos, id) -> {
            showMenu(data.get(pos));
            return true;
        });
        root.addView(list, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyView = UiKit.text(this, "#\n\n还没有标签\n在条目上长按 → 快速打标签", 14f);
        emptyView.setTextColor(UiKit.C.TEXT_3);
        emptyView.setGravity(Gravity.CENTER);
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
        String[] items = {"重命名", "设置颜色", "删除标签"};
        new AlertDialog.Builder(this)
                .setTitle("#" + t.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        UiKit.promptText(this, "重命名标签", t.name, "新名称", false, s -> {
                            if (s.isEmpty()) return;
                            LinkStore.get(this).renameTag(t.id, s);
                            refresh();
                        });
                    } else if (which == 1) {
                        UiKit.pickColor(this, "标签颜色", t.color, color -> {
                            LinkStore.get(this).setTagColor(t.id, color);
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
            android.widget.LinearLayout outer;
            android.widget.LinearLayout box;
            View dot;
            TextView name, count;
            if (convert == null) {
                outer = UiKit.vertical(TagManagerActivity.this);
                outer.setPadding(0, 0, 0, UiKit.dp(TagManagerActivity.this, 8));

                box = UiKit.horizontal(TagManagerActivity.this);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setBackground(UiKit.roundRect(TagManagerActivity.this,
                        UiKit.C.SURFACE, 12f));
                box.setElevation(UiKit.dp(TagManagerActivity.this, 1.5f));
                int p = UiKit.dp(TagManagerActivity.this, 14);
                box.setPadding(p, UiKit.dp(TagManagerActivity.this, 13), p,
                        UiKit.dp(TagManagerActivity.this, 13));
                outer.addView(box);

                dot = UiKit.colorDot(TagManagerActivity.this, 0, 14);
                android.widget.LinearLayout.LayoutParams dotLp =
                        new android.widget.LinearLayout.LayoutParams(
                                UiKit.dp(TagManagerActivity.this, 14),
                                UiKit.dp(TagManagerActivity.this, 14));
                dotLp.rightMargin = UiKit.dp(TagManagerActivity.this, 10);
                dotLp.gravity = Gravity.CENTER_VERTICAL;
                box.addView(dot, dotLp);

                name = UiKit.text(TagManagerActivity.this, "", 15f);
                box.addView(name, new android.widget.LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                count = UiKit.text(TagManagerActivity.this, "", 12f);
                count.setAlpha(0.6f);
                box.addView(count);
                outer.setTag(new View[]{dot, name, count});
            } else {
                outer = (android.widget.LinearLayout) convert;
                box = (android.widget.LinearLayout) outer.getChildAt(0);
                View[] v = (View[]) outer.getTag();
                dot = v[0]; name = (TextView) v[1]; count = (TextView) v[2];
            }
            LinkStore.Tag t = data.get(pos);
            name.setText("#" + t.name);
            name.setTextColor(t.color == 0 ? UiKit.C.TEXT : t.color);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            count.setText(t.count + " 条");
            count.setTextColor(UiKit.C.TEXT_3);

            // 色点
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            if (t.color == 0) {
                g.setColor(0x00000000);
                g.setStroke(UiKit.dp(TagManagerActivity.this, 1.5f), 0x66757575);
            } else {
                g.setColor(t.color);
                g.setStroke(UiKit.dp(TagManagerActivity.this, 1), 0x33000000);
            }
            dot.setBackground(g);
            return outer;
        }
    }
}
