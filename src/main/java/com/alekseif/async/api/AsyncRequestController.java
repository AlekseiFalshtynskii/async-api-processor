package com.alekseif.async.api;

import static com.alekseif.async.api.AsyncRequestState.ERROR;
import static com.alekseif.async.api.AsyncRequestState.PROCESSING;
import static com.alekseif.async.api.AsyncRequestState.SUCCESS;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AsyncRequestController {

  private final AsyncRequestService asyncRequestService;
  private final AsyncRequestFileService asyncRequestFileService;

  @Operation(summary = "Получение состояния асинхронного запроса")
  @GetMapping("/async/state/{id}")
  AsyncRequestStateResponse getAsyncRequestState(
      @Parameter(name = "id", description = "id асинхронного запроса")
      @PathVariable Long id
  ) {
    var entity = asyncRequestService.getById(id);
    var status = entity.getStatus();
    var state = ERROR;
    if (isNull(status)) {
      state = PROCESSING;
    } else if (status.is2xxSuccessful()) {
      state = SUCCESS;
    }
    return AsyncRequestStateResponse.builder()
        .id(id)
        .state(state)
        .startDateTime(entity.getStartDateTime())
        .build();
  }

  @Operation(summary = "Получение асинхронного ответа")
  @GetMapping("/async/response/{id}")
  ResponseEntity<JsonNode> getAsyncResponse(
      @Parameter(name = "id", description = "id асинхронного запроса")
      @PathVariable Long id
  ) {
    var entity = asyncRequestService.getById(id);
    return status(entity.getStatus()).body(entity.getResponse());
  }

  @Operation(summary = "Получение асинхронного документа")
  @GetMapping("/async/file/{id}")
  ResponseEntity<byte[]> getAsyncFile(
      @Parameter(name = "id", description = "id асинхронного запроса")
      @PathVariable Long id
  ) {
    var entity = asyncRequestService.getById(id);
    byte[] bytes = asyncRequestFileService.getFile(entity.getFileUuid());
    var fileName = encode(entity.getFileName(), UTF_8);
    var headers = new HttpHeaders();
    headers.setContentType(APPLICATION_OCTET_STREAM);
    headers.set("Content-Disposition", "attachment; filename*=utf-8''" + fileName);
    return ok().headers(headers).body(bytes);
  }
}
