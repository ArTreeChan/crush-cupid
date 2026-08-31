package cn.yzfy.crushApp.api;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import cn.yzfy.crushApp.model.Crush;
import cn.yzfy.crushApp.dto.CrushPayload;

/** 暗恋对象 CRUD + 原材料/版本/构建 */
public final class CrushApi {

    private static final Type LIST_CRUSH = new TypeToken<Result<List<Crush>>>() {
    }.getType();
    private static final Type ONE_CRUSH = new TypeToken<Result<Crush>>() {
    }.getType();

    private CrushApi() {
    }

    public static void list(Rest.Callback<List<Crush>> cb) {
        Rest.get("/api/crush", LIST_CRUSH, cb);
    }

    public static void create(CrushPayload p, Rest.Callback<Crush> cb) {
        Rest.post("/api/crush", p, ONE_CRUSH, cb);
    }

    public static void update(long id, CrushPayload p, Rest.Callback<Crush> cb) {
        Rest.put("/api/crush/" + id, p, ONE_CRUSH, cb);
    }

    public static void delete(long id, Rest.Callback<Void> cb) {
        Rest.delete("/api/crush/" + id, cb);
    }

    public static void get(long id, Rest.Callback<Crush> cb) {
        Rest.get("/api/crush/" + id, ONE_CRUSH, cb);
    }

    public static void listSources(long crushId, Rest.Callback<List<cn.yzfy.crushApp.model.Source>> cb) {
        Rest.get("/api/crush/" + crushId + "/sources",
                new TypeToken<Result<List<cn.yzfy.crushApp.model.Source>>>() {
                }.getType(), cb);
    }

    public static void addSource(long crushId, String content, String type, String fileName,
                                 Rest.Callback<cn.yzfy.crushApp.model.Source> cb) {
        cn.yzfy.crushApp.dto.SourcePayload p = new cn.yzfy.crushApp.dto.SourcePayload();
        p.content = content;
        p.type = type;
        p.fileName = fileName;
        Rest.post("/api/crush/" + crushId + "/sources", p,
                new TypeToken<Result<cn.yzfy.crushApp.model.Source>>() {
                }.getType(), cb);
    }

    public static void deleteSource(long crushId, long sourceId, Rest.Callback<Void> cb) {
        Rest.delete("/api/crush/" + crushId + "/sources/" + sourceId, cb);
    }

    /** 图片/文件上传为原材料（后端走 OCR 或直接读文本） */
    public static void uploadSource(long crushId, byte[] bytes, String fileName, String mime,
                                    Rest.Callback<cn.yzfy.crushApp.model.Source> cb) {
        okhttp3.RequestBody fileBody = okhttp3.RequestBody.create(bytes,
                okhttp3.MediaType.get(mime == null ? "application/octet-stream" : mime));
        okhttp3.MultipartBody.Part part = okhttp3.MultipartBody.Part.createFormData("file", fileName, fileBody);
        okhttp3.MultipartBody.Builder mb = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addPart(part);
        Rest.upload("/api/crush/" + crushId + "/sources/upload", mb.build(),
                new TypeToken<Result<cn.yzfy.crushApp.model.Source>>() {
                }.getType(), cb);
    }

    public static void listVersions(long crushId, Rest.Callback<List<cn.yzfy.crushApp.model.Version>> cb) {
        Rest.get("/api/crush/" + crushId + "/versions",
                new TypeToken<Result<List<cn.yzfy.crushApp.model.Version>>>() {
                }.getType(), cb);
    }

    public static Sse.Handle build(long crushId, Sse.Listener l) {
        return Sse.open("POST", "/api/crush/" + crushId + "/build", null, l);
    }
}