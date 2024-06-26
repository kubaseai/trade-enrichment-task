package com.verygoodbank.tes.web.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.verygoodbank.tes.model.Trade;
import com.verygoodbank.tes.model.ValidationException;
import com.verygoodbank.tes.service.TradeEnrichmentService;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@OpenAPIDefinition(
	info = @Info(
		title = "Trade Encrichment Service",
		version = "0.9"
	)
)
@RequestMapping("/api/v1")
public class TradeEnrichmentController {

    private final static Logger logger = LoggerFactory.getLogger(TradeEnrichmentController.class);
    @Autowired
    private TradeEnrichmentService tradeEnrichmentService;
       
    @Operation(summary = "This operation accepts csv content with product lines: "+
        "date,product_id,currency,price - to be enriched. Product Id is replaced with product description. "+
        "Input content is validated. Wrong records are not enriched.")
    @ApiResponses(value = { 
	    @ApiResponse(responseCode = "200", description = "Enriched CSV file",
	        content = { 
                @Content(mediaType = "text/csv", schema = @Schema(defaultValue = "20240101,1,EUR,10.0"))
            }
        )
    })
    @RequestMapping(value = "/enrich", method = RequestMethod.POST, produces = "text/csv")
	public ResponseEntity<StreamingResponseBody> enrichProduct(ServletRequest req,
        @RequestBody String csv, InputStream file, HttpServletResponse response) throws Exception 
    {
        // please note that "String csv" above is a workaround for Swagger UI
		if (req instanceof MultipartHttpServletRequest) {
			MultipartHttpServletRequest multi = (MultipartHttpServletRequest)req;
			MultipartFile multipartFile = multi.getFile("file");
			file = multipartFile.getInputStream();			
		}
        long startTime = System.currentTimeMillis();
        long recordsCount = 0;
        AtomicLong wrongRecordsCount = new AtomicLong();
        Path tempStorePath = newTemporaryStorePath();
        
        try (InputStream autoClosable = file;
            InputStreamReader isr = new InputStreamReader(file);
            Scanner scanner = new Scanner(isr);            
            RandomAccessFile store = newTemporaryStore(tempStorePath);
            FileChannel channel = store.getChannel()) 
        {
            scanner.useDelimiter("[\\r]*[\\n]+");
            StringBuilder sb = new StringBuilder(4096);
            response.setHeader("Content-Type", "text/csv");
            response.setStatus(200);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (++recordsCount==1) {
                    // Always output the csv header.
                    write(channel, TradeEnrichmentService.ENRICHED_CSV_HEADER);
                }
                processSingleRecord(line, recordsCount, wrongRecordsCount, channel, startTime, sb); 
            }
            long length = channel.position();
            channel.force(false);
            StreamingResponseBody stream = newResponseStreaming(tempStorePath, length);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("Content-Length", length+"");
            responseHeaders.add("Content-Type", "text/csv");
            return new ResponseEntity<>(stream, responseHeaders, HttpStatus.OK);
        }
        catch (Exception e) {
            logger.error("Enrichment failed. Current records count="+recordsCount, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        finally {        
            long endTime = System.currentTimeMillis();
            logger.info("Processed {} records in {} ms, invalid count: {}", 
                recordsCount, (endTime-startTime), wrongRecordsCount);
        }
    }

    @Operation(summary = "This operation accepts csv content with product lines: "+
    "date,product_id,currency,price - to be enriched. Product Id is replaced with product description. "+
    "Input content is validated. Wrong records are not enriched.")
    @ApiResponses(value = { 
        @ApiResponse(responseCode = "200", description = "Enriched CSV file",
            content = { 
                @Content(mediaType = "text/csv", schema = @Schema(defaultValue = "20240101,1,EUR,10.0"))
            }
        )
    })
    @RequestMapping(value = "/enrich-bidi", method = RequestMethod.POST, produces = "text/csv")
    public  void enrichProductBidi(ServletRequest req,
        @RequestBody String csv, InputStream file, HttpServletResponse response) throws Exception 
    {
        // please note that "String csv" above is a workaround for Swagger UI
        if (req instanceof MultipartHttpServletRequest) {
            MultipartHttpServletRequest multi = (MultipartHttpServletRequest)req;
            MultipartFile multipartFile = multi.getFile("file");
            if (multipartFile!=null) {
                file = multipartFile.getInputStream();
            }	
        }
        long startTime = System.currentTimeMillis();
        long recordsCount = 0;
        AtomicLong wrongRecordsCount = new AtomicLong();
                
        try (InputStream autoClosable = file;
            InputStreamReader isr = new InputStreamReader(file);
            Scanner scanner = new Scanner(isr);            
            OutputStream channel = response.getOutputStream())        
        {
            scanner.useDelimiter("[\\r]*[\\n]+");
            StringBuilder sb = new StringBuilder(4096);
            response.setHeader("Content-Type", "text/csv");
            response.setStatus(200);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (++recordsCount==1) {
                    // Always output the csv header.
                    write(channel, TradeEnrichmentService.ENRICHED_CSV_HEADER);
                }
                processSingleRecord(line, recordsCount, wrongRecordsCount, channel, startTime, sb); 
            }        
        }
        catch (Exception e) {
            logger.error("Enrichment failed. Current records count="+recordsCount, e);
            response.setStatus(500);
        }
        finally {        
            long endTime = System.currentTimeMillis();
            logger.info("Processed {} records in {} ms, invalid count: {}", 
                recordsCount, (endTime-startTime), wrongRecordsCount);
        }
    }

