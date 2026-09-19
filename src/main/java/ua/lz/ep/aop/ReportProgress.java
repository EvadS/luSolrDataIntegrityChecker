package ua.lz.ep.aop;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReportProgress {
    // Пустая маркерная аннотация
}
