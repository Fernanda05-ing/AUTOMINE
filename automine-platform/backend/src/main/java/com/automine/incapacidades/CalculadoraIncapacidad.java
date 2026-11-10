package com.automine.incapacidades;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Dias y valor de incapacidad que corresponden a un mes de nomina (mes comercial de 30 dias).
 * Reglas (Colombia):
 * - Enfermedad general: 66,67 % del salario los primeros 90 dias (dias 1-2 los paga el
 *   empleador, del 3 en adelante la EPS) y 50 % del dia 91 al 180.
 * - Accidente de trabajo y enfermedad laboral (ARL): 100 %.
 * - Licencias de maternidad y paternidad (EPS): 100 %.
 * Ningun dia se reconoce por debajo del salario minimo diario.
 * La empresa paga el total al trabajador en la nomina y recobra a la EPS/ARL.
 */
public final class CalculadoraIncapacidad {

    private static final BigDecimal TREINTA = BigDecimal.valueOf(30);
    private static final BigDecimal DOS_TERCIOS = new BigDecimal("0.6667");
    private static final BigDecimal MEDIO = new BigDecimal("0.5");

    private CalculadoraIncapacidad() {
    }

    /**
     * @param diasPrevios dias ya acumulados antes de {@code inicio} (prorrogas), para el conteo 1-90 / 91-180
     */
    public record Tramo(String tipo, LocalDate inicio, LocalDate fin, int diasPrevios) {
    }

    public record Resultado(int dias, BigDecimal valor) {
        public static final Resultado CERO = new Resultado(0, BigDecimal.ZERO.setScale(2));
    }

    public static BigDecimal porcentaje(String tipo, int diaNumero) {
        if (Incapacidad.ENFERMEDAD_GENERAL.equals(tipo)) {
            return diaNumero <= 90 ? DOS_TERCIOS : MEDIO;
        }
        return BigDecimal.ONE;
    }

    public static Resultado delMes(List<Tramo> tramos, YearMonth mes, BigDecimal salarioBase, BigDecimal salarioMinimo) {
        LocalDate primero = mes.atDay(1);
        LocalDate ultimo = mes.atEndOfMonth();
        BigDecimal diario = salarioBase.divide(TREINTA, 6, RoundingMode.HALF_UP);
        BigDecimal minimoDiario = salarioMinimo.divide(TREINTA, 6, RoundingMode.HALF_UP);
        int dias = 0;
        BigDecimal valor = BigDecimal.ZERO;
        for (Tramo t : tramos) {
            LocalDate desde = t.inicio().isAfter(primero) ? t.inicio() : primero;
            LocalDate hasta = t.fin().isBefore(ultimo) ? t.fin() : ultimo;
            for (LocalDate d = desde; !d.isAfter(hasta) && dias < 30; d = d.plusDays(1)) {
                if (d.getDayOfMonth() == 31) {
                    continue; // mes comercial de 30 dias
                }
                int numero = t.diasPrevios() + (int) ChronoUnit.DAYS.between(t.inicio(), d) + 1;
                // Febrero: si la incapacidad cubre el fin de mes se completan los 30 dias comerciales
                int repeticiones = d.equals(ultimo) && ultimo.getDayOfMonth() < 30 && !t.fin().isBefore(ultimo)
                        ? 30 - ultimo.getDayOfMonth() + 1
                        : 1;
                for (int r = 0; r < repeticiones && dias < 30; r++) {
                    BigDecimal v = diario.multiply(porcentaje(t.tipo(), numero + r));
                    valor = valor.add(v.max(minimoDiario));
                    dias++;
                }
            }
        }
        return new Resultado(dias, valor.setScale(0, RoundingMode.HALF_UP).setScale(2));
    }
}
