package com.termux.contentmanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 链接存储：单表 links。 */
public class LinkStore extends SQLiteOpenHelper {

    private static final String DB = "content.db";
    private static final int VERSION = 1;

    public static class Link {
        public long id;
        public String url;
        public String title;
        public String source;
        public long createdAt;
        /** 仅内存用：是否正在联网抓标题 */
        public boolean fetching;
    }

    private static LinkStore instance;

    public static synchronized LinkStore get(Context ctx) {
        if (instance == null) instance = new LinkStore(ctx.getApplicationContext());
        return instance;
    }

    private LinkStore(Context ctx) {
        super(ctx, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE links ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "url TEXT NOT NULL,"
                + "title TEXT,"
                + "source TEXT,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX idx_url ON links(url)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { }

    /** 插入；URL 重复则更新时间戳并返回 -1。返回新行 id。 */
    public long insert(String url, String title, String source) {
        ContentValues v = new ContentValues();
        v.put("url", url);
        v.put("title", title);
        v.put("source", source);
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase()
                .insertWithOnConflict("links", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** 按时间倒序取全部。 */
    public List<Link> listAll() {
        List<Link> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("links", null,
                null, null, null, null, "created_at DESC, id DESC")) {
            while (c.moveToNext()) {
                Link l = new Link();
                l.id = c.getLong(c.getColumnIndexOrThrow("id"));
                l.url = c.getString(c.getColumnIndexOrThrow("url"));
                l.title = c.getString(c.getColumnIndexOrThrow("title"));
                l.source = c.getString(c.getColumnIndexOrThrow("source"));
                l.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                out.add(l);
            }
        }
        return out;
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM links", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void delete(long id) {
        getWritableDatabase().delete("links", "id=?", new String[]{String.valueOf(id)});
    }

    /** 抓取成功后回写标题。 */
    public void updateTitle(long id, String title) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        getWritableDatabase().update("links", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** 短链解析后回写真实 URL 与来源。 */
    public void updateUrlAndSource(long id, String url, String source) {
        ContentValues v = new ContentValues();
        v.put("url", url);
        v.put("source", source);
        try {
            getWritableDatabase().update("links", v, "id=?",
                    new String[]{String.valueOf(id)});
        } catch (Exception ignored) {
            // url 有唯一索引：若目标 URL 已存在则忽略
        }
    }

    public void deleteAll() {
        getWritableDatabase().delete("links", null, null);
    }
}
