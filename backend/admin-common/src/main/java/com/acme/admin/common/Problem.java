package com.acme.admin.common;
import org.springframework.http.HttpStatus;
public class Problem extends RuntimeException {
 public final HttpStatus status;
 public Problem(HttpStatus status,String message){super(message);this.status=status;}
 public static Problem bad(String message){return new Problem(HttpStatus.BAD_REQUEST,message);}
 public static Problem forbidden(){return new Problem(HttpStatus.FORBIDDEN,"无权访问该资源");}
 public static Problem missing(){return new Problem(HttpStatus.NOT_FOUND,"记录不存在");}
}
