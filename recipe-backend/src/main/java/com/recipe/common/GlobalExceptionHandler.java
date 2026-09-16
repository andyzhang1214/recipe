package com.recipe.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException e) {
        return ApiResult.err(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getDefaultMessage()).orElse("参数错误");
        return ApiResult.err(400, msg);
    }

    /** 缺请求参数（@RequestParam 必填未传） */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResult<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResult.err(400, "缺少参数: " + e.getParameterName());
    }

    /** 请求体缺失 / JSON 解析失败 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return ApiResult.err(400, "请求体格式错误");
    }

    /** 参数类型不匹配（如 id 传非数字） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResult.err(400, "参数类型错误: " + e.getName());
    }

    /** 手工数字解析失败（如 body 中 familyId/ownerId 传非数字），返回 400 而非 500 */
    @ExceptionHandler(NumberFormatException.class)
    public ApiResult<Void> handleNumberFormat(NumberFormatException e) {
        return ApiResult.err(400, "参数格式错误：期望数字");
    }

    /** 接口不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResult<Void> handleNotFound(NoResourceFoundException e) {
        return ApiResult.err(404, "接口不存在");
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResult<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResult.err(405, "请求方法不支持");
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception e) {
        log.error("unexpected error", e);
        return ApiResult.err(500, "服务器开小差了，请稍后再试");
    }
}
