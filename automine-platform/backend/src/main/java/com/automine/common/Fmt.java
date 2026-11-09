package com.automine.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatos de presentacion (moneda colombiana, fechas). */
public final class Fmt {

    private static final Locale CO = Locale.of("es", "CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", CO);

    private Fmt() {
    }

    public static String money(BigDecimal v) {
        if (v == null) {
            return "";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(CO);
        nf.setMaximumFractionDigits(0);
        return "$ " + nf.format(v.setScale(0, RoundingMode.HALF_UP));
    }

    public static String num(BigDecimal v) {
        if (v == null) {
            return "";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(CO);
        nf.setMaximumFractionDigits(2);
        return nf.format(v);
    }

    public static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    public static String dateTime(LocalDateTime d) {
        return d == null ? "" : d.format(DATE_TIME);
    }

    public static String longDate(LocalDate d) {
        return d == null ? "" : d.format(LONG_DATE);
    }

    public static String mes(int mes) {
        return switch (mes) {
            case 1 -> "Enero";
            case 2 -> "Febrero";
            case 3 -> "Marzo";
            case 4 -> "Abril";
            case 5 -> "Mayo";
            case 6 -> "Junio";
            case 7 -> "Julio";
            case 8 -> "Agosto";
            case 9 -> "Septiembre";
            case 10 -> "Octubre";
            case 11 -> "Noviembre";
            case 12 -> "Diciembre";
            default -> String.valueOf(mes);
        };
    }
}
