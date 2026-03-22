package com.sudarshanchakra.auth.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method for automatic audit logging via {@link AuditAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Logical action name, e.g. {@code farm.create}. */
    String action();

    /** Entity type label stored in audit log. */
    String entityType();

    /**
     * Optional SpEL expression evaluated against method parameters ({@code #id}, {@code #request}) and
     * {@code #result} (return value). Must resolve to a string (or UUID convertible). If blank, the aspect
     * tries {@code result.id} when the return type exposes {@code getId()}.
     */
    String entityId() default "";
}
