package cn.keking.web.controller.result;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class Result<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public Result() {
    }

    public Result(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Result(int code, String msg, T data) {
        this(code, msg);
        this.data = data;
    }

    public Result(ResultCodeEnum codeEnum) {
        this(codeEnum.getCode(), codeEnum.getTip());
    }

    public Result(ResultCodeEnum codeEnum, T data) {
        this(codeEnum.getCode(), codeEnum.getTip(), data);
    }

    public static <T> Result<T> createSuccess() {
        return new Result<>(ResultCodeEnum.SUCCESS);
    }

    public static <T> Result<T> createSuccess(String msg) {
        return new Result<>(ResultCodeEnum.SUCCESS.getCode(), msg);
    }

    public static <T> Result<T> createSuccess(T data) {
        return new Result<>(ResultCodeEnum.SUCCESS, data);
    }

    public static <T> Result<T> createFail() {
        return new Result<>(ResultCodeEnum.FAIL);
    }

    public static <T> Result<T> createFail(String msg) {
        return new Result<>(ResultCodeEnum.FAIL.getCode(), msg);
    }

    public static <T> Result<T> createFail(T data) {
        return new Result<>(ResultCodeEnum.FAIL, data);
    }

    public static <T> Result<T> createFail(String msg, T data) {
        return new Result<>(ResultCodeEnum.FAIL.getCode(), msg, data);
    }
}
