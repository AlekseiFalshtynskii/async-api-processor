package com.alekseif.async.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Состояние асинхронной задачи")
public enum AsyncRequestState {

  PROCESSING,
  SUCCESS,
  ERROR,
}
