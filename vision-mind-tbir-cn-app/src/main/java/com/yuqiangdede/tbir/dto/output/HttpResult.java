package com.yuqiangdede.tbir.dto.output;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class HttpResult<T> {

    /**
     * 错误码 0正常 其他错误
     */
    private String code;

    /**
     * 错误信息
     */
    private String msg;

    /**
     * 检测结果数据
     */
    private T data;

    public HttpResult(String code, String msg) {
        super();
        this.code = code;
        this.msg = msg;
    }

    public HttpResult(boolean success, T data) {
        super();
        this.code = success ? "0" : "-1";
        this.data = data;
    }

    public HttpResult(boolean success, String msg, T data) {
        super();
        this.code = success ? "0" : "-1";
        this.data = data;
        this.msg = msg;
    }

    public HttpResult(boolean success, String msg) {
        super();
        this.code = success ? "0" : "-1";
        this.data = null;
        this.msg = msg;
    }
}

