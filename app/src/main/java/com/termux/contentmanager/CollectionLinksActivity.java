package com.termux.contentmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 某个收藏夹内的条目列表。
 *
 * 这里条目的顺序由用户控制（▲▼），因为收藏夹的用途是
 * 「把收集来的材料排成一个结构」，顺序本身就是信息。
 */
public class CollectionLinksActivity extends Activity {

    public static final String EXTRA_COLLECTION_ID = "collection_id";
    public static final String EXTRA_COLLECTION_NAME = "collection_name";

    private final List<LinkStore.Link> data = new ArrayList<>();
    private ItemAdapter adapter;
    private TextView emptyView;
    private long collectionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        collectionId = getIntent().getLongExtra(EXTRA_COLLECTION_ID, -1);
        String name = getIntent().getStringExtra(EXTRA_COLLECTION_NAME);
        if (collectionId <= 0) { finish(); return; }

        LinearLayout root = UiKit.vertical(this);
        int p = UiKit.dp(this, 8);

        TextView hint = UiKit.text(this, "顺序即结构，用 ▲▼ 调整", 12f);
        hint.setAlpha(0.65f);
        hint.setPadding(p, p, p, p);
        root.addView(hint);

        ListView list = new ListView(this);
        adapter = new ItemAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((vp, v, pos, id) -> {
            Intent i = new Intent(this, LinkDetailActivity.class);
            i.putExtra(LinkDetailActivity.EXTRA_ID, data.get(pos).id);
            startActivity(i);
        });
        list.setOnItemLongClickListener((vp, v, pos, id) -> {
            showMenu(data.get(pos));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyView = UiKit.text(this,
                "这个收藏夹还是空的。\n在列表里长按条目 → 加入收藏夹。", 15f);
        int ep = UiKit.dp(this, 24);
        emptyView.setPadding(ep, ep, ep, ep);
        root.addView(emptyView);

        setContentView(root);
        setTitle(name == null ? "收藏夹" : name);
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
        data.addAll(store.listInCollection(collectionId));
        for (LinkStore.Link l : data) l.tags = store.tagsOf(l.id);
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showMenu(final LinkStore.Link l) {
        String[] items = {"查看详情", "移出收藏夹"};
        new AlertDialog.Builder(this)
                .setTitle(l.title == null ? l.url : l.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(this, LinkDetailActivity.class);
                        i.putExtra(LinkDetailActivity.EXTRA_ID, l.id);
                        startActivity(i);
                    } else {
                        LinkStore.get(this).removeFromCollection(l.id, collectionId);
                        refresh();
                    }
                })
                .show();
    }

    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int p) { return data.get(p); }
        @Override public long getItemId(int p) { return data.get(p).id; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout box;
            TextView idx, title, sub, up, down;
            if (convert == null) {
                box = UiKit.horizontal(CollectionLinksActivity.this);
                box.setGravity(Gravity.CENTER_VERTICAL);
                int p = UiKit.dp(CollectionLinksActivity.this, 12);
                box.setPadding(p, UiKit.dp(CollectionLinksActivity.this, 10), p,
                        UiKit.dp(CollectionLinksActivity.this, 10));

                idx = UiKit.text(CollectionLinksActivity.this, "", 13f);
                idx.setAlpha(0.5f);
                idx.setMinWidth(UiKit.dp(CollectionLinksActivity.this, 24));
                box.addView(idx);

                LinearLayout mid = UiKit.vertical(CollectionLinksActivity.this);
                title = UiKit.text(CollectionLinksActivity.this, "", 15f);
                title.setMaxLines(2);
                sub = UiKit.text(CollectionLinksActivity.this, "", 12f);
                sub.setAlpha(0.6f);
                sub.setMaxLines(1);
                mid.addView(title);
                mid.addView(sub);
                box.addView(mid, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                up = UiKit.smallButton(CollectionLinksActivity.this, "▲", null);
                down = UiKit.smallButton(CollectionLinksActivity.this, "▼", null);
                box.addView(up);
                box.addView(down);

                box.setTag(new View[]{idx, title, sub, up, down});
            } else {
                box = (LinearLayout) convert;
                View[] v = (View[]) box.getTag();
                idx = (TextView) v[0]; title = (TextView) v[1];
                sub = (TextView) v[2]; up = (TextView) v[3]; down = (TextView) v[4];
            }

            final LinkStore.Link l = data.get(pos);
            idx.setText(String.valueOf(pos + 1));

            String t = (l.title == null || l.title.trim().isEmpty()) ? l.url : l.title;
            if (l.rating > 0) t = UiKit.ratingText(l.rating) + "  " + t;
            title.setText(t);

            StringBuilder sb = new StringBuilder(l.source == null ? "" : l.source);
            if (l.tags != null && !l.tags.isEmpty()) {
                for (String g : l.tags) sb.append("  #").append(g);
            }
            sub.setText(sb.toString());

            up.setAlpha(pos == 0 ? 0.25f : 1f);
            down.setAlpha(pos == data.size() - 1 ? 0.25f : 1f);
            up.setOnClickListener(v -> {
                if (pos == 0) return;
                LinkStore.get(CollectionLinksActivity.this)
                        .moveInCollection(l.id, collectionId, -1);
                refresh();
            });
            down.setOnClickListener(v -> {
                if (pos == data.size() - 1) return;
                LinkStore.get(CollectionLinksActivity.this)
                        .moveInCollection(l.id, collectionId, 1);
                refresh();
            });

            return box;
        }
    }
}
