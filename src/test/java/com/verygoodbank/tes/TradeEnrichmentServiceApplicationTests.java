package com.verygoodbank.tes;

import static org.junit.jupiter.api.Assertions.*;

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

    private URI uri() {
        try {
            return new URI("/api/v1/enrich");
        } catch (URISyntaxException e) {
            // should not happen (tm)
            throw new RuntimeException("Impossible happended", e);
        }
    }

    @Test
    public void testOneLine() throws Exception {
        String request = "20160101,1,EUR,10.0";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER+"20160101,Treasury Bills Domestic,EUR,10.0\r\n",
            resp.getBody());
    }

    @Test
    public void testWrongDate() throws Exception {
        String request = "20252229,1,EUR,10.0";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER, resp.getBody());
    }

    @Test
    public void testWrongPrice() throws Exception {
        String request = "20250228,1,EUR,10k";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER, resp.getBody());
    }

    @Test
    public void testWrongNumberOfFieldsInRow() throws Exception {
        String request = "20240229,1,EUR,10,11,12";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER, resp.getBody());
    }

    @Test
    public void test1mRecordsCanBeProcessedIn10s() throws Exception {
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
        String request = "20250228,999999999,EUR,10";
        var resp = rest.exchange(uri(), HttpMethod.POST, new HttpEntity<>(request), String.class);
        assertEquals(TradeEnrichmentService.ENRICHED_CSV_HEADER+
            "20250228,"+TradeEnrichmentService.TXT_ON_MISSING+"999999999,EUR,10\r\n", resp.getBody());
    }

    @Test
    public void testGoodAndBadLines() throws Exception {
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
    public void test1mRecordsin10ThreadsCanBeProcessedIn90s() throws Exception {
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
}
