package com.alekseif.async.api;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toMap;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.ExceptionHandler;

public interface AsyncRequestExceptionHandler {

  /**
   * Универсальный обработчик всех исключений для обработки асинхронных запросов
   */
  @SneakyThrows
  default Object universalHandleException(HttpServletRequest httpRequest, Exception exception) {
    var method = getMethod(exception.getClass(), getMethodMap());
    if (isNull(method)) {
      throw new NotFoundExceptionHandlerException(exception);
    }
    return method.invoke(this, httpRequest, exception);
  }

  default Map<Class<?>, Method> getMethodMap() {
    return Stream.of(getClass().getMethods())
        .flatMap(method -> {
          var annotation = method.getAnnotation(ExceptionHandler.class);
          if (isNull(annotation)) {
            return Stream.empty();
          }
          return Stream.of(annotation.value()).map(c -> Map.entry(c, method));
        })
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Method getMethod(Class<?> type, Map<Class<?>, Method> methodMap) {
    if (Objects.equals(type, Object.class)) {
      return null;
    }
    return methodMap.getOrDefault(type, getMethod(type.getSuperclass(), methodMap));
  }
}
