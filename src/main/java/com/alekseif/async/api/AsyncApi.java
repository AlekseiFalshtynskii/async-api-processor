package com.alekseif.async.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Аннотация для генерации асинхронной копии api
 */
@Target({TYPE, METHOD})
@Retention(SOURCE)
public @interface AsyncApi {

}
