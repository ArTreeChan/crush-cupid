package cn.yzfy.crushApp.api;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import cn.yzfy.crushApp.model.AdvisorCommand;
import cn.yzfy.crushApp.model.CrushReport;
import cn.yzfy.crushApp.model.SkillCatalog;

/** Skill 目录 / 军师子命令 / 关系报告 */
public final class SkillApi {

    private static final Type CATALOG = new TypeToken<Result<SkillCatalog>>() {
    }.getType();
    private static final Type COMMANDS = new TypeToken<Result<List<AdvisorCommand>>>() {
    }.getType();
    private static final Type STRING_T = new TypeToken<Result<String>>() {
    }.getType();
    private static final Type REPORT = new TypeToken<Result<CrushReport>>() {
    }.getType();
    private static final Type REPORTS = new TypeToken<Result<List<CrushReport>>>() {
    }.getType();

    private SkillApi() {
    }

    public static void catalog(Rest.Callback<SkillCatalog> cb) {
        Rest.get("/api/skill/catalog", CATALOG, cb);
    }

    public static void prompt(String name, Rest.Callback<String> cb) {
        Rest.get("/api/skill/prompt/" + ChatApi.encode(name), STRING_T, cb);
    }

    public static void advisorCommands(Rest.Callback<List<AdvisorCommand>> cb) {
        Rest.get("/api/skill/advisor", COMMANDS, cb);
    }

    public static void invoke(String name, String question, String crushSlug, Rest.Callback<String> cb) {
        cn.yzfy.crushApp.dto.AdvisorInvoke body = new cn.yzfy.crushApp.dto.AdvisorInvoke();
        body.name = name;
        body.question = question;
        body.crushSlug = crushSlug;
        Rest.post("/api/skill/advisor/invoke", body, STRING_T, cb);
    }

    public static void generateReport(String crushSlug, Rest.Callback<CrushReport> cb) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("crushSlug", crushSlug);
        Rest.post("/api/skill/advisor/report", body, REPORT, cb);
    }

    public static void reports(String crushSlug, Rest.Callback<List<CrushReport>> cb) {
        Rest.get("/api/skill/report/list?crushSlug=" + ChatApi.encode(crushSlug), REPORTS, cb);
    }

    public static void reportDetail(long id, Rest.Callback<CrushReport> cb) {
        Rest.get("/api/skill/report/" + id, REPORT, cb);
    }

    public static void deleteReport(long id, Rest.Callback<Void> cb) {
        Rest.delete("/api/skill/report/" + id, cb);
    }
}