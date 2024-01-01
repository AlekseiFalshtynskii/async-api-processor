package com.alekseif.async.api;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.OK;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.AsyncResult;

@RequiredArgsConstructor
@Slf4j
public class AsyncRequestAspectBase {

  private final AsyncRequestService asyncRequestService;
  private final AsyncRequestFileService asyncRequestFileService;
  private final AsyncRequestExceptionHandler asyncRequestExceptionHandler;
  private final ObjectMapper objectMapper;

  @Value("${temp-file-duration-second:666}")
  private long tempFileDurationSecond;

  @SuppressWarnings("unchecked")
  public Object aroundAsyncRequest(ProceedingJoinPoint pjp) throws Throwable {
    var args = pjp.getArgs();
    var id = (Long) args[0];
    var httpRequest = (HttpServletRequest) args[1];
    var status = OK;
    AsyncResult<Object> asyncResult;
    Object result;
    Object responseBody;
    try {
      asyncRequestService.start(id);
      asyncResult = (AsyncResult<Object>) pjp.proceed(args);
      result = asyncResult.get();
      responseBody = result;
      if (result instanceof ResponseEntity) {
        status = ((ResponseEntity<Object>) result).getStatusCode();
        responseBody = ((ResponseEntity<Object>) result).getBody();
        if (responseBody instanceof byte[]) {
          return handleFile(id, asyncResult, (ResponseEntity<byte[]>) result);
        }
      }
      var json = ofNullable(responseBody).map(body -> objectMapper.convertValue(body, JsonNode.class)).orElse(null);
      asyncRequestService.saveSuccess(id, status, json);
      return asyncResult;
    } catch (Exception e) {
      result = asyncRequestExceptionHandler.universalHandleException(httpRequest, e);
      JsonNode json;
      if (result instanceof ResponseEntity) {
        status = ((ResponseEntity<Object>) result).getStatusCode();
        responseBody = ((ResponseEntity<Object>) result).getBody();
        json = objectMapper.convertValue(responseBody, JsonNode.class);
      } else {
        json = objectMapper.convertValue(result, JsonNode.class);
      }
      asyncRequestService.saveError(id, status, json, e);
      throw e;
    }
  }

  private AsyncResult<Object> handleFile(Long id, AsyncResult<Object> asyncResult, ResponseEntity<byte[]> result) {
    var status = result.getStatusCode();
    var content = result.getBody();
    var fileUuid = asyncRequestFileService.uploadTempFile(content, LocalDateTime.now().plusSeconds(tempFileDurationSecond));
    var fileName = result.getHeaders().getContentDisposition().getFilename();
    asyncRequestService.saveFile(id, status, fileName, fileUuid);
    return asyncResult;
  }
}
