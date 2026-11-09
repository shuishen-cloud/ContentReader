package com.termux.contentmanager;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 轻量 UI 工具：纯代码构建界面时的常用件。 */
public final class UiKit {

    private UiKit() {}

    // ============================================================ 色彩

    /** 全局配色：活泼但克制。 */
    public static final class C {
        /** 页面背景（浅灰蓝） */
        public static final int BG = 0xFFF2F4F8;
        /** 卡片 / 工具条背景 */
        public static final int SURFACE = 0xFFFFFFFF;
        /** 主色（亮蓝） */
        public static final int PRIMARY = 0xFF4A7DFF;
        /** 主色浅底（chip 选中、徽标底） */
        public static final int PRIMARY_SOFT = 0xFFE8EEFF;
        /** 强调色（珊瑚橙，用于评分与高亮） */
        public static final int ACCENT = 0xFFFF8A3D;
        public static final int ACCENT_SOFT = 0xFFFFF0E4;
        /** 成功绿 */
        public static final int SUCCESS = 0xFF2FBF71;
        /** 危险红 */
        public static final int DANGER = 0xFFF0564A;
        public static final int DANGER_SOFT = 0xFFFFEBE9;

        /** 主文字 */
        public static final int TEXT = 0xFF1F2430;
        /** 次文字 */
        public static final int TEXT_2 = 0xFF6B7280;
        /** 辅助文字 */
        public static final int TEXT_3 = 0xFF9AA1AC;
        /** 分隔线 */
        public static final int DIVIDER = 0xFFE6E9EF;

        /** 来源徽标配色（按来源取色）。 */
        public static int sourceColor(String source) {
            String s = source == null ? "" : source.toLowerCase();
            switch (s) {
                case "zhihu": return 0xFF1772F6;      // 知乎蓝
                case "bilibili": return 0xFFFB7299;   // B站粉
                case "xiaohongshu": return 0xFFFF2442; // 小红书红
                case "weibo": return 0xFFFF8200;      // 微博橙
                case "github": return 0xFF24292F;     // GitHub 黑
                case "youtube": return 0xFFFF0033;
                case "other": return 0xFF3AAFB0;      // 网址：青绿
                default: return 0xFF3AAFB0;           // 未知来源：青绿
            }
        }

        /** 来源显示名。 */
        public static String sourceLabel(String source) {
            String s = source == null ? "" : source.toLowerCase();
            switch (s) {
                case "zhihu": return "知乎";
                case "bilibili": return "B站";
                case "xiaohongshu": return "小红书";
                case "weibo": return "微博";
                case "github": return "GitHub";
                case "youtube": return "YouTube";
                case "other": return "网页";
                default: return s.isEmpty() ? "网页" : s;
            }
        }
    }

    // ============================================================ 图形工具

    /** 圆角矩形背景。 */
    public static GradientDrawable roundRect(Context c, int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** 圆角矩形 + 描边。 */
    public static GradientDrawable roundRectStroke(Context c, int fill,
                                                   float radiusDp, int strokeColor,
                                                   float strokeDp) {
        GradientDrawable g = roundRect(c, fill, radiusDp);
        g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    /** 带水波纹的圆角背景。 */
    public static RippleDrawable ripple(Context c, int fill, float radiusDp, int rippleColor) {
        GradientDrawable content = roundRect(c, fill, radiusDp);
        GradientDrawable mask = roundRect(c, Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    /** 卡片：白底圆角 + 极轻阴影。 */
    public static LinearLayout card(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = roundRect(c, C.SURFACE, 14f);
        v.setBackground(bg);
        v.setElevation(dp(c, 1.5f));
        v.setClipToOutline(false);
        return v;
    }

    /** 分隔线。 */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(C.DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f))));
        return v;
    }

