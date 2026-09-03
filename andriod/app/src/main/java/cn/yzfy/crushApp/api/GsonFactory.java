package cn.yzfy.crushApp.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class GsonFactory {
    public static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    private GsonFactory() {
    }
}