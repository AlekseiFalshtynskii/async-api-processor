package com.alekseif.async.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alekseif.async.api.handler.TestExceptionHandler;
import java.util.stream.Stream;
import javax.persistence.EntityNotFoundException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

class AsyncRequestExceptionHandlerTest {

  private static final TestExceptionHandler HANDLER = new TestExceptionHandler();

  @ParameterizedTest
  @MethodSource("testUniversalHandleException_arguments")
  void testUniversalHandleException(Exception exception, Class<?> expected) {
    assertEquals(expected, HANDLER.universalHandleException(null, exception));
  }

  private static Stream<Arguments> testUniversalHandleException_arguments() {
    return Stream.of(
        Arguments.of(new SecurityException(), SecurityException.class),
        Arguments.of(new AccessDeniedException(null), AccessDeniedException.class),
        Arguments.of(new EntityNotFoundException(), RuntimeException.class),
        Arguments.of(new RuntimeException(), RuntimeException.class),
        Arguments.of(new Exception(), Exception.class)
    );
  }
}
