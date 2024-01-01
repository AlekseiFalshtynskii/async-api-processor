package com.alekseif.async.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Статус асинхронного запроса")
@Data
@Builder
public class AsyncRequestStateResponse {

  @Schema(description = "id запроса")
  private Long id;

  @Schema(description = "Статус запроса")
  private AsyncRequestState state;

  @Schema(description = "Дата время старта запроса")
  private LocalDateTime startDateTime;
}
