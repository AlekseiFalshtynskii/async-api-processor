package com.alekseif.async.api;

import static com.github.javaparser.ast.Modifier.Keyword.PRIVATE;
import static com.github.javaparser.ast.Modifier.Keyword.PUBLIC;
import static com.sun.source.util.Trees.instance;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.SourceVersion.latest;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.tools.Diagnostic.Kind.NOTE;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.uncapitalize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.UnknownType;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Types;
import javax.servlet.http.HttpServletRequest;
import javax.tools.JavaFileObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@SupportedAnnotationTypes("com.alekseif.async.api.AsyncApi")
public class AsyncApiProcessor extends AbstractProcessor {

  private static final JavaParser JAVA_PARSER = new JavaParser();
  private static final String HTTP_REQUEST = "httpRequest";
  private Types types;
  private Trees trees;
  private String packageName;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return latest();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    types = processingEnv.getTypeUtils();
    trees = instance(processingEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (isEmpty(annotations)) {
      return true;
    }
    var app = roundEnv.getElementsAnnotatedWith(SpringBootApplication.class).iterator().next();
    packageName = processingEnv.getElementUtils().getPackageOf(app).toString();
    processingEnv.getMessager().printMessage(NOTE, annotations.toString());
    var services = processControllers(annotations, roundEnv);
    processAspect(services);
    return true;
  }

  private List<CompilationUnit> processControllers(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    var annotatedElements = roundEnv.getElementsAnnotatedWith(annotations.iterator().next());

    var annotatedClasses = Stream.concat(
        annotatedElements.stream().filter(e -> e.getKind().isClass()),
        annotatedElements.stream().filter(e -> e.getKind().isInterface())
            .map(e -> getClassesByInterface(e, roundEnv))
            .flatMap(Set::stream)
    ).collect(toSet());

    var annotatedMethods = Stream.concat(
        annotatedElements.stream()
            .filter(e -> e.getKind() == METHOD)
            .filter(e -> e.getEnclosingElement().getKind().isClass())
            .filter(e -> !annotatedClasses.contains(e.getEnclosingElement()))
            .map(e -> Pair.of(e.getEnclosingElement(), e)),
        annotatedElements.stream()
            .filter(e -> e.getKind() == METHOD)
            .filter(e -> e.getEnclosingElement().getKind().isInterface())
            .filter(e -> annotatedClasses.stream()
                .noneMatch(c -> types.isSubtype(c.asType(), e.getEnclosingElement().asType())))
            .flatMap(e -> getClassesByInterface(e.getEnclosingElement(), roundEnv).stream().map(c -> Pair.of(c, e)))
    ).collect(groupingBy(Pair::getLeft, mapping(Pair::getRight, toList())));

    return Stream.concat(
            annotatedClasses.stream()
                .map(c -> process(c, emptyList(), roundEnv)),
            annotatedMethods.entrySet().stream()
                .map(entry -> process(entry.getKey(), entry.getValue(), roundEnv))
        )
        .toList();
  }

  @SneakyThrows
  private CompilationUnit process(Element sourceElement, List<? extends Element> methodElements,
      RoundEnvironment roundEnv) {
    var source = parse(sourceElement);

    var annotatedMethodSignatureMap = methodElements.stream()
        .map(element -> {
          var name = element.getSimpleName().toString();
          var signature = getMethodSignature(element);
          return Pair.of(name, signature);
        })
        .collect(groupingBy(Pair::getLeft, mapping(Pair::getRight, toList())));

    var cMethodsMap = getMethods(source);

    var interfacesNames = getClassDeclaration(source).getImplementedTypes().stream()
        .map(this::getName)
        .toList();
    var interfaces = parse(roundEnv.getRootElements().stream()
        .filter(element -> interfacesNames.contains(element.getSimpleName().toString())));
    var iMethodsMap = getMethods(interfaces);

    var service = createAsyncService(source);
    var controller = createAsyncController(source, interfaces, service);

    copyControllerMethods(cMethodsMap, iMethodsMap, methodElements, annotatedMethodSignatureMap, source, service,
        controller);
    copyAnotherControllerMembers(source, service);
    copyAnotherInterfaceMembers(interfaces, service);

    write(service);
    write(controller);
    return service;
  }

