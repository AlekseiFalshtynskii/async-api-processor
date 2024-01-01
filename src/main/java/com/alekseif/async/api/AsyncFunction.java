package com.alekseif.async.api;

import javax.servlet.http.HttpServletRequest;

/**
 * Функция-обертка асинхронного метода
 */
@FunctionalInterface
public interface AsyncFunction<T> {

  T apply(Long id, HttpServletRequest httpRequest, Object... args);
}
