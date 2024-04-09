package com.verygoodbank.tes.model;

import java.time.DateTimeException;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public class Trade {
    String sDate;
    String sProductId;
    String sProductName;
    String sCurrency;
    String sPrice;
    transient boolean isLikelyHeader = false;

    public static Trade fromCsvLine(String line, boolean mightBeHeader) throws ValidationException {
        if (line!=null && !line.isBlank()) {
            // We give flexibility to the caller to include or skip csv header.
            // As a result we must have some heuristics here to detect its presence.
            // This detection is needed because we validate date and price.
            boolean isLikelyHeader = line.chars().map( ch -> Character.isDigit(ch) ? 1 : 0).sum() == 0 &&
                mightBeHeader;
            String values[] = line.split("\\,");
            if (values.length!=4) {
                throw new ValidationException("Unexpected number of fields in trade record: "+
                    values.length+" vs 4", line);
            }
            Trade t = new Trade();
            t.sDate = values[0];
            if (!validateDate(t.sDate)) {
                if (!isLikelyHeader)
                    throw new ValidationException("Invalid date format: "+t.sDate, line);
            }
            t.sProductId = values[1];
            t.sCurrency = values[2];
            t.sPrice = values[3];
            if (!validatePrice(t.sPrice)) {
                if (!isLikelyHeader)
                    throw new ValidationException("Invalid price format: "+t.sPrice, line);
            }
            t.isLikelyHeader = isLikelyHeader;
            return t;
        }
        return null;
    }

    private static boolean validateDate(String dt) {
        if (dt.length()!=8) {
            return false;
        }
        try {
            int year = Integer.parseInt(dt.substring(0, 4));
            int month = Integer.parseInt(dt.substring(4, 6));
            int day = Integer.parseInt(dt.substring(6, 8));
            LocalDate.of(year, month, day);
        }
        catch (NumberFormatException | DateTimeException e) {
            return false;
        }
        return true;
    }

    private static boolean validatePrice(String number) {
        try {
            Double.parseDouble(number);
            return true;
        }
        catch (NumberFormatException nfe) {
            return false;
        }        
    }

    public String getDate() {
        return sDate;
    }

    public void setDate(String sDate) {
        this.sDate = sDate;
    }

    public String getProductId() {
        return sProductId;
    }

    public void setProductId(String sProductId) {
        this.sProductId = sProductId;
    }

    public String getProductName() {
        return sProductName;
    }

    public void setProductName(String sProductName) {
        this.sProductName = sProductName;
    }

    public String getCurrency() {
        return sCurrency;
    }

    public void setCurrency(String sCurrency) {
        this.sCurrency = sCurrency;
    }

    public String getPrice() {
        return sPrice;
    }

    public void setPrice(String sPrice) {
        this.sPrice = sPrice;
    }

    public String toCsvEnrichedLine(StringBuilder sb) {
        sb.setLength(0);
        sb.append(sDate).append(",").append(sProductName).append(",")
            .append(sCurrency).append(",").append(sPrice).append("\r\n");
        return sb.toString();
    }

    public boolean isLikelyHeader() {
        return isLikelyHeader;
    }
}
