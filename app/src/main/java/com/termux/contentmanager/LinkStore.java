package com.termux.contentmanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 链接存储。
 *
 * 结构：
 *   links         条目本体（标题/来源/评分/评论/所属收藏夹）
 *   tags          标签（扁平，用于检索）
 *   link_tags     条目 ↔ 标签 多对多
 *   collections   收藏夹（带 position，用户可手动排序；用于沉淀与构建思想）
 */
public class LinkStore extends SQLiteOpenHelper {

    private static final String DB = "content.db";
    private static final int VERSION = 3;

    /** 收藏夹未指定时的 id（NULL 语义用 0 表示"未归类"）。 */
    public static final long NO_COLLECTION = 0L;

    public static class Link {
        public long id;
        public String url;
        public String title;
        public String source;
        public long createdAt;
        /** 评论（较长，可为空） */
        public String comment;
        /** 评分 0 表示未评分，否则 1~5 */
        public int rating;
        /** 该条目的标签名（列表展示用，按需加载） */
        public List<String> tags = new ArrayList<>();
        /** 所属收藏夹 id 列表（多对多，按需加载） */
        public List<Long> collectionIds = new ArrayList<>();
        /** 所属收藏夹名列表（与 collectionIds 一一对应，按需加载） */
        public List<String> collectionNames = new ArrayList<>();
        /** 仅内存用：是否正在联网抓标题 */
        public boolean fetching;
    }

    public static class Tag {
        public long id;
        public String name;
        public int count;
    }

    public static class Collection {
        public long id;
        public String name;
        public int position;
        public int count;
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
        createBase(db);
        db.execSQL("CREATE TABLE tags ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL COLLATE NOCASE)");
        db.execSQL("CREATE UNIQUE INDEX idx_tag_name ON tags(name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE link_tags ("
                + "link_id INTEGER NOT NULL,"
                + "tag_id INTEGER NOT NULL,"
                + "PRIMARY KEY(link_id, tag_id))");
        db.execSQL("CREATE INDEX idx_link_tags_tag ON link_tags(tag_id)");
        db.execSQL("CREATE TABLE collections ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "position INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE link_collections ("
                + "link_id INTEGER NOT NULL,"
                + "collection_id INTEGER NOT NULL,"
                + "position INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(link_id, collection_id))");
        db.execSQL("CREATE INDEX idx_lc_col ON link_collections(collection_id)");
    }

    /** links 基础表（v1 结构 + v2 新增列），供 onCreate 与新装共用。 */
    private static void createBase(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE links ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "url TEXT NOT NULL,"
                + "title TEXT,"
                + "source TEXT,"
                + "created_at INTEGER NOT NULL,"
                + "comment TEXT,"
                + "rating INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX idx_url ON links(url)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            // v1 -> v2：只加列/建表，保留原有数据
            db.execSQL("ALTER TABLE links ADD COLUMN comment TEXT");
            db.execSQL("ALTER TABLE links ADD COLUMN rating INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE links ADD COLUMN collection_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE links ADD COLUMN order_in_collection INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_links_collection ON links(collection_id)");
            db.execSQL("CREATE TABLE IF NOT EXISTS tags ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL COLLATE NOCASE)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tag_name ON tags(name COLLATE NOCASE)");
            db.execSQL("CREATE TABLE IF NOT EXISTS link_tags ("
                    + "link_id INTEGER NOT NULL,"
                    + "tag_id INTEGER NOT NULL,"
                    + "PRIMARY KEY(link_id, tag_id))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_link_tags_tag ON link_tags(tag_id)");
            db.execSQL("CREATE TABLE IF NOT EXISTS collections ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL,"
                    + "position INTEGER NOT NULL DEFAULT 0)");
        }
        if (oldV < 3) {
            // v2 -> v3：单个 collection_id 改为多对多 link_collections。
            // 旧列若存在，把数据搬过去（旧列本身保留，SQLite 删列成本高且无必要）。
            db.execSQL("CREATE TABLE IF NOT EXISTS link_collections ("
                    + "link_id INTEGER NOT NULL,"
                    + "collection_id INTEGER NOT NULL,"
                    + "position INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY(link_id, collection_id))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_lc_col ON link_collections(collection_id)");
            try {
                db.execSQL("INSERT OR IGNORE INTO link_collections(link_id, collection_id, position) "
                        + "SELECT id, collection_id, order_in_collection FROM links "
                        + "WHERE collection_id > 0");
            } catch (Exception ignored) {
                // 若 v1 直接升到 v3，老库可能没有 order_in_collection 列
                try {
                    db.execSQL("INSERT OR IGNORE INTO link_collections(link_id, collection_id, position) "
                            + "SELECT id, collection_id, 0 FROM links WHERE collection_id > 0");
                } catch (Exception ignored2) { }
            }
        }
    }

