package com.automine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.automine.nomina.CalculadoraNomina;
import com.automine.nomina.CalculadoraNomina.Entrada;
import com.automine.nomina.CalculadoraNomina.Parametros;
import com.automine.nomina.CalculadoraNomina.Resultado;

/** RF011 a RF014: reglas de liquidacion. */
class CalculadoraNominaTest {

    private static final Parametros P = new Parametros(bd(1_423_500), bd(200_000), bd(220), bd("1.25"), bd("1.75"),
            bd("2.00"), bd("0.04"), bd("0.04"), bd("0.01"));

    @Test
    void salarioMinimoMesCompletoIncluyeAuxilioYAportes() {
        Resultado r = CalculadoraNomina.calcular(new Entrada(bd(1_423_500), 30, null, null, null, null, null), P);
        assertThat(r.salarioDevengado()).isEqualByComparingTo("1423500");
        assertThat(r.auxilioTransporte()).isEqualByComparingTo("200000");
        assertThat(r.salud()).isEqualByComparingTo("56940");
        assertThat(r.pension()).isEqualByComparingTo("56940");
        assertThat(r.fondoSolidaridad()).isZero();
        assertThat(r.netoPagar()).isEqualByComparingTo("1509620");
    }

    @Test
    void diasProporcionalesYHorasExtra() {
        // 2.200.000 / 220 = 10.000 la hora; 10 h diurnas x 1.25 + 4 h nocturnas x 1.75 = 195.000
        Resultado r = CalculadoraNomina.calcular(
                new Entrada(bd(2_200_000), 15, bd(10), bd(4), BigDecimal.ZERO, bd(100_000), bd(50_000)), P);
        assertThat(r.salarioDevengado()).isEqualByComparingTo("1100000");
        assertThat(r.valorHorasExtra()).isEqualByComparingTo("195000");
        assertThat(r.auxilioTransporte()).isEqualByComparingTo("100000");
        // IBC = 1.100.000 + 195.000 + 100.000 = 1.395.000 -> 4% = 55.800
        assertThat(r.salud()).isEqualByComparingTo("55800");
        assertThat(r.totalDescuentos()).isEqualByComparingTo("161600");
        assertThat(r.netoPagar()).isEqualByComparingTo("1333400");
    }

    @Test
    void sinAuxilioSobreDosMinimosYConSolidaridadDesdeCuatro() {
        Resultado r = CalculadoraNomina.calcular(new Entrada(bd(6_000_000), 30, null, null, null, null, null), P);
        assertThat(r.auxilioTransporte()).isZero();
        assertThat(r.fondoSolidaridad()).isEqualByComparingTo("60000");
    }

    @Test
    void rechazaDescuentosMayoresAlDevengado() {
        assertThatThrownBy(() -> CalculadoraNomina
                .calcular(new Entrada(bd(1_423_500), 1, null, null, null, null, bd(5_000_000)), P))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
