package cn.yzfy.crushApp.api;

/** 与后端 Result&lt;T&gt; 对齐：code=0 成功。 */
public class Result<T> {
    public int code;
    public String message;
    public T data;

    public boolean ok() {
        return code == 0;
    }
}