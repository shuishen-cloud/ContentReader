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
        root.setBackgroundColor(UiKit.C.BG);
        int p = UiKit.dp(this, 14);

        TextView hint = UiKit.text(this, "▤  顺序即结构，用 ▲▼ 调整", 12f);
        hint.setTextColor(UiKit.C.TEXT_3);
        hint.setPadding(p, UiKit.dp(this, 12), p, UiKit.dp(this, 8));
        root.addView(hint);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(UiKit.dp(this, 14), 0, UiKit.dp(this, 14), UiKit.dp(this, 14));
        list.setClipToPadding(false);
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
                "▤\n\n这个收藏夹还是空的\n在条目上长按 → 加入收藏夹", 14f);
        emptyView.setTextColor(UiKit.C.TEXT_3);
        emptyView.setGravity(Gravity.CENTER);
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
            LinearLayout outer, box, mid;
            TextView idx, badge, title, sub, up, down;
            if (convert == null) {
                outer = UiKit.vertical(CollectionLinksActivity.this);
                outer.setPadding(0, 0, 0, UiKit.dp(CollectionLinksActivity.this, 8));

                box = UiKit.horizontal(CollectionLinksActivity.this);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setBackground(UiKit.roundRect(CollectionLinksActivity.this,
                        UiKit.C.SURFACE, 12f));
                box.setElevation(UiKit.dp(CollectionLinksActivity.this, 1.5f));
                int p = UiKit.dp(CollectionLinksActivity.this, 12);
                box.setPadding(p, UiKit.dp(CollectionLinksActivity.this, 11), p,
                        UiKit.dp(CollectionLinksActivity.this, 11));
                outer.addView(box);

                idx = UiKit.text(CollectionLinksActivity.this, "", 12f);
                idx.setTextColor(UiKit.C.TEXT_3);
                idx.setTypeface(null, android.graphics.Typeface.BOLD);
                idx.setMinWidth(UiKit.dp(CollectionLinksActivity.this, 20));
                box.addView(idx);

                mid = UiKit.vertical(CollectionLinksActivity.this);

                LinearLayout headRow = UiKit.horizontal(CollectionLinksActivity.this);
                headRow.setGravity(Gravity.CENTER_VERTICAL);
                badge = UiKit.badge(CollectionLinksActivity.this, "",
                        Color.WHITE, UiKit.C.PRIMARY);
                badge.setTextSize(10f);
                headRow.addView(badge);
                headRow.addView(UiKit.spacer(CollectionLinksActivity.this, 7, 1));
                title = UiKit.text(CollectionLinksActivity.this, "", 14.5f);
                title.setTextColor(UiKit.C.TEXT);
                title.setMaxLines(2);
                headRow.addView(title, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                mid.addView(headRow);

                sub = UiKit.text(CollectionLinksActivity.this, "", 11.5f);
                sub.setMaxLines(1);
                sub.setPadding(0, UiKit.dp(CollectionLinksActivity.this, 4), 0, 0);
                mid.addView(sub);

                box.addView(mid, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                up = UiKit.smallButton(CollectionLinksActivity.this, "▲", null);
                down = UiKit.smallButton(CollectionLinksActivity.this, "▼", null);
                up.setTextSize(12f);
                down.setTextSize(12f);
                box.addView(up);
                box.addView(down);

                outer.setTag(new View[]{idx, badge, title, sub, up, down});
            } else {
                outer = (LinearLayout) convert;
                box = (LinearLayout) outer.getChildAt(0);
                mid = (LinearLayout) box.getChildAt(1);
                View[] v = (View[]) outer.getTag();
                idx = (TextView) v[0]; badge = (TextView) v[1];
                title = (TextView) v[2]; sub = (TextView) v[3];
                up = (TextView) v[4]; down = (TextView) v[5];
            }

            final LinkStore.Link l = data.get(pos);
            idx.setText(String.valueOf(pos + 1));

            badge.setText(UiKit.C.sourceLabel(l.source));
            badge.setBackground(UiKit.roundRect(CollectionLinksActivity.this,
                    UiKit.C.sourceColor(l.source), 6f));

            String t = (l.title == null || l.title.trim().isEmpty()) ? l.url : l.title;
            if (l.rating > 0) t = UiKit.ratingText(l.rating) + " " + t;
            title.setText(t);

            // 副标题：彩色标签
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            if (l.tags != null) {
                LinkStore store = LinkStore.get(CollectionLinksActivity.this);
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
            }
            if (sb.length() == 0) {
                sub.setVisibility(View.GONE);
            } else {
                sub.setVisibility(View.VISIBLE);
                sub.setText(sb);
            }

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

            return outer;
        }
    }
}
