package cn.keking.web.controller.result;

import lombok.Getter;

@Getter
public enum ResultCodeEnum {
    SUCCESS(200, "成功"),
    FAIL(400, "失败"),
    ;

    private final int code;
    private final String tip;

    ResultCodeEnum(int code, String tip) {
        this.code = code;
        this.tip = tip;
    }

}
