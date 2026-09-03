package cn.yzfy.crushApp.api;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import java.io.File;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/** 图片加载工具：远端 URL / 相对路径 / 本地文件 / data:base64。 */
public final class ImageLoader {
    private ImageLoader() {
    }

    public static void load(final ImageView iv, final String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (url.startsWith("data:") || url.startsWith("data=") || isLikelyBase64(url)) {
            iv.setImageBitmap(decodeBase64(url));
            return;
        }
        if (!url.startsWith("http")) {
            // 相对路径：拼后端地址；本地文件绝对路径则直接读
            File f = new File(url);
            if (f.exists()) {
                iv.setImageBitmap(decodeFile(url));
            } else {
                load(iv, Config.BASE_URL + (url.startsWith("/") ? "" : "/") + url);
            }
            return;
        }
        final String target = url.startsWith("http") ? url : Config.BASE_URL + url;
        Http.client.newCall(new okhttp3.Request.Builder().url(target).build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, java.io.IOException e) {
                    }

                    @Override
                    public void onResponse(Call call, Response resp) throws java.io.IOException {
                        if (!resp.isSuccessful()) {
                            resp.close();
                            return;
                        }
                        try {
                            byte[] bytes = resp.body() == null ? null : resp.body().bytes();
                            final Bitmap bmp = bytes == null ? null : BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bmp != null) {
                                cn.yzfy.crushApp.ui.Ui.post(() -> iv.setImageBitmap(bmp));
                            }
                        } finally {
                            resp.close();
                        }
                    }
                });
    }

    public static Bitmap decodeFile(String path) {
        return BitmapFactory.decodeFile(path);
    }

    private static boolean isLikelyBase64(String s) {
        if (s.length() < 200) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '+' || c == '/' || c == '=' || c == '\n' || c == '\r' || c == ' ')) {
                return false;
            }
        }
        return true;
    }

    public static Bitmap decodeBase64(String data) {
        String s = data;
        int comma = s.indexOf(',');
        if (comma >= 0) s = s.substring(comma + 1);
        try {
            byte[] bytes = android.util.Base64.decode(s, android.util.Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }
}