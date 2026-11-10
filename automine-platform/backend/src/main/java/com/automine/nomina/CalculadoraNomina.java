package com.automine.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de liquidacion (RF011 a RF014). Clase pura, sin base de datos, para
 * poder probarla de forma aislada.
 */
public final class CalculadoraNomina {

    private static final BigDecimal TREINTA = BigDecimal.valueOf(30);

    private CalculadoraNomina() {
    }

    public record Parametros(BigDecimal salarioMinimo, BigDecimal auxilioTransporte, BigDecimal horasMes,
            BigDecimal recargoDiurna, BigDecimal recargoNocturna, BigDecimal recargoDominical,
            BigDecimal porcSalud, BigDecimal porcPension, BigDecimal porcSolidaridad) {
    }

    public record Entrada(BigDecimal salarioBase, int diasTrabajados, BigDecimal horasExtraDiurnas,
            BigDecimal horasExtraNocturnas, BigDecimal horasExtraDominicales, BigDecimal bonificaciones,
            BigDecimal otrosDescuentos, BigDecimal valorIncapacidad, BigDecimal valorVacaciones) {

        public Entrada(BigDecimal salarioBase, int diasTrabajados, BigDecimal horasExtraDiurnas,
                BigDecimal horasExtraNocturnas, BigDecimal horasExtraDominicales, BigDecimal bonificaciones,
                BigDecimal otrosDescuentos, BigDecimal valorIncapacidad) {
            this(salarioBase, diasTrabajados, horasExtraDiurnas, horasExtraNocturnas, horasExtraDominicales,
                    bonificaciones, otrosDescuentos, valorIncapacidad, BigDecimal.ZERO);
        }

        public Entrada(BigDecimal salarioBase, int diasTrabajados, BigDecimal horasExtraDiurnas,
                BigDecimal horasExtraNocturnas, BigDecimal horasExtraDominicales, BigDecimal bonificaciones,
                BigDecimal otrosDescuentos) {
            this(salarioBase, diasTrabajados, horasExtraDiurnas, horasExtraNocturnas, horasExtraDominicales,
                    bonificaciones, otrosDescuentos, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record Resultado(BigDecimal salarioDevengado, BigDecimal valorHorasExtra, BigDecimal auxilioTransporte,
            BigDecimal totalDevengado, BigDecimal salud, BigDecimal pension, BigDecimal fondoSolidaridad,
            BigDecimal totalDescuentos, BigDecimal netoPagar) {
    }

    public static Resultado calcular(Entrada in, Parametros p) {
        if (in.diasTrabajados() < 0 || in.diasTrabajados() > 30) {
            throw new IllegalArgumentException("Los días trabajados deben estar entre 0 y 30");
        }
        BigDecimal dias = BigDecimal.valueOf(in.diasTrabajados());

        // RF011: salario base proporcional a los dias trabajados
        BigDecimal devengado = peso(in.salarioBase().multiply(dias).divide(TREINTA, 6, RoundingMode.HALF_UP));

        // RF012: horas extra sobre el valor de la hora ordinaria
        BigDecimal valorHora = in.salarioBase().divide(p.horasMes(), 6, RoundingMode.HALF_UP);
        BigDecimal extras = peso(valorHora.multiply(
                nz(in.horasExtraDiurnas()).multiply(p.recargoDiurna())
                        .add(nz(in.horasExtraNocturnas()).multiply(p.recargoNocturna()))
                        .add(nz(in.horasExtraDominicales()).multiply(p.recargoDominical()))));

        // Auxilio de transporte solo hasta 2 salarios minimos, proporcional a los dias
        BigDecimal auxilio = BigDecimal.ZERO;
        if (in.salarioBase().compareTo(p.salarioMinimo().multiply(BigDecimal.TWO)) <= 0) {
            auxilio = peso(p.auxilioTransporte().multiply(dias).divide(TREINTA, 6, RoundingMode.HALF_UP));
        }

        BigDecimal bonificaciones = nz(in.bonificaciones());
        // Incapacidad reconocida (EPS/ARL), calculada aparte; no da derecho a auxilio de transporte
        BigDecimal incapacidad = nz(in.valorIncapacidad());
        // Vacaciones disfrutadas en el mes: salario ordinario, sin auxilio de transporte
        BigDecimal vacaciones = nz(in.valorVacaciones());
        BigDecimal totalDevengado = devengado.add(extras).add(bonificaciones).add(incapacidad).add(vacaciones)
                .add(auxilio);

        // RF013: aportes de ley sobre el ingreso base de cotizacion (sin auxilio de transporte)
        BigDecimal ibc = devengado.add(extras).add(bonificaciones).add(incapacidad).add(vacaciones);
        BigDecimal salud = peso(ibc.multiply(p.porcSalud()));
        BigDecimal pension = peso(ibc.multiply(p.porcPension()));
        BigDecimal solidaridad = BigDecimal.ZERO;
        if (ibc.compareTo(p.salarioMinimo().multiply(BigDecimal.valueOf(4))) >= 0) {
            solidaridad = peso(ibc.multiply(p.porcSolidaridad()));
        }
        BigDecimal totalDescuentos = salud.add(pension).add(solidaridad).add(nz(in.otrosDescuentos()));

        // RF014: neto a pagar
        BigDecimal neto = totalDevengado.subtract(totalDescuentos);
        if (neto.signum() < 0) {
            throw new IllegalArgumentException("Los descuentos superan el total devengado");
        }
        return new Resultado(devengado, extras, auxilio, totalDevengado, salud, pension, solidaridad,
                totalDescuentos, neto);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Pesos colombianos sin centavos. */
    private static BigDecimal peso(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).setScale(2);
    }
}
