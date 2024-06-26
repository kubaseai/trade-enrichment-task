package com.verygoodbank.tes.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.verygoodbank.tes.model.Trade;

@Service
public class TradeEnrichmentService {

    public final static String ENRICHED_CSV_HEADER = "date,product_name,currency,price\r\n";
    // Please note that this text is not compliant with requirements, but more sane
    // when trying to reconcile product definitions between consumer and provider
    public final static String TXT_ON_MISSING = "Missing Product Name for ID=";
    private final static Logger logger = LoggerFactory.getLogger(TradeEnrichmentService.class);
    private ConcurrentHashMap<String,String> productIdToNameMap = new ConcurrentHashMap<>();

    public TradeEnrichmentService() {
        readProductMap();
    }

    public void readProductMap() {
        try {
            ClassPathResource file = new ClassPathResource("products.csv");
            ConcurrentHashMap<String,String> _productIdToNameMap = new ConcurrentHashMap<>();
            try (InputStream fis = file.getInputStream(); InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader reader = new BufferedReader(isr)) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        String map[] = line.split("\\,");
                        if (map.length!=2 || map[0].isBlank() || map[1].isBlank()) {
                            logger.warn("Invalid product line: "+line);
                        }
                        else {
                            String prev = _productIdToNameMap.put(map[0].trim(), map[1].trim());
                            if (prev!=null) {
                                logger.warn("Duplicated product line: "+line);
                            }
                        }
                    }
                }
            }
            if (_productIdToNameMap.isEmpty()) {
                logger.warn("ProductIdToNameMap is empty, not reloading");
            }
            else {
                productIdToNameMap = _productIdToNameMap;
            }
        }
        catch (Exception e) {
            String msg = "Can't initialize ThreatEnrichmentService due to error: "+e.getMessage();
            logger.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }
    
    public Trade enrichTrade(Trade t) {
        String productId = t.getProductId();
        if (productId!=null) {
            String name = productIdToNameMap.get(productId);
            t.setProductName(Optional.ofNullable(name).orElseGet( () -> TXT_ON_MISSING+productId ));
        }
        return t;
    }
}