  private CompilationUnit createAsyncService(CompilationUnit sourceController) {
    var unit = new CompilationUnit();
    unit.setPackageDeclaration(packageName + ".service.async");

    var serviceName = getName(sourceController).replace("Impl", "").replace("Controller", "AsyncService");
    var service = unit.addClass(serviceName);

    unit.setImports(sourceController.getImports());
    unit.addImport(AsyncResult.class);
    unit.addImport(Future.class);
    unit.addImport(HttpServletRequest.class);

    service.addMarkerAnnotation(Async.class);
    service.addMarkerAnnotation(Service.class);
    service.addMarkerAnnotation(RequiredArgsConstructor.class);
    service.addMarkerAnnotation(Slf4j.class);

    service.setMembers(
        getClassDeclaration(sourceController).getMembers().stream()
            .filter(BodyDeclaration::isFieldDeclaration)
            .collect(toCollection(NodeList::new))
    );
    return unit;
  }

  private CompilationUnit createAsyncController(CompilationUnit source, List<CompilationUnit> interfaces,
      CompilationUnit service) {
    var unit = new CompilationUnit();
    unit.setPackageDeclaration(packageName + ".controller.async");

    var controllerName = getName(source)
        .replace("Impl", "")
        .replace("Controller", "AsyncController");
    var controller = unit.addClass(controllerName);

    unit.setImports(source.getImports());
    unit.addImport(getPackageName(service) + "." + getName(service));
    unit.addImport(AsyncRequestService.class);
    for (var i : interfaces) {
      unit.getImports().addAll(i.getImports());
    }

    interfaces.stream()
        .map(this::getClassDeclaration)
        .map(c -> c.getAnnotationByClass(Tag.class))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .ifPresent(tag -> {
              var annotation = (NormalAnnotationExpr) tag.clone();
              annotation.getPairs().stream()
                  .filter(pair -> Objects.equals("name", getName(pair)))
                  .forEach(this::addAsync);
              controller.addAnnotation(annotation);
            }
        );
    controller.addMarkerAnnotation(RestController.class);
    controller.addMarkerAnnotation(RequiredArgsConstructor.class);
    controller.addMarkerAnnotation(Slf4j.class);

    controller.addField("AsyncRequestService", "asyncRequestService")
        .setPrivate(true)
        .setFinal(true);
    controller.addField(getName(service), uncapitalize(getName(service)))
        .setPrivate(true)
        .setFinal(true);
    return unit;
  }

  private void copyControllerMethods(
      Map<String, List<MethodDeclaration>> cMethodsMap,
      Map<String, List<MethodDeclaration>> iMethodsMap,
      List<? extends Element> methodElements,
      Map<String, List<String>> annotatedMethodSignatureMap,
      CompilationUnit source,
      CompilationUnit service,
      CompilationUnit controller
  ) {
    for (var methodName : cMethodsMap.keySet()) {
      var cMethods = getMethods(cMethodsMap, methodElements, annotatedMethodSignatureMap, methodName);
      var iMethods = getMethods(iMethodsMap, methodElements, annotatedMethodSignatureMap, methodName);

      var asyncMethodName = "async" + capitalize(methodName);

      for (int i = 0; i < cMethods.size(); i++) {
        var cMethod = cMethods.get(i);
        var iMethod = isNotEmpty(iMethods) ? iMethods.get(i) : null;
        addMethodAsyncService(getClassDeclaration(service), cMethod, asyncMethodName, source);
        addMethodAsyncController(getClassDeclaration(controller), cMethod, iMethod, getName(service), asyncMethodName);
      }
    }
  }

