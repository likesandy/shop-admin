package com.acme.admin.common;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(Problem.class) ResponseEntity<Api<Void>> problem(Problem e){return ResponseEntity.status(e.status).body(new Api<>(null,e.getMessage()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Api<Void>> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(new Api<>(null,e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).findFirst().orElse("参数错误")));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Api<Void>> conflict(){return ResponseEntity.status(409).body(new Api<>(null,"记录重复或仍被其他数据引用"));}
 @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<Api<Void>> malformed(){return ResponseEntity.badRequest().body(new Api<>(null,"请求格式错误"));}
}
