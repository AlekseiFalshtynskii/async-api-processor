package com.alekseif.async.api;

import static java.util.Comparator.comparing;
import static java.util.Map.entry;
import static lombok.AccessLevel.PRIVATE;

import com.github.javaparser.ast.expr.AnnotationExpr;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.transaction.Transactional;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@NoArgsConstructor(access = PRIVATE)
class AsyncApiUtils {

  private static final Set<String> IGNORED_ANNOTATIONS = Set.of(
      AsyncApi.class.getSimpleName(),
      Override.class.getSimpleName()
  );

  static final Predicate<AnnotationExpr> ANNOTATION_FILTER =
      annotationExpr -> !IGNORED_ANNOTATIONS.contains(annotationExpr.getNameAsString());

  private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
      GetMapping.class.getSimpleName(),
      PostMapping.class.getSimpleName(),
      PutMapping.class.getSimpleName(),
      DeleteMapping.class.getSimpleName()
  );

  public static boolean isMappingAnnotation(AnnotationExpr annotationExpr) {
    return MAPPING_ANNOTATIONS.contains(annotationExpr.getNameAsString());
  }

  public static boolean isOperationAnnotation(AnnotationExpr annotationExpr) {
    return Objects.equals(Operation.class.getSimpleName(), annotationExpr.getNameAsString());
  }

  static final Comparator<AnnotationExpr> METHOD_ANNOTATION_COMPARATOR =
      comparing(AsyncApiUtils::getMethodAnnotationPriority);


  static final Comparator<AnnotationExpr> PARAMETER_ANNOTATION_COMPARATOR =
      comparing(AsyncApiUtils::getParameterAnnotationPriority);

  private static int getMethodAnnotationPriority(AnnotationExpr annotationExpr) {
    return getAnnotationPriority(METHOD_ANNOTATION_PRIORITY, annotationExpr);
  }

  private static int getParameterAnnotationPriority(AnnotationExpr annotationExpr) {
    return getAnnotationPriority(PARAMETER_ANNOTATION_PRIORITY, annotationExpr);
  }

  private static int getAnnotationPriority(Map<String, Integer> prioritiesMap, AnnotationExpr annotationExpr) {
    return prioritiesMap.getOrDefault(annotationExpr.getNameAsString(), 666);
  }

  private static final Map<String, Integer> METHOD_ANNOTATION_PRIORITY = Map.ofEntries(
      entryPriority(Operation.class.getSimpleName()),
      entryPriority(Parameters.class.getSimpleName()),
      entryPriority(Parameter.class.getSimpleName()),
      entryPriority(GetMapping.class.getSimpleName()),
      entryPriority(PostMapping.class.getSimpleName()),
      entryPriority(PutMapping.class.getSimpleName()),
      entryPriority(DeleteMapping.class.getSimpleName()),
      entryPriority(ResponseStatus.class.getSimpleName()),
      entryPriority(PreAuthorize.class.getSimpleName()),
      entryPriority(Transactional.class.getSimpleName())
  );

  private static final Map<String, Integer> PARAMETER_ANNOTATION_PRIORITY = Map.ofEntries(
      entryPriority(Parameter.class.getSimpleName()),
      entryPriority(PathVariable.class.getSimpleName()),
      entryPriority(RequestBody.class.getSimpleName()),
      entryPriority(RequestParam.class.getSimpleName()),
      entryPriority(DateTimeFormat.class.getSimpleName()),
      entryPriority("NotNull"),
      entryPriority("NonNull"),
      entryPriority("Nonnull"),
      entryPriority("Nullable")
  );

  private static int priority = 1;

  private static Map.Entry<String, Integer> entryPriority(String key) {
    return entry(key, priority++);
  }
}
