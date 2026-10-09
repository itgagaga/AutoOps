package com.zhiwei.superemployee.app.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局 API 异常处理：将常见业务/校验异常转为统一 JSON 错误体。
 * <p>
 * 响应体形如 {@code { "status": &lt;httpStatus&gt;, "message": "..." }}。
 * 作用于全部 {@code @RestController}，无独立路径前缀。
 * </p>
 * @author yangdb
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 登录口令错误 → 401。
     *
     * @return 统一错误体
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> badCredentials() {
        return response(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    /**
     * Bean Validation 失败 → 400，取首个字段错误信息。
     *
     * @param ex 校验异常
     * @return 统一错误体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数不合法" : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return response(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 参数非法（业务前置校验）→ 400。
     *
     * @param ex 非法参数异常
     * @return 统一错误体
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * 状态冲突或不允许的操作 → 409。
     *
     * @param ex 非法状态异常
     * @return 统一错误体
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex) {
        return response(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * 组装统一错误响应。
     *
     * @param status  HTTP 状态
     * @param message 错误文案
     * @return ResponseEntity
     */
    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