    /** 徽标：小圆角药丸，深色字 + 浅色底。 */
    public static TextView badge(Context c, String text, int textColor, int bgColor) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(11f);
        t.setTextColor(textColor);
        t.setPadding(dp(c, 7), dp(c, 2), dp(c, 7), dp(c, 2));
        t.setBackground(roundRect(c, bgColor, 6f));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** 筛选 chip：选中态实心主色，未选中描边。 */
    public static TextView chip(Context c, String text, boolean active,
                                View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12.5f);
        t.setPadding(dp(c, 13), dp(c, 7), dp(c, 13), dp(c, 7));
        t.setGravity(Gravity.CENTER);
        if (active) {
            t.setTextColor(Color.WHITE);
            t.setBackground(ripple(c, C.PRIMARY, 20f, 0x55FFFFFF));
        } else {
            t.setTextColor(C.TEXT_2);
            t.setBackground(ripple(c, C.SURFACE, 20f, 0x22000000));
            GradientDrawable g = roundRectStroke(c, C.SURFACE, 20f, C.DIVIDER, 1f);
            t.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(0x22000000), g,
                    roundRect(c, Color.WHITE, 20f)));
        }
        t.setOnClickListener(l);
        return t;
    }

    /** 图标按钮：圆形/圆角底 + 符号，带水波纹。 */
    public static TextView iconButton(Context c, String symbol, int sizeDp,
                                      int textColor, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(symbol);
        t.setTextSize(sizeDp * 0.5f);
        t.setTextColor(textColor);
        t.setGravity(Gravity.CENTER);
        int box = dp(c, sizeDp);
        t.setLayoutParams(new LinearLayout.LayoutParams(box, box));
        t.setBackground(ripple(c, Color.TRANSPARENT, sizeDp / 2f, 0x22000000));
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    /** 带底色的圆形图标（用于工具条上的主操作）。 */
    public static TextView circleIcon(Context c, String symbol, int sizeDp,
                                      int textColor, int bgColor,
                                      View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(symbol);
        t.setTextSize(sizeDp * 0.42f);
        t.setTextColor(textColor);
        t.setGravity(Gravity.CENTER);
        int box = dp(c, sizeDp);
        t.setLayoutParams(new LinearLayout.LayoutParams(box, box));
        t.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                roundRect(c, bgColor, sizeDp / 2f),
                roundRect(c, Color.WHITE, sizeDp / 2f)));
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    /** 水平间距占位。 */
    public static View spacer(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp)));
        return v;
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static Button button(Context c, String text, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(13f);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    /** 小号文字按钮（用于操作栏，省空间）。 */
    public static TextView smallButton(Context c, String text, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13f);
        t.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        t.setOnClickListener(l);
        return t;
    }

    public static TextView text(Context c, String s, float size) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        return t;
    }

    /** 单行输入框，套一层带 padding 的容器方便放进对话框。 */
    public static EditText editText(Context c, String initial, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setText(initial == null ? "" : initial);
        e.setHint(hint == null ? "" : hint);
        if (multiline) {
            e.setSingleLine(false);
            e.setMinLines(3);
            e.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    public static View pad(Context c, View v, int pad) {
        FrameLayout box = new FrameLayout(c);
        int p = dp(c, pad);
        box.setPadding(p, p, p, p);
        box.addView(v);
        return box;
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    /** 一行「左边标题 + 右边按钮」的结构，返回容器，按钮由调用方 addView。 */
    public static LinearLayout row(Context c, String label) {
        LinearLayout r = horizontal(c);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t = text(c, label, 14f);
        r.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    /** 纯文本输入对话框。 */
    public interface OnText {
        void onText(String s);
    }

    public static void promptText(Context c, String title, String initial,
                                  String hint, boolean multiline, OnText cb) {
        EditText input = editText(c, initial, hint, multiline);
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(pad(c, input, 16))
                .setPositiveButton("保存", (d, w) -> cb.onText(input.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    /** 星级选择行：点星星设置评分，返回容器。 */
    public static LinearLayout starRow(Context c, int rating, RatingChange cb) {
        LinearLayout row = horizontal(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView[] stars = new TextView[5];
        for (int i = 0; i < 5; i++) {
            final int value = i + 1;
            TextView s = text(c, "☆", 26f);
            s.setTextColor(C.ACCENT);
            s.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
            s.setOnClickListener(v -> cb.onChange(value));
            stars[i] = s;
            row.addView(s);
        }
        applyStars(stars, rating);
        row.setTag(stars);
        return row;
    }

    /** 更新已构建的星级行的显示。 */
    public static void updateStars(LinearLayout row, int rating) {
        Object tag = row.getTag();
        if (tag instanceof TextView[]) applyStars((TextView[]) tag, rating);
    }

    private static void applyStars(TextView[] stars, int rating) {
        for (int i = 0; i < stars.length; i++) {
            stars[i].setText(i < rating ? "★" : "☆");
            stars[i].setTextColor(C.ACCENT);
            stars[i].setAlpha(i < rating ? 1f : 0.3f);
        }
    }

    public interface RatingChange {
        void onChange(int rating);
    }

    /** 把评分渲染成字符串，如 ★★★☆☆；0 分返回空串。 */
    public static String ratingText(int rating) {
        if (rating <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < rating ? '★' : '☆');
        return sb.toString();
    }

    // ---------------------------------------------------------------- 颜色

    /** 调色板：名称 + ARGB。0 表示「无颜色」。 */
    public static final String[] COLOR_NAMES = {
            "无颜色", "红", "橙", "黄", "绿", "青", "蓝", "紫", "粉", "灰"
    };
    public static final int[] COLOR_VALUES = {
            0x00000000,
            0xFFE53935, // 红
            0xFFFB8C00, // 橙
            0xFFFDD835, // 黄
            0xFF43A047, // 绿
            0xFF00ACC1, // 青
            0xFF1E88E5, // 蓝
            0xFF8E24AA, // 紫
            0xFFD81B60, // 粉
            0xFF757575, // 灰
    };

    /** 颜色名（用于对话框显示），未知值返回「自定义」。 */
    public static String colorName(int color) {
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i] == color) return COLOR_NAMES[i];
        }
        return "自定义";
    }

    /** 颜色圆点视图，color 为 0 时显示描边空心圈。 */
    public static View colorDot(Context c, int color, int sizeDp) {
        int size = dp(c, sizeDp);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (color == 0) {
            g.setColor(0x00000000);
            g.setStroke(dp(c, 1.5f), 0x66757575);
        } else {
            g.setColor(color);
            g.setStroke(dp(c, 1), 0x33000000);
        }
        View v = new View(c);
        v.setBackground(g);
        v.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        return v;
    }

    /** 选色回调。 */
    public interface OnColor {
        void onColor(int color);
    }

    /** 颜色选择对话框：列出调色板，每行一个圆点 + 名称。 */
    public static void pickColor(Context c, String title, int current, OnColor cb) {
        String[] items = new String[COLOR_NAMES.length];
        for (int i = 0; i < COLOR_NAMES.length; i++) {
            items[i] = (COLOR_VALUES[i] == current ? "✓ " : "   ") + COLOR_NAMES[i];
        }
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setItems(items, (d, which) -> cb.onColor(COLOR_VALUES[which]))
                .setNegativeButton("取消", null)
                .show();
    }
}
