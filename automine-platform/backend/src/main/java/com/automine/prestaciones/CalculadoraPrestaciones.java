package com.automine.prestaciones;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;

/**
 * Prestaciones sociales (Codigo Sustantivo del Trabajo), en dias comerciales (año de 360, mes de 30):
 * - Prima de servicios (art. 306): base x dias del semestre / 360.
 * - Cesantias (art. 249): base x dias del año / 360.
 * - Intereses a las cesantias (Ley 52/1975): cesantias x dias x 12 % / 360.
 * - Vacaciones (art. 186): 15 dias habiles por cada 360 dias laborados.
 * - Indemnizacion por despido sin justa causa (art. 64).
 * La base incluye el auxilio de transporte para quienes ganan hasta 2 salarios minimos.
 */
public final class CalculadoraPrestaciones {

    private static final BigDecimal B360 = BigDecimal.valueOf(360);
    private static final BigDecimal B30 = BigDecimal.valueOf(30);
    private static final BigDecimal DOCE = new BigDecimal("0.12");
    /** Contratos laborales con prestaciones (no aplican a aprendices ni a prestacion de servicios). */
    public static final Set<String> CONTRATOS_CON_PRESTACIONES = Set.of("INDEFINIDO", "FIJO", "OBRA_LABOR");

    private CalculadoraPrestaciones() {
    }

    public static boolean aplica(String tipoContrato) {
        return CONTRATOS_CON_PRESTACIONES.contains(tipoContrato);
    }

    /** Dias comerciales entre dos fechas, ambas incluidas (dia 31 cuenta como 30; fin de febrero como 30). */
    public static int dias360(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            return 0;
        }
        int d1 = Math.min(desde.getDayOfMonth(), 30);
        int d2 = Math.min(hasta.getDayOfMonth(), 30);
        if (hasta.getMonthValue() == 2 && hasta.equals(hasta.withDayOfMonth(hasta.lengthOfMonth()))) {
            d2 = 30;
        }
        return (hasta.getYear() - desde.getYear()) * 360 + (hasta.getMonthValue() - desde.getMonthValue()) * 30
                + (d2 - d1) + 1;
    }

    public static BigDecimal base(BigDecimal salario, BigDecimal auxilio, BigDecimal salarioMinimo) {
        boolean conAuxilio = salario.compareTo(salarioMinimo.multiply(BigDecimal.TWO)) <= 0;
        return conAuxilio ? salario.add(auxilio) : salario;
    }

    public static BigDecimal prima(BigDecimal base, int dias) {
        return peso(base.multiply(BigDecimal.valueOf(dias)).divide(B360, 6, RoundingMode.HALF_UP));
    }

    public static BigDecimal cesantias(BigDecimal base, int dias) {
        return prima(base, dias);
    }

    public static BigDecimal intereses(BigDecimal cesantias, int dias) {
        return peso(cesantias.multiply(BigDecimal.valueOf(dias)).multiply(DOCE).divide(B360, 6, RoundingMode.HALF_UP));
    }

    /** Dias habiles de vacaciones causados por los dias laborados. */
    public static BigDecimal diasVacacionesCausados(int diasLaborados) {
        return BigDecimal.valueOf(diasLaborados).multiply(BigDecimal.valueOf(15)).divide(B360, 2, RoundingMode.DOWN);
    }

    /** Compensacion en dinero de vacaciones no disfrutadas (salario diario x dias habiles pendientes). */
    public static BigDecimal compensacionVacaciones(BigDecimal salario, BigDecimal diasPendientes) {
        if (diasPendientes.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return peso(salario.multiply(diasPendientes).divide(B30, 6, RoundingMode.HALF_UP));
    }

    public static BigDecimal salarioDias(BigDecimal salario, int dias) {
        return peso(salario.multiply(BigDecimal.valueOf(dias)).divide(B30, 6, RoundingMode.HALF_UP));
    }

    /**
     * Indemnizacion por terminacion sin justa causa (art. 64 CST).
     * Indefinido: menos de 10 SMMLV -> 30 dias el primer año y 20 por cada año adicional (proporcional);
     * 10 SMMLV o mas -> 20 y 15. Fijo: salarios del tiempo que faltaba. Obra o labor: lo que faltaba, minimo 15 dias.
     */
    public static BigDecimal indemnizacion(String tipoContrato, BigDecimal salario, BigDecimal salarioMinimo,
            LocalDate ingreso, LocalDate retiro, LocalDate finContrato) {
        BigDecimal diario = salario.divide(B30, 6, RoundingMode.HALF_UP);
        BigDecimal dias;
        switch (tipoContrato) {
            case "INDEFINIDO" -> {
                boolean alto = salario.compareTo(salarioMinimo.multiply(BigDecimal.TEN)) >= 0;
                BigDecimal primero = BigDecimal.valueOf(alto ? 20 : 30);
                BigDecimal adicional = BigDecimal.valueOf(alto ? 15 : 20);
                BigDecimal anios = BigDecimal.valueOf(dias360(ingreso, retiro)).divide(B360, 6, RoundingMode.HALF_UP);
                BigDecimal extra = anios.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
                dias = primero.add(adicional.multiply(extra));
            }
            case "FIJO" -> {
                if (finContrato == null || !finContrato.isAfter(retiro)) {
                    return BigDecimal.ZERO.setScale(2);
                }
                dias = BigDecimal.valueOf(dias360(retiro.plusDays(1), finContrato));
            }
            case "OBRA_LABOR" -> {
                int faltan = finContrato == null || !finContrato.isAfter(retiro) ? 0
                        : dias360(retiro.plusDays(1), finContrato);
                dias = BigDecimal.valueOf(Math.max(15, faltan));
            }
            default -> {
                return BigDecimal.ZERO.setScale(2);
            }
        }
        return peso(diario.multiply(dias));
    }

    private static BigDecimal peso(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).setScale(2);
    }
}
