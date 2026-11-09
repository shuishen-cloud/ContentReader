package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏夹管理。
 *
 * 收藏夹是「收集材料 → 构建思想」的容器，顺序本身有含义，
 * 所以每个收藏夹可手动上移 / 下移（position 落库）。
 */
public class CollectionActivity extends Activity {

    private final List<LinkStore.Collection> data = new ArrayList<>();
    private CollectionAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = UiKit.vertical(this);
        int p = UiKit.dp(this, 8);

        root.setBackgroundColor(UiKit.C.BG);

        LinearLayout bar = UiKit.horizontal(this);
        bar.setPadding(p, UiKit.dp(this, 12), p, UiKit.dp(this, 8));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView hint = UiKit.text(this, "▤  顺序即结构，用 ▲▼ 调整", 12f);
        hint.setTextColor(UiKit.C.TEXT_3);
        bar.addView(hint, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView newBtn = UiKit.text(this, "＋ 新建", 12.5f);
        newBtn.setTextColor(Color.WHITE);
        newBtn.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 7),
                UiKit.dp(this, 14), UiKit.dp(this, 7));
        newBtn.setBackground(UiKit.ripple(this, UiKit.C.ACCENT, 16f, 0x33FFFFFF));
        newBtn.setOnClickListener(v -> createCollection());
        bar.addView(newBtn);
        root.addView(bar);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(UiKit.dp(this, 14), 0, UiKit.dp(this, 14), UiKit.dp(this, 14));
        list.setClipToPadding(false);
        adapter = new CollectionAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((vp, v, pos, id) -> openCollection(data.get(pos)));
        list.setOnItemLongClickListener((vp, v, pos, id) -> {
            showMenu(data.get(pos));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyView = UiKit.text(this,
                "▤\n\n还没有收藏夹\n收藏夹用来把条目组织成结构\n逐步构建你的想法", 14f);
        emptyView.setTextColor(UiKit.C.TEXT_3);
        emptyView.setGravity(Gravity.CENTER);
        int ep = UiKit.dp(this, 24);
        emptyView.setPadding(ep, ep, ep, ep);
        root.addView(emptyView);

        setContentView(root);
        setTitle("收藏夹");
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        data.clear();
        data.addAll(LinkStore.get(this).listCollections());
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void createCollection() {
        UiKit.promptText(this, "新建收藏夹", "", "如：待读、选题 A、理论框架", false, s -> {
            if (s.isEmpty()) return;
            long id = LinkStore.get(this).createCollection(s);
            if (id > 0) refresh();
        });
    }

    private void openCollection(LinkStore.Collection c) {
        Intent i = new Intent(this, CollectionLinksActivity.class);
        i.putExtra(CollectionLinksActivity.EXTRA_COLLECTION_ID, c.id);
        i.putExtra(CollectionLinksActivity.EXTRA_COLLECTION_NAME, c.name);
        startActivity(i);
    }

    private void showMenu(final LinkStore.Collection c) {
        String[] items = {"查看条目", "重命名", "设置颜色", "上移", "下移", "删除收藏夹"};
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: openCollection(c); break;
                        case 1:
                            UiKit.promptText(this, "重命名收藏夹", c.name, "新名称", false, s -> {
                                if (s.isEmpty()) return;
                                LinkStore.get(this).renameCollection(c.id, s);
                                refresh();
                            });
                            break;
                        case 2:
                            UiKit.pickColor(this, "收藏夹颜色", c.color, color -> {
                                LinkStore.get(this).setCollectionColor(c.id, color);
                                refresh();
                            });
                            break;
                        case 3:
                            LinkStore.get(this).moveCollection(c.id, -1);
                            refresh();
                            break;
                        case 4:
                            LinkStore.get(this).moveCollection(c.id, 1);
                            refresh();
                            break;
                        case 5:
                            new AlertDialog.Builder(this)
                                    .setTitle("删除收藏夹「" + c.name + "」？")
                                    .setMessage("其中的 " + c.count
                                            + " 条记录不会被删除，会变为「未归类」。")
                                    .setPositiveButton("删除", (dd, w) -> {
                                        LinkStore.get(this).deleteCollection(c.id);
                                        refresh();
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                            break;
                    }
                })
                .show();
    }

    private class CollectionAdapter extends BaseAdapter {
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int p) { return data.get(p); }
        @Override public long getItemId(int p) { return data.get(p).id; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout outer;
            LinearLayout box;
            View dot;
            TextView idx, name, count, up, down;
            if (convert == null) {
                box = UiKit.horizontal(CollectionActivity.this);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setBackground(UiKit.roundRect(CollectionActivity.this,
                        UiKit.C.SURFACE, 12f));
                box.setElevation(UiKit.dp(CollectionActivity.this, 1.5f));
                int p = UiKit.dp(CollectionActivity.this, 14);
                box.setPadding(p, UiKit.dp(CollectionActivity.this, 12), p,
                        UiKit.dp(CollectionActivity.this, 12));

                idx = UiKit.text(CollectionActivity.this, "", 12f);
                idx.setTextColor(UiKit.C.TEXT_3);
                idx.setTypeface(null, android.graphics.Typeface.BOLD);
                idx.setMinWidth(UiKit.dp(CollectionActivity.this, 22));
                box.addView(idx);

                dot = UiKit.colorDot(CollectionActivity.this, 0, 14);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                        UiKit.dp(CollectionActivity.this, 14),
                        UiKit.dp(CollectionActivity.this, 14));
                dotLp.rightMargin = UiKit.dp(CollectionActivity.this, 10);
                dotLp.gravity = Gravity.CENTER_VERTICAL;
                box.addView(dot, dotLp);

                LinearLayout mid = UiKit.vertical(CollectionActivity.this);
                name = UiKit.text(CollectionActivity.this, "", 15f);
                count = UiKit.text(CollectionActivity.this, "", 12f);
                count.setAlpha(0.6f);
                mid.addView(name);
                mid.addView(count);
                box.addView(mid, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                up = UiKit.smallButton(CollectionActivity.this, "▲", null);
                down = UiKit.smallButton(CollectionActivity.this, "▼", null);
                box.addView(up);
                box.addView(down);

                outer = UiKit.vertical(CollectionActivity.this);
                outer.setPadding(0, 0, 0, UiKit.dp(CollectionActivity.this, 8));
                outer.addView(box);
                outer.setTag(new View[]{idx, dot, name, count, up, down});
            } else {
                outer = (LinearLayout) convert;
                box = (LinearLayout) outer.getChildAt(0);
                View[] v = (View[]) outer.getTag();
                idx = (TextView) v[0]; dot = v[1]; name = (TextView) v[2];
                count = (TextView) v[3]; up = (TextView) v[4]; down = (TextView) v[5];
            }

            final LinkStore.Collection c = data.get(pos);
            idx.setText(String.valueOf(pos + 1));
            name.setText(c.name);
            name.setTextColor(c.color == 0 ? UiKit.C.TEXT : c.color);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            count.setText(c.count + " 条");
            count.setTextColor(UiKit.C.TEXT_3);

            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            if (c.color == 0) {
                g.setColor(0x00000000);
                g.setStroke(UiKit.dp(CollectionActivity.this, 1.5f), 0x66757575);
            } else {
                g.setColor(c.color);
                g.setStroke(UiKit.dp(CollectionActivity.this, 1), 0x33000000);
            }
            dot.setBackground(g);

            up.setAlpha(pos == 0 ? 0.25f : 1f);
            down.setAlpha(pos == data.size() - 1 ? 0.25f : 1f);
            up.setOnClickListener(v -> {
                if (pos == 0) return;
                LinkStore.get(CollectionActivity.this).moveCollection(c.id, -1);
                refresh();
            });
            down.setOnClickListener(v -> {
                if (pos == data.size() - 1) return;
                LinkStore.get(CollectionActivity.this).moveCollection(c.id, 1);
                refresh();
            });

            return outer;
        }
    }
}