  private void addMethodAsyncService(ClassOrInterfaceDeclaration service, MethodDeclaration method,
      String methodName, CompilationUnit source) {

    var arguments = method.getParameters().stream().map(Parameter::getNameAsExpression).toArray(Expression[]::new);

    var parameters = copyParameters(method)
        .addFirst(new Parameter(new TypeParameter("HttpServletRequest"), HTTP_REQUEST))
        .addFirst(new Parameter(new TypeParameter("Long"), "id"));
    service.addMethod(methodName, PUBLIC)
        .setBody(new BlockStmt()
            .addStatement(new ReturnStmt(
                new MethodCallExpr("AsyncResult.forValue", new MethodCallExpr(getName(method), arguments)))))
        .setParameters(parameters)
        .setType(new TypeParameter("Future<%s>".formatted(method.getTypeAsString())))
    ;

    copyMethodController(service, method, source);
  }

  private void addMethodAsyncController(ClassOrInterfaceDeclaration controller, MethodDeclaration cMethod,
      MethodDeclaration iMethod, String serviceName, String methodName) {
    var lambdaArguments = new NodeList<>(new NameExpr("id"), new NameExpr("hsr"));
    for (int i = 0; i < cMethod.getParameters().size(); i++) {
      var param = cMethod.getParameter(i);
      lambdaArguments.add(new NameExpr("(%s) args[%d]".formatted(param.getTypeAsString(), i)));
    }

    var arguments = new NodeList<>(
        new LambdaExpr(
            new NodeList<>(
                new Parameter(new UnknownType(), "id"),
                new Parameter(new UnknownType(), "hsr"),
                new Parameter(new UnknownType(), "args")
            ),
            new BlockStmt().addStatement(new ReturnStmt(
                new MethodCallExpr(new NameExpr(uncapitalize(serviceName)), methodName,
                    lambdaArguments.stream().collect(toCollection(NodeList::new)))))
        ),
        new NameExpr(HTTP_REQUEST)
    );
    for (int i = 0; i < cMethod.getParameters().size(); i++) {
      var param = cMethod.getParameter(i);
      arguments.add(param.getNameAsExpression());
    }
    var method = cMethod.clone().setName(methodName)
        .setBody(new BlockStmt().addStatement(
            new ReturnStmt(
                new MethodCallExpr(
                    new NameExpr("asyncRequestService"),
                    "wrapAsyncFunction",
                    arguments
                ))))
        .setPublic(true)
        .setType(new TypeParameter("Long"));

    var annotations = concatAnnotations(cMethod, iMethod).stream()
        .filter(AsyncApiUtils.ANNOTATION_FILTER)
        .map(this::updateAnnotation)
        .sorted(AsyncApiUtils.METHOD_ANNOTATION_COMPARATOR)
        .collect(toCollection(NodeList::new));
    method.setAnnotations(annotations);

    for (int i = 0; i < method.getParameters().size(); i++) {
      var cParameter = cMethod.getParameter(i);
      var iParameter = nonNull(iMethod) ? iMethod.getParameter(i) : null;
      annotations = concatAnnotations(cParameter, iParameter).stream()
          .sorted(AsyncApiUtils.PARAMETER_ANNOTATION_COMPARATOR)
          .collect(toCollection(NodeList::new));
      method.getParameter(i).setAnnotations(annotations);
    }
    method.addParameter(HttpServletRequest.class, HTTP_REQUEST);
    controller.addMember(method);
  }

  private void copyMethodController(ClassOrInterfaceDeclaration service, MethodDeclaration method,
      CompilationUnit source) {
    var copiedMethod = service.addMethod(getName(method), PRIVATE)
        .setType(method.getType())
        .setParameters(copyParameters(method))
        .setBody(method.getBody().orElseThrow())
        .setJavadocComment("Copied%n@see %s.%s#%s".formatted(getPackageName(source), getName(source), getName(method)));
    if (isNotEmpty(method.getThrownExceptions())) {
      copiedMethod.addMarkerAnnotation(SneakyThrows.class);
    }
  }

