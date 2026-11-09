package com.termux.contentmanager;

import android.app.AlertDialog;
import android.content.Context;
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
            s.setPadding(dp(c, 6), dp(c, 2), dp(c, 6), dp(c, 2));
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
            stars[i].setAlpha(i < rating ? 1f : 0.35f);
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
