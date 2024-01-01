package com.alekseif.async.api;

import java.time.LocalDateTime;

public interface AsyncRequestFileService {

  String uploadTempFile(byte[] content, LocalDateTime expirationDateTime);

  byte[] getFile(String uuid);
}
