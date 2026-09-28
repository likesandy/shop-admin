package com.acme.admin.common;
public record Api<T>(T data, String message) {
 public static <T> Api<T> ok(T data) { return new Api<>(data, "ok"); }
}
