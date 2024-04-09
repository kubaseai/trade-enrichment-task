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


