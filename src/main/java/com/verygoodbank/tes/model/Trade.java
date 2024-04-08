package com.verygoodbank.tes.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Calendar;

public class Trade {
    String sDate;
    String sProductId;
    String sProductName;
    String sCurrency;
    String sPrice;
    boolean isLikelyHeader = false;

    public static Trade fromCsvLine(String line, boolean mightBeHeader) throws ValidationException {
        if (line!=null && !line.isBlank()) {
            String values[] = line.split("\\,");
            int isLikelyHeader = 0;
            if (values.length!=4) {
                throw new ValidationException("Unexpected number of fields in trade record: "+
                    values.length+" vs 4", line);
            }
            Trade t = new Trade();
            t.sDate = values[0];
            if (!validateDate(t.sDate)) {
                if (!mightBeHeader)
                    throw new ValidationException("Invalid date format: "+t.sDate, line);
                else
                    isLikelyHeader++;
            }
            t.sProductId = values[1];
            t.sCurrency = values[2];
            t.sPrice = values[3];
            if (!validatePrice(t.sPrice)) {
                if (!mightBeHeader)
                    throw new ValidationException("Invalid price format: "+t.sPrice, line);
                else
                    isLikelyHeader++;
            }
            if (isLikelyHeader==2 && mightBeHeader) {
                t.isLikelyHeader = true;
            }
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