  private NodeList<Parameter> copyParameters(MethodDeclaration method) {
    return method.getParameters().stream()
        .map(parameter -> new Parameter(parameter.getType(), parameter.getName()))
        .collect(toCollection(NodeList::new));
  }

  private NodeList<AnnotationExpr> concatAnnotations(NodeWithAnnotations<? extends Node> withAnnotations1,
      NodeWithAnnotations<? extends Node> withAnnotations2) {
    var annotationMap = Stream.concat(
            ofNullable(withAnnotations1).map(NodeWithAnnotations::getAnnotations).stream().flatMap(NodeList::stream),
            ofNullable(withAnnotations2).map(NodeWithAnnotations::getAnnotations).stream().flatMap(NodeList::stream)
        )
        .collect(groupingBy(AnnotationExpr::getNameAsString, toSet()));
    return annotationMap.values().stream()
        .map(value -> {
          if (value.size() > 1) {
            return value.stream().max(comparing(String::valueOf)).orElseThrow();
          }
          return value.iterator().next();
        })
        .collect(toCollection(NodeList::new));
  }

  private AnnotationExpr updateAnnotation(AnnotationExpr annotationExpr) {
    if (AsyncApiUtils.isMappingAnnotation(annotationExpr)) {
      return toAsyncMappingAnnotation(annotationExpr);
    } else if (AsyncApiUtils.isOperationAnnotation(annotationExpr)) {
      return updateOperationAnnotation(annotationExpr);
    }
    return annotationExpr;
  }

  private AnnotationExpr toAsyncMappingAnnotation(AnnotationExpr annotationExpr) {
    String path;
    if (annotationExpr instanceof SingleMemberAnnotationExpr annotation) {
      path = ((StringLiteralExpr) annotation.getMemberValue()).getValue();
    } else {
      var annotation = (NormalAnnotationExpr) annotationExpr;
      var memberValuePair = annotation.getPairs().stream()
          .filter(pair -> Set.of("path", "value").contains(getName(pair)))
          .findFirst()
          .orElseThrow();
      path = ((StringLiteralExpr) memberValuePair.getValue()).getValue();
    }
    path = path.replaceFirst("/?(v\\d*/)?([\\w-]+)(/.+)?", "/$1$2/async$3");
    return new SingleMemberAnnotationExpr(annotationExpr.getName(), new StringLiteralExpr(path));
  }

  private AnnotationExpr updateOperationAnnotation(AnnotationExpr annotationExpr) {
    var annotation = (NormalAnnotationExpr) annotationExpr;
    annotation.setPairs(
        annotation.getPairs().stream()
            .filter(pair -> Set.of("summary", "description").contains(getName(pair)))
            .map(pair -> {
              if (Objects.equals("summary", pair.getNameAsString())) {
                return addAsync(pair);
              }
              return pair;
            })
            .collect(toCollection(NodeList::new))
    );
    return annotation;
  }

  private MemberValuePair addAsync(MemberValuePair pair) {
    pair.setValue(new NameExpr(pair.getValue().toString().replaceFirst("\"", "\"[ASYNC] ")));
    return pair;
  }

  private void copyAnotherControllerMembers(CompilationUnit source, CompilationUnit service) {
    for (var member : getClassDeclaration(source).getMembers()) {
      if (member.isMethodDeclaration()) {
        var method = member.asMethodDeclaration();
        var signature = getOriginalMethodSignature(method);
        if (isEmpty(getClassDeclaration(service).getMethodsBySignature(getName(method), signature))) {
          copyMethodController(getClassDeclaration(service), method, source);
        }
      }
      if (member.isClassOrInterfaceDeclaration()) {
        var import1 = String.join(".", getPackageName(source), getName(source),
            getName(member.asClassOrInterfaceDeclaration()));
        service.addImport(import1);
      }
    }
  }

