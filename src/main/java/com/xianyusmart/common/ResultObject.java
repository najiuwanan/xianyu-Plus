package com.xianyusmart.common;

/**
 * 统一返回结果封装类
 * @param <T> 返回数据的类型
 */
public class ResultObject<T> {
    /**
     * 状态码
     */
    private Integer code;
    
    /**
     * 消息
     */
    private String msg;
    
    /**
     * 数据
     */
    private T data;

    public ResultObject() {}

    public ResultObject(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功返回结果
     * @param data 获取的数据
     * @param <T> 数据类型
     * @return ResultObject
     */
    public static <T> ResultObject<T> success(T data) {
        return new ResultObject<T>(200, "操作成功", data);
    }

    /**
     * 成功返回结果
     * @param data 获取的数据
     * @param message 提示信息
     * @param <T> 数据类型
     * @return ResultObject
     */
    public static <T> ResultObject<T> success(T data, String message) {
        return new ResultObject<T>(200, message, data);
    }

    /**
     * 失败返回结果
     * @param message 提示信息
     * @param <T> 数据类型
     * @return ResultObject
     */
    public static <T> ResultObject<T> failed(String message) {
        return new ResultObject<T>(500, message, null);
    }

    /**
     * 失败返回结果
     * @param code 错误码
     * @param message 提示信息
     * @param <T> 数据类型
     * @return ResultObject
     */
    public static <T> ResultObject<T> failed(Integer code, String message) {
        return new ResultObject<T>(code, message, null);
    }

    /**
     * 参数验证失败返回结果
     * @param message 提示信息
     * @param <T> 数据类型
     * @return ResultObject
     */
    public static <T> ResultObject<T> validateFailed(String message) {
        return new ResultObject<T>(404, message, null);
    }

    /**
     * 未登录返回结果
     * @param data 获取的数据
     * @param <T> 数据类型
     * @return ResultObject
     */
    public static <T> ResultObject<T> unauthorized(T data) {
        return new ResultObject<T>(401, "暂未登录或token已经过期", data);
    }

    /**
     * 未授权返回结果
     * @param data 获取的数据
     * @param <T> 数据类型
     * @return ResultObject
     */
    public static <T> ResultObject<T> forbidden(T data) {
        return new ResultObject<T>(403, "没有相关权限", data);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ResultObject{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                ", data=" + data +
                '}';
    }
}