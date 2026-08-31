package cn.yzfy.crushApp.model;

/** SSE 流式消息块：一次对话按 index 切多条气泡 */
public class MultiChunk {
    public int index;
    public String type;   // text | sticker
    public String content;
    public boolean done;
}