  private void copyAnotherInterfaceMembers(List<CompilationUnit> interfaces, CompilationUnit service) {
    for (var interface1 : interfaces) {
      for (var member : getClassDeclaration(interface1).getMembers()) {
        if (member.isMethodDeclaration()
            && (member.asMethodDeclaration().isDefault() || member.asMethodDeclaration().isStatic())) {
          getClassDeclaration(service).addMember(member);
        }
        if (member.isClassOrInterfaceDeclaration()) {
          var import1 = String.join(".", getPackageName(interface1), getName(interface1),
              getName(member.asClassOrInterfaceDeclaration()));
          service.addImport(import1);
        }
      }
    }
  }

  private void processAspect(Collection<CompilationUnit> services) {
    var unit = new CompilationUnit();
    unit.setPackageDeclaration(packageName + ".aspect.async");

    unit.addImport(AsyncRequestService.class);
    unit.addImport(AsyncRequestFileService.class);
    unit.addImport(AsyncRequestExceptionHandler.class);
    unit.addImport(ObjectMapper.class);

    var aspect = unit.addClass("AsyncRequestAspect");
    aspect.addMarkerAnnotation(Aspect.class);
    aspect.addMarkerAnnotation(Component.class);
    aspect.addMarkerAnnotation(Slf4j.class);

    aspect.addExtendedType(AsyncRequestAspectBase.class);

    aspect.addConstructor(PUBLIC)
        .addParameter(getName(AsyncRequestService.class), uncapitalize(getName(AsyncRequestService.class)))
        .addParameter(getName(AsyncRequestFileService.class), uncapitalize(getName(AsyncRequestFileService.class)))
        .addParameter(getName(AsyncRequestExceptionHandler.class),
            uncapitalize(getName(AsyncRequestExceptionHandler.class)))
        .addParameter(getName(ObjectMapper.class), uncapitalize(getName(ObjectMapper.class)))
        .setBody(new BlockStmt()
            .addStatement(new MethodCallExpr("super",
                new NameExpr(uncapitalize(getName(AsyncRequestService.class))),
                new NameExpr(uncapitalize(getName(AsyncRequestFileService.class))),
                new NameExpr(uncapitalize(getName(AsyncRequestExceptionHandler.class))),
                new NameExpr(uncapitalize(getName(ObjectMapper.class)))
            )));

    var pointcutNames = services.stream()
        .map(service -> {
          var pointcutName = uncapitalize(getName(service)) + "Pointcut";
          aspect.addMethod(pointcutName, PUBLIC)
              .addSingleMemberAnnotation(Pointcut.class,
                  "\"execution(public * %s.%s.*(..))\"".formatted(getPackageName(service), getName(service)));
          return pointcutName;
        })
        .toList();

    var aroundValue = pointcutNames.stream()
        .map(pointcutName -> pointcutName + "()")
        .collect(joining(" || "));
    aspect.addMethod("aroundAsyncRequest", PUBLIC)
        .addSingleMemberAnnotation(Around.class, putInQuotes(aroundValue))
        .addMarkerAnnotation(Override.class)
        .addParameter(ProceedingJoinPoint.class, "pjp")
        .addThrownException(Throwable.class)
        .setType(Object.class)
        .setBody(
            new BlockStmt().addStatement(
                new ReturnStmt(
                    new MethodCallExpr("super.aroundAsyncRequest", new NameExpr("pjp"))
                )
            )
        );
    write(unit);
  }

  @SneakyThrows
  private void write(CompilationUnit unit) {
    unit.getImports().sort(comparing(ImportDeclaration::isStatic).thenComparing(this::getName));
    var javaFileObject = processingEnv.getFiler()
        .createSourceFile(getPackageName(unit) + "." + getName(unit));
    processingEnv.getMessager().printMessage(NOTE, "Creating " + javaFileObject.toUri());
    var writer = javaFileObject.openWriter();
    var printWriter = new PrintWriter(writer);
    printWriter.println(unit);
    printWriter.flush();
    printWriter.close();
  }

