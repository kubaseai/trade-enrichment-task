See the [TASK](./TASK.md) file for instructions.

The implementation assumes that millions of records can be requested by the caller to be processed and because of this uses servlet InputStream.
Streaming means that data is processed as it arrives, with minimal buffering, and enriched content is offloaded to disk instead of caching in memory.
RandomAccessFile with prepared result is streamed back to the caller.
Why this implementation?
- (1) The string with CVS content prepared by Spring Boot, passed as a web method parameter, would mean that whole HTTP request must be collected in memory and then converted from byte buffer to String. Not very efficient.
- (2) In case the web method parameter representing the HTTP content is passed as InputStream, then consumed and mapped in memory, finally returned as a whole - it's still very inefficient. There are ways to use less RAM.
- (3) In case the InputStream is read iteratively, mapped and written to the Servlet OutputStream - it's the most efficient pattern when it comes to memory usage. There is one problem with this approach - the client that calls the microservice must write and read from the socket concurrently. Think about 1GB request. It's written in chunks, received by the microservice in chunks, processed by it in chunks and also response is piped in chunks. If the client writes whole request and then expects to read response - everything will freeze. The microservice will try to write next chunk of response to Servlet's OutputStream. The previous still wasn't consumed by the caller. The TCP/IP buffer has got not acknowledged packets and is full. The buffer size is limited and new content can't be placed because there is no space. Write to Servlet OutputStream is blocked and as a result the microservice hangs. The caller also hangs because remote peer doesn't accept new packets in input TCP/IP buffer.
- (4) When InputStream is processed iteratively and transformed data is written to RAF, the caller doesn't need to support concurrent writing and reading. When data transformation is done the microservice sends response in chunks. There is no risk of server freeze or OutOfMemoryError. Usually it's better to have a rock solid stability than top performance.

Failure of 3rd implementation can be visible in unit tests:
```
mvn test -DargLine="-Xmx512m -DcommMode=bidi"
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705788|->
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705829->|
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705829|->
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705870->|
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705870|->
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705911->|
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705911|->
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705952->|
2024-04-23T23:05:19.065+02:00  INFO 334299 --- [  XNIO-1 task-9] c.v.t.w.c.TradeEnrichmentController      : 2705952|->    no exit from write

==========> test1mRecordsIn10ThreadsCanBeProcessedIn90s
Used heap: 442MB 82%, other: 64MB, bidi=true, running 49870 ms
...
==========> test1mRecordsIn10ThreadsCanBeProcessedIn90s
Used heap: 443MB 82%, other: 64MB, bidi=true, running 86893 ms

==========> test1mRecordsIn10ThreadsCanBeProcessedIn90s
Used heap: 443MB 82%, other: 64MB, bidi=true, running 87894 ms
[ERROR] Tests run: 9, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 107.0 s <<< FAILURE! -- in com.verygoodbank.tes.TradeEnrichmentServiceApplicationTests
[ERROR] com.verygoodbank.tes.TradeEnrichmentServiceApplicationTests.test1mRecordsCanBeProcessedIn10s -- Time elapsed: 11.18 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <true> but was: <false>

[ERROR] Failures: 
[ERROR]   TradeEnrichmentServiceApplicationTests.test1mRecordsCanBeProcessedIn10s:139 expected: <true> but was: <false>
[ERROR]   TradeEnrichmentServiceApplicationTests.test1mRecordsIn10ThreadsCanBeProcessedIn90s:193 expected: <10> but was: <0>
[INFO] 
[ERROR] Tests run: 9, Failures: 2, Errors: 0, Skipped: 0

```

## How to test interactively:
```
mvn spring-boot:run
```
in one console
```
curl --data-binary @src/test/resources/big-file-trade.csv --header 'Content-Type: text/csv' http://localhost:8080/api/v1/enrich >result.csv
```
in the other


## How to run the service
```
git clone https://github.com/kubaseai/trade-enrichment-task.git
cd trade-enrichment-task
mvn package
java -jar target/*.jar
```


## How to use the API
You can use API from Swagger UI (http://localhost:8080/swagger-ui/index.html), from command-line:
```
curl -X 'POST' \
  'http://localhost:8080/api/v1/enrich' \
  -H 'Accept: text/csv' \
  -H 'Content-Type: text/cvs' \
  -d '20240101,1,EUR,10.0'
```
or from any external integration by posting CVS lines matching schema: date,product_id,currency,price.


## Limitations of the code.
* Application uses temporary files to deliver response. These are created in /tmp/ directory, not in configurable path. This is not a big problem when the microservice is containerized, however on standard host it's a security anti-pattern.
* There is no HTTPS configured.
* There is no (HTTP Basic) authorization.
* There is no reloading of product id-name dictionary.
* No clean-up of orphaned temporary files
* Application wasn't prepared for GraalVM native build and there is no Dockerfile for building container.

## Ideas for improvement if there were more time available.
* Remove limitations listed above by providing more comprehensive implementation.
* Plug in Your Kit Java Profiler to see if speed-up or more conservative memory usage is possible.
* Enable observability: Actuator/OpenTracing/OpenTelemetry.
* Select better servlet runtime (Tomcat vs Jetty vs JBoss) when it comes to performance (highest throughput, lowest CPU and memory usage).
* Perform static code analysis, vulnerability detection, make sure pom.xml contains up to date dependencies.
* Fix code formatting, add method comments.
