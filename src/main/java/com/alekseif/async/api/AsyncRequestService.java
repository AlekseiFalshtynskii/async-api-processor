package com.alekseif.async.api;

import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Objects;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncRequestService {

  private final AsyncRequestRepository asyncRequestRepository;
  private final AsyncRequestUserService asyncRequestUserService;
  private final ObjectMapper objectMapper;

  public <T> Long wrapAsyncFunction(AsyncFunction<T> asyncFunc, HttpServletRequest httpRequest, Object... args) {
    var id = create(httpRequest, args);
    try {
      asyncFunc.apply(id, httpRequest, args);
    } catch (TaskRejectedException e) {
      saveError(id, INTERNAL_SERVER_ERROR, null, e);
      throw new TaskRejectedException("Повышенная нагрузка, повторите позже", e);
    }
    return id;
  }

  public AsyncRequestEntity getById(Long id) {
    return asyncRequestRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("async_request not found by id = " + id));
  }

  public Long create(HttpServletRequest request, Object... args) {
    var params = new HashMap<String, Object>();
    if (isNotEmpty(request.getParameterMap())) {
      params.put("query_string", request.getQueryString());
    }
    if (!Objects.equals(GET.name(), request.getMethod()) && getLength(args) > 0) {
      for (int i = 0; i < args.length; i++) {
        params.put("body" + (i == 0 ? "" : i), args[i]);
      }
    }
    var entity = new AsyncRequestEntity();
    entity.setUserId(asyncRequestUserService.getUserId());
    entity.setApi(request.getRequestURI());
    if (isNotEmpty(params)) {
      entity.setParams(objectMapper.convertValue(params, JsonNode.class));
    }
    entity.setCreateDateTime(LocalDateTime.now());
    return asyncRequestRepository.save(entity).getId();
  }

  public void start(Long id) {
    var entity = getById(id);
    entity.setStartDateTime(LocalDateTime.now());
    asyncRequestRepository.save(entity);
  }

  public void saveSuccess(Long id, HttpStatus status, JsonNode response) {
    var entity = getById(id);
    entity.setEndDateTime(LocalDateTime.now());
    entity.setStatus(status);
    entity.setResponse(response);
    asyncRequestRepository.save(entity);
  }

  public void saveError(Long id, HttpStatus status, JsonNode response, Exception e) {
    var entity = getById(id);
    entity.setEndDateTime(LocalDateTime.now());
    entity.setStatus(status);
    entity.setResponse(response);
    entity.setError(getStackTrace(e));
    asyncRequestRepository.save(entity);
  }

  public void saveFile(Long id, HttpStatus status, String fileName, String fileUuid) {
    var entity = getById(id);
    entity.setEndDateTime(LocalDateTime.now());
    entity.setStatus(status);
    entity.setFileName(fileName);
    entity.setFileUuid(fileUuid);
    asyncRequestRepository.save(entity);
  }
}
