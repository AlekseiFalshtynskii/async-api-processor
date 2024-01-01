# Библиотека генерации асинхронных api на основе синхронных

Поддерживает Spring boot 2

## Описание работы

1. Добавить в зависимости и процессор аннотаций

* Maven

~~~
<dependencies>
    <dependency>
        <groupId>com.alekseif</groupId>
        <artifactId>async-api-processor</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>

<pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>${version}</version>
            <configuration>
                <annotationProcessorPaths>
                    <annotationProcessorPath>
                        <groupId>com.alekseif</groupId>
                        <artifactId>async-api-processor</artifactId>
                        <version>${version}</version>
                    </annotationProcessorPath>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</pluginManagement>
~~~

* Gradle

~~~
implementation      "com.alekseif:async-api-processor:${version}"
annotationProcessor "com.alekseif:async-api-processor:${version}"
~~~

2. Скопировать и выполнить liquibase скрипт создания таблицы журнала из /test/resources/liquibase/*.xml(
   yml)


3. Реализовать библиотечные интерфейсы

* Получение id пользователя для журнала

~~~
AsyncRequestUserService
~~~

* Сохранение и получение файла из внешнего хранилища

~~~
AsyncRequestFileService
~~~

* Общий обработчик всех исключений (только унаследовать, метод реализован)

~~~
AsyncRequestExceptionHandler
~~~

3. Добавить аннотацию @AsyncApi на необходимые методы @RestController

~~~
@RestController
public class TestController {

    @AsyncApi
    @GetMapping("/test")
    public String test() {
        return "test";
    }
}
~~~

4. Сгенерируется сервис асинхронных операций с копией метода контроллера. Все аннотации на методе и параметрах будут
   сохранены.

~~~
@Async
@Service
public class TestAsyncService {

    public Future<String> asyncTest(Long id, HttpServletRequest httpRequest) {
        return AsyncResult.forValue(test());
    }
    
    /**
     * Copied
     * @see TestController#test
     */
    private String test() {
        return "test";
    }
}
~~~

5. И контроллер с вызовом асинхронного сервиса. Все аннотации на методе и параметрах будут сохранены. В описании
   @Operation добавится ASYNC, в path - /async. Возвращаемым значением будет id запроса.

~~~
@RestController
public class TestAsyncController {

    private final TestAsyncService testAsyncService;

    @Operation(summary = "[ASYNC] Описание")
    @GetMapping("/test/async")
    public Long test() {
        return asyncRequestService.wrapAsyncFunction((id, hsr, args) -> {
            return testAsyncService.asyncTest(id, hsr);
        }, httpRequest);
    }
}
~~~

6. И аспект, журналирующий параметры запроса со всем пойнткатами для всех методов всех асинхронных сервисов

~~~
@Aspect
@Component
@Slf4j
public class AsyncRequestAspect extends AsyncRequestAspectBase {

    @Pointcut("execution(public * TestAsyncService.*(..))")
    public void testAsyncServicePointcut() {
    }

    @Around("testAsyncServicePointcut()")
    @Override
    public Object aroundAsyncRequest(ProceedingJoinPoint pjp) throws Throwable {
        return super.aroundAsyncRequest(pjp);
    }
}
~~~

7. Состояние запроса отслеживается по id запросом. Возможны варианты PROCESSING / SUCCESS / ERROR

~~~
GET /async/state/{id}
~~~

Получение обычного json ответа запросом. Ответом будет полностью идентичный ответ аналогичного синхронного запроса.

~~~
GET /async/response/{id}
~~~

Получение ответа с бинарными данными

~~~
GET /async/file/{id}
~~~

## Поддерживаемые настройки application.yml

* Настройки асинхронного исполнителя (по умолчанию значения spring)

~~~
async.properties.threadNamePrefix
async.properties.allowCoreThreadTimeOut
async.properties.keepAliveSeconds
async.properties.corePoolSize
async.properties.maxPoolSize
async.properties.queueCapacity
~~~

* Длительность хранения временных файлов в секундах (по умолчанию 666 секунд)

~~~
temp-file-duration-second
~~~

