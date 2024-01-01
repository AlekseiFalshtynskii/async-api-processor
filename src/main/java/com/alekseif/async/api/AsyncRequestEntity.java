package com.alekseif.async.api;

import static javax.persistence.EnumType.STRING;
import static javax.persistence.GenerationType.IDENTITY;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.type.TextType;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "async_request")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@TypeDef(name = "text", typeClass = TextType.class)
@Data
public class AsyncRequestEntity {

  @Id
  @Column(name = "id", nullable = false)
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "api", nullable = false)
  private String api;

  @Type(type = "jsonb")
  @Column(name = "params", columnDefinition = "jsonb")
  private JsonNode params;

  @Column(name = "create_datetime", nullable = false)
  private LocalDateTime createDateTime;

  @Column(name = "start_datetime")
  private LocalDateTime startDateTime;

  @Column(name = "end_datetime")
  private LocalDateTime endDateTime;

  @Enumerated(STRING)
  @Column(name = "status")
  private HttpStatus status;

  @Type(type = "jsonb")
  @Column(name = "response", columnDefinition = "jsonb")
  private JsonNode response;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "file_uuid")
  private String fileUuid;

  @Type(type = "text")
  @Column(name = "error")
  private String error;
}
