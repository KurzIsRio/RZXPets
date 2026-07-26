package me.rzx.core.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RZXPluginMeta {
    String name();
    String version();
    int api() default 1;
    String[] authors() default {};
}
