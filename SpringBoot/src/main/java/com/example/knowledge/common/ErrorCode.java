package com.example.knowledge.common;

public enum ErrorCode {
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "请先登录"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源当前状态不允许此操作"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
    FILE_TYPE_UNSUPPORTED(415, "不支持的文件类型"),
    FILE_TOO_LARGE(413, "文件不能超过 50MB"),
    DOCUMENT_PROCESS_FAILED(422, "文档处理失败"),
    AI_UNAVAILABLE(503, "AI 服务暂不可用"),
    INSUFFICIENT_CONTEXT(422, "当前知识库没有足够相关的资料");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() { return code; }
    public String message() { return message; }
}

