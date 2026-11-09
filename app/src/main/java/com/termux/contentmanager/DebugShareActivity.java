package com.termux.contentmanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 调试用：完整 dump 分享 Intent 的全部内容，支持一键复制。
 * 用于确认各 App（知乎/B站等）分享时到底给了哪些字段。
 */
public class DebugShareActivity extends Activity {

    private String lastDump = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        dump(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dump(intent);
    }

    private TextView body;

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(24, 24, 24, 8);

        Button copy = new Button(this);
        copy.setText("复制全部");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("dump", lastDump));
                Toast.makeText(this, "已复制，去粘贴吧", Toast.LENGTH_SHORT).show();
            }
        });

        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(v -> finish());

        bar.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(close, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        body = new TextView(this);
        body.setTextSize(13f);
        body.setTextIsSelectable(true);
        body.setPadding(24, 8, 24, 48);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        hs.addView(sv);
        root.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void dump(Intent intent) {
        StringBuilder sb = new StringBuilder();
        if (intent == null) {
            sb.append("intent == null\n");
            lastDump = sb.toString();
            body.setText(lastDump);
            return;
        }

        sb.append("=== ACTION / TYPE ===\n");
        sb.append("action = ").append(intent.getAction()).append('\n');
        sb.append("type   = ").append(intent.getType()).append('\n');
        sb.append("data   = ").append(intent.getDataString()).append('\n');
        sb.append("flags  = 0x").append(Integer.toHexString(intent.getFlags())).append('\n');
        sb.append("package= ").append(intent.getPackage()).append('\n');
        sb.append("component = ").append(intent.getComponent()).append('\n');
        sb.append('\n');

        // ClipData
        sb.append("=== ClipData ===\n");
        ClipData cd = intent.getClipData();
        if (cd == null) {
            sb.append("(无)\n");
        } else {
            sb.append("itemCount = ").append(cd.getItemCount()).append('\n');
            for (int i = 0; i < cd.getItemCount(); i++) {
                ClipData.Item it = cd.getItemAt(i);
                sb.append("  [").append(i).append("] text=").append(it.getText()).append('\n');
                sb.append("      html=").append(it.getHtmlText()).append('\n');
                sb.append("      uri =").append(it.getUri()).append('\n');
                sb.append("      intent=").append(it.getIntent()).append('\n');
            }
        }
        sb.append('\n');

        // Extras 全量
        sb.append("=== Extras ===\n");
        Bundle ex = intent.getExtras();
        if (ex == null) {
            sb.append("(无 extras)\n");
        } else {
            sb.append("keyCount = ").append(ex.size()).append('\n');
            for (String k : ex.keySet()) {
                Object v = ex.get(k);
                sb.append('\n');
                sb.append("--- ").append(k).append('\n');
                if (v == null) {
                    sb.append("    (null)\n");
                    continue;
                }
                sb.append("    class = ").append(v.getClass().getName()).append('\n');
                if (v instanceof CharSequence) {
                    String s = v.toString();
                    sb.append("    length = ").append(s.length()).append('\n');
                    sb.append("    value  = <<<").append(s).append(">>>\n");
                } else if (v instanceof Bundle) {
                    sb.append("    value(Bundle) = ").append(v).append('\n');
                } else if (v instanceof byte[]) {
                    sb.append("    value(byte[]) len = ").append(((byte[]) v).length).append('\n');
                } else {
                    sb.append("    value  = ").append(v).append('\n');
                }
            }
        }

        // 单独把常用 key 拎出来，方便一眼看
        sb.append('\n');
        sb.append("=== 常用 KEY 速查 ===\n");
        appendNamed(sb, intent, Intent.EXTRA_TEXT);
        appendNamed(sb, intent, Intent.EXTRA_SUBJECT);
        appendNamed(sb, intent, Intent.EXTRA_TITLE);
        appendNamed(sb, intent, Intent.EXTRA_HTML_TEXT);
        appendNamed(sb, intent, Intent.EXTRA_STREAM);
        appendNamed(sb, intent, "android.intent.extra.TITLE");

        lastDump = sb.toString();
        body.setText(lastDump);
    }

    private void appendNamed(StringBuilder sb, Intent i, String key) {
        try {
            Object v = i.getExtras() == null ? null : i.getExtras().get(key);
            sb.append(key).append(" = ")
              .append(v == null ? "(无)" : "<<" + v + ">>")
              .append('\n');
        } catch (Throwable t) {
            sb.append(key).append(" = (读取异常 ").append(t).append(")\n");
        }
    }
}