    // ============================================================ links

    /** 插入；URL 重复则忽略并返回 -1。返回新行 id。 */
    public long insert(String url, String title, String source) {
        ContentValues v = new ContentValues();
        v.put("url", url);
        v.put("title", title);
        v.put("source", source);
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase()
                .insertWithOnConflict("links", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** 按时间倒序取全部（不带标签）。 */
    public List<Link> listAll() {
        return query(null);
    }

    /** 取全部条目（时间倒序）。需要按收藏夹取请用 listInCollection。 */
    public List<Link> query(Long collectionId) {
        if (collectionId != null) return listInCollection(collectionId);
        List<Link> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("links", null, null, null,
                null, null, "created_at DESC, id DESC")) {
            while (c.moveToNext()) out.add(readLink(c));
        }
        return out;
    }

    /**
     * 检索：按标题/URL/评论 关键词 + 标签 + 评分下限 组合过滤。
     *
     * @param keyword     关键词，null/空 表示不限
     * @param tagFilter   标签名过滤，null/空 表示不限
     * @param minRating   评分下限，0 表示不限
     * @param collectionId 收藏夹，null 表示不限
     */
    public List<Link> search(String keyword, String tagFilter, int minRating,
                             Long collectionId) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT l.* FROM links l");
        List<String> args = new ArrayList<>();

        if (tagFilter != null && !tagFilter.trim().isEmpty()) {
            sql.append(" JOIN link_tags lt ON lt.link_id=l.id")
               .append(" JOIN tags t ON t.id=lt.tag_id");
        }
        boolean withCollection = collectionId != null && collectionId > 0;
        if (withCollection) {
            sql.append(" JOIN link_collections lc ON lc.link_id=l.id");
        }
        sql.append(" WHERE 1=1");

        if (keyword != null && !keyword.trim().isEmpty()) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" AND (l.title LIKE ? OR l.url LIKE ? OR l.comment LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (tagFilter != null && !tagFilter.trim().isEmpty()) {
            sql.append(" AND t.name = ? COLLATE NOCASE");
            args.add(tagFilter.trim());
        }
        if (minRating > 0) {
            sql.append(" AND l.rating >= ?");
            args.add(String.valueOf(minRating));
        }
        if (withCollection) {
            sql.append(" AND lc.collection_id = ?");
            args.add(String.valueOf(collectionId));
        }
        sql.append(" ORDER BY l.created_at DESC, l.id DESC");