    private boolean processSingleRecord(String line, long recordsCount, AtomicLong wrongRecordsCount,
        OutputStream channel, long startTime, StringBuilder sb) throws IOException
    {
        Trade trade = null;
        try {
            trade = Trade.fromCsvLine(line, recordsCount==1);
        }
        catch (ValidationException ve) {
            wrongRecordsCount.incrementAndGet();
            logger.warn("Not processable record at line {} due to error: {}, content: \"{}\"",
                recordsCount, ve.getMessage(), ve.getContent());
            return false;
        }   
        
        if (trade!=null && !trade.isLikelyHeader()) {
            //logger.info(recordsCount+"|->");
            write(channel, tradeEnrichmentService.enrichTrade(trade).toCsvEnrichedLine(sb));
        }
        else {
            wrongRecordsCount.incrementAndGet();
            logger.warn("Empty record at line {}", recordsCount);
        }  
        if (recordsCount % 1_000_000 == 0) {
            long mlns = recordsCount/1_000_000;
            long seconds = (System.currentTimeMillis()-startTime)/1000L;
            double throughput = Math.round(100.0*mlns/seconds)/100.0;
            logger.info("Currenly processed {} mln records, wall time is {}s, throughput {} mln/s",
                mlns, seconds, throughput);
        }
        //logger.info(recordsCount+"->|");
        return true;
    }

    private boolean processSingleRecord(String line, long recordsCount, AtomicLong wrongRecordsCount,
        FileChannel channel, long startTime, StringBuilder sb) throws IOException
    {
        Trade trade = null;
        try {
            trade = Trade.fromCsvLine(line, recordsCount==1);
        }
        catch (ValidationException ve) {
            wrongRecordsCount.incrementAndGet();
            logger.warn("Not processable record at line {} due to error: {}, content: \"{}\"",
                recordsCount, ve.getMessage(), ve.getContent());
            return false;
        }   
        
        if (trade!=null && !trade.isLikelyHeader()) {
            write(channel, tradeEnrichmentService.enrichTrade(trade).toCsvEnrichedLine(sb));
        }
        else {
            wrongRecordsCount.incrementAndGet();
            logger.warn("Empty record at line {}", recordsCount);
        }  
        if (recordsCount % 1_000_000 == 0) {
            long mlns = recordsCount/1_000_000;
            long seconds = (System.currentTimeMillis()-startTime)/1000L;
            double throughput = Math.round(100.0*mlns/seconds)/100.0;
            logger.info("Currenly processed {} mln records, wall time is {}s, throughput {} mln/s",
                mlns, seconds, throughput);
        }
        return true;
    }

    private StreamingResponseBody  newResponseStreaming(Path tempStorePath, long length) {
        StreamingResponseBody stream = output -> {
            byte[] buffer = new byte[1024*1024];
            try (RandomAccessFile raf = new RandomAccessFile(tempStorePath.toFile(), "r")) {
                long pos = 0;
                raf.seek(pos);
                while (pos < length - 1) {                            
                    int n = raf.read(buffer);
                    if (n>0) {
                        output.write(buffer, 0, n);
                        pos += n;
                    }
                    else {
                        break;
                    }                    
                }
                if (pos != length) {
                    throw new RuntimeException("Truncated read, actual="+pos+", expected="+length);
                }
                output.flush();                    
            }
            catch (Throwable e) {
                logger.error("Response transfer error: " + e.getMessage());                    
            }
            finally {
                Files.delete(tempStorePath);
            }
        };
        return stream;
    }

    private void write(OutputStream channel, String s) throws IOException {
        channel.write(s.getBytes());        
    }

    private void write(FileChannel channel, String s) throws IOException {
        channel.write(ByteBuffer.wrap(s.getBytes()));        
    }

    private Path newTemporaryStorePath() throws IOException {
        return Files.createTempFile("trade-enrichment-svc-", ".csv");
    }
    private RandomAccessFile newTemporaryStore(Path path) throws FileNotFoundException, IOException {
        return new RandomAccessFile(path.toFile(), "rw");
    }
}