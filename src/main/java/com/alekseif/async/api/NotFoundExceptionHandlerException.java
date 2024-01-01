package com.alekseif.async.api;

public class NotFoundExceptionHandlerException extends RuntimeException {

  public NotFoundExceptionHandlerException(Exception exception) {
    super("Не найден обработчик исключения %s".formatted(exception.getClass().getName()));
  }
}
