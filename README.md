See the [TASK](./TASK.md) file for instructions.

The implementation assumes that millions of records can be requested by the caller to be processed and because of this uses servlet InputStream.
Streaming means that data is processed as it arrives, with minimal buffering, and enriched content is offloaded to disk instead of caching in memory.
RandomAccessFile with prepared result is streamed back to the caller.

How to test:
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
