package cn.yzfy.crushApp.api;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

/** 语音合成 / 声音设计 */
public final class VoiceApi {

    private static final Type STRING_T = new TypeToken<Result<String>>() {
    }.getType();

    private VoiceApi() {
    }

    /** 文本 → mp3 base64 */
    public static void synthesize(String text, String voice, Rest.Callback<String> cb) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("text", text);
        if (voice != null && !voice.isEmpty()) {
            body.put("voice", voice);
        }
        Rest.post("/api/chat/voice", body, STRING_T, cb);
    }

    /** 人设描述 → 专属 voice_id */
    public static void design(String voicePrompt, String previewText, Rest.Callback<String> cb) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("voicePrompt", voicePrompt);
        if (previewText != null && !previewText.isEmpty()) {
            body.put("previewText", previewText);
        }
        Rest.post("/api/chat/voice/design", body, STRING_T, cb);
    }
}