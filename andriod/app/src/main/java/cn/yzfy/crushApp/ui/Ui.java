package cn.yzfy.crushApp.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import cn.yzfy.crushApp.R;

/** 通用 UI 工具：主线程分发、dp、胶囊标签、圆头像、卡片、加载框。 */
public final class Ui {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return (int) ((v * c.getResources().getDisplayMetrics().density) + 0.5f);
    }

    public static void post(final Runnable r) {
        MAIN.post(r);
    }

    public static void toast(Context c, String s) {
        toast(c, s, false);
    }

    public static void toast(Context c, String s, boolean longToast) {
        Ui.post(() -> Toast.makeText(c, s, longToast ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
    }

    public static void confirm(Context c, String title, String message, String okText, Runnable onOk) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(okText, (d, w) -> {
                    d.dismiss();
                    if (onOk != null) onOk.run();
                })
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .show();
    }

    /** 圆形头像（名字首字） */
    public static TextView avatar(Context c, String initial, @ColorInt int color, int sizeDp) {
        TextView tv = new TextView(c);
        int size = dp(c, sizeDp);
        tv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        tv.setText(TextUtils.isEmpty(initial) ? "?" : initial.substring(0, 1));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(sizeDp * 0.36f);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setBackground(circle(color));
        return tv;
    }

    public static GradientDrawable circle(@ColorInt int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    /** 圆角纯色块 */
    public static GradientDrawable rounded(@ColorInt int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * 4); // px
        return d;
    }

    public static GradientDrawable rounded(float radiusDp) {
        return rounded(0, radiusDp);
    }

    public static GradientDrawable rounded(@ColorInt int color) {
        return rounded(color, 12);
    }

    /** 胶囊标签 chip */
    public static TextView chip(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(0xFF9B5A66);
        tv.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        tv.setBackground(ContextCompat.getDrawable(c, R.drawable.bg_chip));
        return tv;
    }

    /** 标签文字（secondary 描述） */
    public static TextView caption(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFFA5929C);
        return tv;
    }

    /** 小节标题 */
    public static TextView section(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setTextColor(0xFF2A2233);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 6));
        return tv;
    }

    /** 白色圆角卡片容器 */
    public static LinearLayout card(Context c, float radiusDp) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(0xFFFFFFFF, radiusDp));
        box.setElevation(dp(c, 2));
        box.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return box;
    }

    public static LinearLayout card(Context c) {
        return card(c, 18);
    }

    /** 垂直间距 */
    public static View space(Context c, float dpVal) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpVal)));
        return v;
    }

    public static Dialog loading(Context c, String message) {
        Dialog d = new Dialog(c);
        d.setCancelable(false);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(c, 28), dp(c, 24), dp(c, 28), dp(c, 24));
        CircularProgressIndicator bar = new CircularProgressIndicator(c);
        bar.setIndeterminate(true);
        box.addView(bar, new ViewGroup.LayoutParams(dp(c, 56), dp(c, 56)));
        if (!TextUtils.isEmpty(message)) {
            TextView tv = new TextView(c);
            tv.setText(message);
            tv.setTextSize(14);
            tv.setTextColor(0xFF6B5E70);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(c, 12);
            box.addView(tv, lp);
        }
        d.setContentView(box);
        d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        d.show();
        return d;
    }

    public static void dismiss(Dialog d) {
        if (d != null && d.isShowing()) {
            try {
                Ui.post(d::dismiss);
            } catch (Exception ignored) {
            }
        }
    }

    public static void setBg(View v, android.graphics.drawable.Drawable d) {
        v.setBackground(d);
    }
}