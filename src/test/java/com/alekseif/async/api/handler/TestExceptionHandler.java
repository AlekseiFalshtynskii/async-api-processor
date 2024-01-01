package com.alekseif.async.api.handler;

import com.alekseif.async.api.AsyncRequestExceptionHandler;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@RequiredArgsConstructor
@Slf4j
public class TestExceptionHandler implements AsyncRequestExceptionHandler {

  @ResponseBody
  @ExceptionHandler(SecurityException.class)
  public Class<?> handleException(HttpServletRequest request, SecurityException exception) {
    return SecurityException.class;
  }

  @ResponseBody
  @ExceptionHandler(AccessDeniedException.class)
  public Class<?> handleException(HttpServletRequest request, AccessDeniedException exception) {
    return AccessDeniedException.class;
  }

  @ResponseBody
  @ExceptionHandler(RuntimeException.class)
  public Class<?> handleException(HttpServletRequest request, RuntimeException exception) {
    return RuntimeException.class;
  }

  @ResponseBody
  @ExceptionHandler(Exception.class)
  public Class<?> handleException(HttpServletRequest request, Exception exception) {
    return Exception.class;
  }
}
