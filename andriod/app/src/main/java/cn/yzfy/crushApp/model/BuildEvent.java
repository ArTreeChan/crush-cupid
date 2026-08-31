package cn.yzfy.crushApp.model;

/** 构建事件（progress / done / error） */
public class BuildEvent {
    public String type;
    public String message;
    public BuildResult result;

    public static class BuildResult {
        public Long crushId;
        public Integer version;
        public String status;
        public String memorySummary;
        public String personaSummary;
    }
}