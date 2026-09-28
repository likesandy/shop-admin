package com.acme.admin.common;
import java.lang.annotation.*;
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface Audited { String value(); }
