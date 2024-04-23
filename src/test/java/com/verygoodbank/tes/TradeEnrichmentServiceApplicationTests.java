package com.verygoodbank.tes;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import com.verygoodbank.tes.service.TradeEnrichmentService;

@SpringBootTest(webEnvironment=WebEnvironment.DEFINED_PORT)  
class TradeEnrichmentServiceApplicationTests {

    @Autowired
    TestRestTemplate rest;

    private static boolean isBidi = System.getProperty("commMode", "").equalsIgnoreCase("bidi");
    private static String lastTestName = "";
    private final static Thread bgThread = new Thread() {
        public void run() {
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            while (lastTestName!=null) {
                long heapUsagePct = memBean.getHeapMemoryUsage().getUsed()*100/memBean.getHeapMemoryUsage().getMax();
                long heapMB = memBean.getHeapMemoryUsage().getUsed()/1000_000;
                long nonHeapMB = memBean.getNonHeapMemoryUsage().getUsed()/1000_000;
                System.out.println("\n==========> "+lastTestName+"\nUsed heap: "+heapMB+"MB "+heapUsagePct+"%, other: "+nonHeapMB+"MB, bidi="+isBidi);
                try {
                    Thread.sleep(1000);
                } 
                catch (InterruptedException e) {
                    break;
                }
            }
            System.out.println("Exit");
        }
    };

    static {
        System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
        System.setProperty("sun.net.client.defaultReadTimeout", "90000");
        bgThread.start();
    }

    private URI uri() {
        try {
            
            return new URI("/api/v1/enrich" + (isBidi ? "-bidi" : ""));
        } catch (URISyntaxException e) {
            // should not happen (tm)
            throw new RuntimeException("Impossible happended", e);
        }
    }

    private void enter(final String s) {
        lastTestName = s;        
    }

    @Test
    public void testOneLine() throws Exception {
        enter("testOneLine");
        String request = "20160101,1,EUR,10.0";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER+"20160101,Treasury Bills Domestic,EUR,10.0\r\n",
            resp.getBody());        
    }

    @Test
    public void testWrongDate() throws Exception {
        enter("testWrongDate");
        String request = "20252229,1,EUR,10.0";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER, resp.getBody());
    }

    @Test
    public void testWrongPrice() throws Exception {
        enter("testWrongPrice");
        String request = "20250228,1,EUR,10k";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER, resp.getBody());
    }

    @Test
    public void testWrongNumberOfFieldsInRow() throws Exception {
        enter("testWrongNumberOfFieldsInRow");
        String request = "20240229,1,EUR,10,11,12";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER, resp.getBody());
    }

    @Test
    public void test1mRecordsCanBeProcessedIn10s() throws Exception {
        enter("test1mRecordsCanBeProcessedIn10s");
        String request = "20240229,1,EUR,10\r\n";
        StringBuilder sb = new StringBuilder(1024*1024);
        for (int i=0; i < 1000_000; i++) {
            sb.append(request);
        }
        request = sb.toString();
        long startTime = System.currentTimeMillis();
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        long endTime = System.currentTimeMillis();
        assertEquals(true, resp.getStatusCode().is2xxSuccessful());
        assertTrue(endTime - startTime <= 10000);
    }

    @Test
    public void testMissingProductMapping() throws Exception {
        enter("testMissingProductMapping");
        String request = "20250228,999999999,EUR,10";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER+
            "20250228,"+TradeEnrichmentService.TXT_ON_MISSING+"999999999,EUR,10\r\n", resp.getBody());
    }

    @Test
    public void testGoodAndBadLines() throws Exception {
        enter("testGoodAndBadLines");
        String request = TradeEnrichmentService.ENRICHED_CSV_HEADER+
                """
                20250228,1,EUR,10
                wrong-record
                20240229,2,EUR,15

                20240227,3,USD,10.5
                """;
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        List<String> response = Arrays.asList(resp.getBody().split("[\r]*\n")).stream()
            .map(line -> line.split("\\,")[0]).toList();
        List<String> filteredRequest = Arrays.asList(request.split("[\r]*\n")).stream().
            filter( val -> val.contains("," )).map(line -> line.split("\\,")[0]).toList();
        assertEquals(response, filteredRequest);
    }

    @Test
    public void test1mRecordsIn10ThreadsCanBeProcessedIn90s() throws Exception {
        enter("test1mRecordsIn10ThreadsCanBeProcessedIn90s");
        String request = "20240229,1,EUR,10\r\n";
        StringBuilder sb = new StringBuilder(1024*1024);
        for (int i=0; i < 1000_000; i++) {
            sb.append(request);
        }
        final String req = sb.toString();
        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        ConcurrentLinkedQueue<Integer> statusList = new ConcurrentLinkedQueue<>();
        long startTime = System.currentTimeMillis();
        for (int i=0; i < 10; i++) {
            threadPool.submit( () -> {
                var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(req), String.class);
                statusList.add(resp.getStatusCode().value());
            });
        }
        threadPool.awaitTermination(90, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        long numSuccesses = statusList.stream().map( status -> status==200 ? 1 : 0)
            .collect(Collectors.summarizingInt(Integer::intValue)).getSum();
        assertEquals(10L, numSuccesses);
        assertTrue(endTime - startTime <= 91000);
    }

    @Test
    public void testEndsHere() throws Exception {
        enter(null);
    }
}
