See the [TASK](./TASK.md) file for instructions.

The implementation assumes that millions of records can be requested by the caller to be processed and because of this uses servlet InputStream.
Streaming means that data is processed as it arrives, with minimal buffering, and enriched content is offloaded to disk instead of caching in memory.
RandomAccessFile with prepared result is streamed back to the caller.