  private Set<? extends Element> getClassesByInterface(Element interface1, RoundEnvironment roundEnv) {
    return roundEnv.getRootElements().stream()
        .filter(e -> !types.isSameType(e.asType(), interface1.asType()))
        .filter(e -> types.isSubtype(e.asType(), interface1.asType()))
        .collect(toSet());
  }

  private CompilationUnit parse(Element element) {
    return parse(List.of(element)).get(0);
  }

  private List<CompilationUnit> parse(Collection<? extends Element> elements) {
    return parse(elements.stream());
  }

  private List<CompilationUnit> parse(Stream<? extends Element> elements) {
    return elements
        .map(trees::getPath)
        .map(TreePath::getCompilationUnit)
        .map(CompilationUnitTree::getSourceFile)
        .map(this::safeOpenInputStream)
        .map(JAVA_PARSER::parse)
        .map(ParseResult::getResult)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  private String putInQuotes(String str) {
    return "\"%s\"".formatted(str);
  }

  @SneakyThrows
  private InputStream safeOpenInputStream(JavaFileObject javaFileObject) {
    return javaFileObject.openInputStream();
  }

  private String getMethodSignature(Element method) {
    return cleanParams(((ExecutableType) method.asType()).getParameterTypes().stream().map(String::valueOf));
  }

  private String getMethodSignature(MethodDeclaration method) {
    return cleanParams(method.getParameters().stream().map(Parameter::getTypeAsString));
  }

  private String cleanParams(Stream<String> stream) {
    return stream.map(this::cleanParam).collect(joining(","));
  }

  private String cleanParam(String param) {
    return param.replaceAll("\\@?(\\w+\\s)?(\\w+\\.)", "");
  }

  private String[] getOriginalMethodSignature(MethodDeclaration method) {
    return method.getParameters().stream()
        .map(Parameter::getTypeAsString)
        .toArray(String[]::new);
  }

  private String getName(Class<?> type) {
    return type.getSimpleName();
  }

  private String getName(CompilationUnit unit) {
    return getName(getClassDeclaration(unit));
  }

  private String getName(NodeWithSimpleName<?> node) {
    return node.getNameAsString();
  }

  private String getName(NodeWithName<?> node) {
    return node.getNameAsString();
  }

  private String getName(Optional<? extends NodeWithName<? extends Node>> optionalNode) {
    return optionalNode.map(this::getName).orElseThrow();
  }

  private String getPackageName(CompilationUnit unit) {
    return getName(unit.getPackageDeclaration());
  }

  private ClassOrInterfaceDeclaration getClassDeclaration(CompilationUnit unit) {
    return (ClassOrInterfaceDeclaration) unit.getType(0);
  }

  private Map<String, List<MethodDeclaration>> getMethods(CompilationUnit unit) {
    return getMethods(List.of(unit));
  }

  private Map<String, List<MethodDeclaration>> getMethods(Collection<CompilationUnit> units) {
    return units.stream()
        .map(this::getClassDeclaration)
        .map(TypeDeclaration::getMethods)
        .flatMap(List::stream)
        .collect(groupingBy(this::getName, LinkedHashMap::new, toList()));
  }

  private List<MethodDeclaration> getMethods(Map<String, List<MethodDeclaration>> methodsMap,
      List<? extends Element> methodElements, Map<String, List<String>> signatureMap, String methodName) {
    return methodsMap.getOrDefault(methodName, emptyList()).stream()
        .filter(method -> isEmpty(methodElements)
            || signatureMap.getOrDefault(methodName, emptyList()).contains(getMethodSignature(method)))
        .toList();
  }
}
