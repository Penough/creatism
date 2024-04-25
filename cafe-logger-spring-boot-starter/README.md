# spring-boot-cafe-logger-starter


cafe-logger
## I.INTRODUCE

cafe-logger-spring-boot-starter is based on springboot and logback. It provides an out-of-the-box asynchronous log component and supports kafka log sending. Mainly include the following:

- Configurable uri matching and method input parameter [request] and output parameter [response] log interception based on RestController
- Method-level log enhancement based on configured cut-off points, which can record method-level input and output parameters, time-consuming and other parameters
- OpenFeign client request/return log enhancement based on OkHttpClient

## II.quickstart

### maven
```xml
<dependency>
    <groupId>com.akulaku.risk</groupId>
    <artifactId>spring-boot-cafe-logger-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
### configuration
<table>
<tr>
    <th>configuration</th>
    <th>description</th>
    <th>demo</th>
</tr>
<tr>
    <td>uri</td>
    <td>controller-api path（support pattern）</td>
    <td>/app/slider/pic</td>
</tr>
<tr>
    <td>feign-uri</td>
    <td>feign request uri</td>
    <td>**/slider/recall</td>
</tr>
<tr>
    <td>topic</td>
    <td>kafka topic name</td>
    <td>test0</td>
</tr>
<tr>
    <td>pointcut</td>
    <td>pointcut pattern</td>
    <td>execution(**)</td>
</tr>
</table>

### configuration demo
```yaml
cafe-logger:
  logger-points:
    - uri: "/app/api"
      feign-uri: "**/api/recall"
      pointcut: "execution(**)"
      topic: test0
```

## III.Embedded Kafka Asynchronous Appender


cafe-logger has a built-in `com.akulaku.risk.cafelogger.appender.CafeLoggerKafkaAsyncAppender` asynchronous appender for logback to configure a customized logger. The configuration example is as follows, you need to specify the topic (Kafka)
```xml
<appender name="LOGSTASH_ASYNC" class="com.akulaku.risk.cafelogger.appender.CafeLoggerKafkaAsyncAppender">
    <discardingThreshold>0</discardingThreshold>
    <queueSize>256</queueSize>
    <includeCallerData>true</includeCallerData>
    <topic>test0</topic>
    <appender-ref ref ="LOGSTASH_FILE"/>
</appender>
```

