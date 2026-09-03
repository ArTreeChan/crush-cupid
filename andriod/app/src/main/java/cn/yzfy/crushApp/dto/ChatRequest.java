package cn.yzfy.crushApp.dto;

import java.util.List;

/** 对话请求体 */
public class ChatRequest {
    public String crushSlug;
    public String message;
    public String provider;
    public List<ChatMedia> media;
    public String skillPrompt;
    public Boolean advisorMode;

    public ChatRequest() {
    }

    public ChatRequest(String crushSlug, String message) {
        this.crushSlug = crushSlug;
        this.message = message;
    }

    public static class ChatMedia {
        public String type;      // IMAGE_BASE64 / IMAGE_URL / AUDIO_BASE64 ...
        public String mimeType;
        public String data;
        public String fileName;

        public ChatMedia() {
        }

        public ChatMedia(String type, String mimeType, String data, String fileName) {
            this.type = type;
            this.mimeType = mimeType;
            this.data = data;
            this.fileName = fileName;
        }
    }
}