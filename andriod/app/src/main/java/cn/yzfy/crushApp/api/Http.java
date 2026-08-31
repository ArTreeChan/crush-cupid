package cn.yzfy.crushApp.api;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/** 全局 OkHttp 单例。 */
public final class Http {
    public static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Config.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Config.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private Http() {
    }
}