        List<Link> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql.toString(),
                args.toArray(new String[0]))) {
            while (c.moveToNext()) out.add(readLink(c));
        }
        return out;
    }

    private static Link readLink(Cursor c) {
        Link l = new Link();
        l.id = c.getLong(c.getColumnIndexOrThrow("id"));
        l.url = c.getString(c.getColumnIndexOrThrow("url"));
        l.title = c.getString(c.getColumnIndexOrThrow("title"));
        l.source = c.getString(c.getColumnIndexOrThrow("source"));
        l.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        l.comment = c.getString(c.getColumnIndexOrThrow("comment"));
        l.rating = c.getInt(c.getColumnIndexOrThrow("rating"));
        return l;
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM links", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("link_tags", "link_id=?", new String[]{String.valueOf(id)});
        db.delete("link_collections", "link_id=?", new String[]{String.valueOf(id)});
        db.delete("links", "id=?", new String[]{String.valueOf(id)});
    }

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

    /** 更新评论与评分。 */
    public void updateCommentAndRating(long id, String comment, int rating) {
        ContentValues v = new ContentValues();
        v.put("comment", comment);
        v.put("rating", Math.max(0, Math.min(5, rating)));
        getWritableDatabase().update("links", v, "id=?", new String[]{String.valueOf(id)});
    }

    // ---- 条目 ↔ 收藏夹：多对多 ----

    /** 条目所属的收藏夹 id 列表。 */
    public List<Long> collectionsOf(long linkId) {
        List<Long> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT collection_id FROM link_collections WHERE link_id=? "
                        + "ORDER BY position ASC, collection_id ASC",
                new String[]{String.valueOf(linkId)})) {
            while (c.moveToNext()) out.add(c.getLong(0));
        }
        return out;
    }

    /** 条目所属的收藏夹名列表（与 collectionsOf 同序）。 */
    public List<String> collectionNamesOf(long linkId) {
        List<String> out = new ArrayList<>();
        String sql = "SELECT c.name FROM collections c "
                + "JOIN link_collections lc ON lc.collection_id=c.id "
                + "WHERE lc.link_id=? ORDER BY lc.position ASC, c.id ASC";
        try (Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(linkId)})) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    /** 该条目是否在指定收藏夹内。 */
    public boolean isInCollection(long linkId, long collectionId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM link_collections WHERE link_id=? AND collection_id=?",
                new String[]{String.valueOf(linkId), String.valueOf(collectionId)})) {
            return c.moveToFirst();
        }
    }

    /** 收藏夹内下一个可用排序值。 */
    private int nextOrderIn(SQLiteDatabase db, long collectionId) {
        try (Cursor c = db.rawQuery(
                "SELECT IFNULL(MAX(position), -1) + 1 FROM link_collections WHERE collection_id=?",
                new String[]{String.valueOf(collectionId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** 加入收藏夹（已在则忽略），追加到末尾。 */
    public void addToCollection(long linkId, long collectionId) {
        if (collectionId <= NO_COLLECTION) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("link_id", linkId);
        v.put("collection_id", collectionId);
        v.put("position", nextOrderIn(db, collectionId));
        db.insertWithOnConflict("link_collections", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** 移出收藏夹。 */
    public void removeFromCollection(long linkId, long collectionId) {
        getWritableDatabase().delete("link_collections",
                "link_id=? AND collection_id=?",
                new String[]{String.valueOf(linkId), String.valueOf(collectionId)});
        normalizeLinkPositions(collectionId);
    }

    /** 切换是否在某收藏夹（详情页多选勾选用）。返回切换后的状态。 */
    public boolean toggleCollection(long linkId, long collectionId) {
        if (isInCollection(linkId, collectionId)) {
            removeFromCollection(linkId, collectionId);
            return false;
        }
        addToCollection(linkId, collectionId);
        return true;
    }

    /** 用给定收藏夹集合替换条目所属（多选用）。 */
    public void setCollections(long linkId, List<Long> collectionIds) {
        SQLiteDatabase db = getWritableDatabase();
        List<Long> before = collectionsOf(linkId);
        db.beginTransaction();
        try {
            db.delete("link_collections", "link_id=?",
                    new String[]{String.valueOf(linkId)});
            if (collectionIds != null) {
                for (Long cid : collectionIds) {
                    if (cid == null || cid <= NO_COLLECTION) continue;
                    ContentValues v = new ContentValues();
                    v.put("link_id", linkId);
                    v.put("collection_id", cid);
                    v.put("position", nextOrderIn(db, cid));
                    db.insertWithOnConflict("link_collections", null, v,
                            SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        for (Long cid : before) normalizeLinkPositions(cid);
        if (collectionIds != null) {
            for (Long cid : collectionIds) normalizeLinkPositions(cid);
        }
    }

    /** 按收藏夹内顺序取条目（供 CollectionLinksActivity 使用）。 */
    public List<Link> listInCollection(long collectionId) {
        List<Link> out = new ArrayList<>();
        String sql = "SELECT l.* FROM links l "
                + "JOIN link_collections lc ON lc.link_id=l.id "
                + "WHERE lc.collection_id=? ORDER BY lc.position ASC, l.id ASC";
        try (Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(collectionId)})) {
            while (c.moveToNext()) out.add(readLink(c));
        }
        return out;
    }

    /** 收藏夹内移动条目：delta = -1 上移，+1 下移。 */
    public void moveInCollection(long linkId, long collectionId, int delta) {
        List<Link> list = listInCollection(collectionId);
        int from = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == linkId) { from = i; break; }
        }
        if (from < 0) return;
        int to = from + delta;
        if (to < 0 || to >= list.size()) return;

        Link moved = list.remove(from);
        list.add(to, moved);

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < list.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("position", i);
                db.update("link_collections", v,
                        "link_id=? AND collection_id=?",
                        new String[]{String.valueOf(list.get(i).id),
                                String.valueOf(collectionId)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 把某收藏夹内的 position 重新编号为 0..n-1。 */
    private void normalizeLinkPositions(long collectionId) {
        SQLiteDatabase db = getWritableDatabase();
        List<Long> ids = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT link_id FROM link_collections WHERE collection_id=? "
                        + "ORDER BY position ASC, link_id ASC",
                new String[]{String.valueOf(collectionId)})) {
            while (c.moveToNext()) ids.add(c.getLong(0));
        }
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("position", i);
                db.update("link_collections", v, "link_id=? AND collection_id=?",
                        new String[]{String.valueOf(ids.get(i)),
                                String.valueOf(collectionId)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("link_tags", null, null);
        db.delete("link_collections", null, null);
        db.delete("links", null, null);
    }

    // ============================================================ tags

    /** 取所有标签（含使用计数），按使用次数倒序。 */
    public List<Tag> listTags() {
        List<Tag> out = new ArrayList<>();
        String sql = "SELECT t.id, t.name, "
                + "(SELECT COUNT(*) FROM link_tags lt WHERE lt.tag_id=t.id) AS cnt "
                + "FROM tags t ORDER BY cnt DESC, t.name COLLATE NOCASE";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Tag t = new Tag();
                t.id = c.getLong(0);
                t.name = c.getString(1);
                t.count = c.getInt(2);
                out.add(t);
            }
        }
        return out;
    }

    /** 取某条目的标签名列表。 */
    public List<String> tagsOf(long linkId) {
        List<String> out = new ArrayList<>();
        String sql = "SELECT t.name FROM tags t JOIN link_tags lt ON lt.tag_id=t.id "
                + "WHERE lt.link_id=? ORDER BY t.name COLLATE NOCASE";
        try (Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(linkId)})) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    /** 取标签 id，不存在则创建。 */
    private long ensureTag(SQLiteDatabase db, String name) {
        String n = name.trim();
        if (n.isEmpty()) return -1;
        try (Cursor c = db.rawQuery("SELECT id FROM tags WHERE name = ? COLLATE NOCASE",
                new String[]{n})) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        ContentValues v = new ContentValues();
        v.put("name", n);
        return db.insert("tags", null, v);
    }

    /** 给条目打标签（已存在则忽略）。 */
    public void addTag(long linkId, String name) {
        SQLiteDatabase db = getWritableDatabase();
        long tagId = ensureTag(db, name);
        if (tagId <= 0) return;
        ContentValues v = new ContentValues();
        v.put("link_id", linkId);
        v.put("tag_id", tagId);
        db.insertWithOnConflict("link_tags", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** 取消条目上的某个标签。 */
    public void removeTag(long linkId, String name) {
        getWritableDatabase().execSQL(
                "DELETE FROM link_tags WHERE link_id=? AND tag_id IN "
                        + "(SELECT id FROM tags WHERE name = ? COLLATE NOCASE)",
                new Object[]{linkId, name.trim()});
    }

    /** 用给定标签集合替换条目的全部标签。 */
    public void setTags(long linkId, List<String> names) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("link_tags", "link_id=?", new String[]{String.valueOf(linkId)});
            if (names != null) {
                for (String n : names) {
                    if (n == null || n.trim().isEmpty()) continue;
                    long tagId = ensureTag(db, n);
                    if (tagId <= 0) continue;
                    ContentValues v = new ContentValues();
                    v.put("link_id", linkId);
                    v.put("tag_id", tagId);
                    db.insertWithOnConflict("link_tags", null, v,
                            SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        cleanupOrphanTags();
    }

    /** 删除没有被任何条目引用的标签。 */
    public void cleanupOrphanTags() {
        getWritableDatabase().execSQL(
                "DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM link_tags)");
    }

    /** 删除标签本身（同时解除引用）。 */
    public void deleteTag(long tagId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("link_tags", "tag_id=?", new String[]{String.valueOf(tagId)});
        db.delete("tags", "id=?", new String[]{String.valueOf(tagId)});
    }

    /** 重命名标签（合并同名时自动去重）。 */
    public void renameTag(long tagId, String newName) {
        String n = newName.trim();
        if (n.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        long existed;
        try (Cursor c = db.rawQuery("SELECT id FROM tags WHERE name = ? COLLATE NOCASE",
                new String[]{n})) {
            existed = c.moveToFirst() ? c.getLong(0) : -1;
        }
        if (existed > 0 && existed != tagId) {
            // 目标名已存在：把引用迁过去，删掉旧标签
            db.execSQL("INSERT OR IGNORE INTO link_tags(link_id, tag_id) "
                    + "SELECT link_id, ? FROM link_tags WHERE tag_id=?", new Object[]{existed, tagId});
            db.delete("link_tags", "tag_id=?", new String[]{String.valueOf(tagId)});
            db.delete("tags", "id=?", new String[]{String.valueOf(tagId)});
        } else {
            ContentValues v = new ContentValues();
            v.put("name", n);
            db.update("tags", v, "id=?", new String[]{String.valueOf(tagId)});
        }
    }

    // ============================================================ collections

    /** 按 position 升序取全部收藏夹（含条目数）。 */
    public List<Collection> listCollections() {
        List<Collection> out = new ArrayList<>();
        String sql = "SELECT c.id, c.name, c.position, "
                + "(SELECT COUNT(*) FROM link_collections lc WHERE lc.collection_id=c.id) AS cnt "
                + "FROM collections c ORDER BY c.position ASC, c.id ASC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Collection col = new Collection();
                col.id = c.getLong(0);
                col.name = c.getString(1);
                col.position = c.getInt(2);
                col.count = c.getInt(3);
                out.add(col);
            }
        }
        return out;
    }

    public long createCollection(String name) {
        String n = name.trim();
        if (n.isEmpty()) return -1;
        SqlNextPos next = nextPosition(getWritableDatabase(), "collections");
        ContentValues v = new ContentValues();
        v.put("name", n);
        v.put("position", next.value);
        return getWritableDatabase().insert("collections", null, v);
    }

    public void renameCollection(long id, String name) {
        String n = name.trim();
        if (n.isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("name", n);
        getWritableDatabase().update("collections", v, "id=?",
                new String[]{String.valueOf(id)});
    }

    /** 删除收藏夹；只解除关联，条目本身保留。 */
    public void deleteCollection(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("link_collections", "collection_id=?",
                    new String[]{String.valueOf(id)});
            db.delete("collections", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 移动收藏夹：delta=-1 上移，+1 下移。
     * 与相邻项交换 position，保证顺序稳定。
     */
    public void moveCollection(long id, int delta) {
        SQLiteDatabase db = getWritableDatabase();
        List<Collection> list = listCollections();
        int from = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) { from = i; break; }
        }
        if (from < 0) return;
        int to = from + delta;
        if (to < 0 || to >= list.size()) return;

        // 交换两者 position
        Collection a = list.get(from);
        Collection b = list.get(to);
        db.beginTransaction();
        try {
            ContentValues va = new ContentValues();
            va.put("position", b.position);
            db.update("collections", va, "id=?", new String[]{String.valueOf(a.id)});
            ContentValues vb = new ContentValues();
            vb.put("position", a.position);
            db.update("collections", vb, "id=?", new String[]{String.valueOf(b.id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        normalizePositions(db, "collections");
    }

    /** position 相同或空洞时重新编号为 0..n-1。 */
    private static void normalizePositions(SQLiteDatabase db, String table) {
        List<Long> ids = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id FROM " + table
                + " ORDER BY position ASC, id ASC", null)) {
            while (c.moveToNext()) ids.add(c.getLong(0));
        }
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("position", i);
                db.update(table, v, "id=?", new String[]{String.valueOf(ids.get(i))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static class SqlNextPos {
        int value;
    }

    private static SqlNextPos nextPosition(SQLiteDatabase db, String table) {
        SqlNextPos p = new SqlNextPos();
        try (Cursor c = db.rawQuery("SELECT IFNULL(MAX(position), -1) + 1 FROM " + table, null)) {
            p.value = c.moveToFirst() ? c.getInt(0) : 0;
        }
        return p;
    }
}
