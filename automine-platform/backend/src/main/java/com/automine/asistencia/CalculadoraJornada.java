package com.automine.asistencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.automine.prestaciones.Festivos;

/**
 * Convierte entradas y salidas en horas trabajadas y horas extra:
 * - Cada dia (segun la hora de entrada) tiene {@code horasDiarias} ordinarias; lo que exceda es extra.
 * - La hora extra es nocturna si ocurre entre {@code inicioNocturno} y {@code finNocturno}
 *   (Ley 2466 de 2025: desde las 7 p. m.), y dominical/festiva si la jornada empezo en domingo o festivo.
 * Una entrada sin salida (o una salida sin entrada) se reporta como inconsistencia y no suma horas.
 */
public final class CalculadoraJornada {

    private CalculadoraJornada() {
    }

    public record Evento(String tipo, LocalDateTime fechaHora) {
    }

    public record Parametros(int horasDiarias, int inicioNocturno, int finNocturno) {
    }

    public record Dia(LocalDate fecha, LocalDateTime entrada, LocalDateTime salida, BigDecimal horas,
            BigDecimal extraDiurna, BigDecimal extraNocturna, BigDecimal extraDominical, boolean dominical,
            String inconsistencia) {
    }

    public record Resumen(List<Dia> dias, BigDecimal horas, BigDecimal extraDiurna, BigDecimal extraNocturna,
            BigDecimal extraDominical, int diasTrabajados, int inconsistencias) {
    }

    /** Eventos ordenados por hora de un mismo trabajador. */
    public static Resumen calcular(List<Evento> eventos, Parametros p) {
        List<Dia> dias = new ArrayList<>();
        LocalDateTime abierta = null;
        for (Evento e : eventos) {
            if (Marcacion.ENTRADA.equals(e.tipo())) {
                if (abierta != null) {
                    dias.add(sinPareja(abierta, null, "Entrada sin salida"));
                }
                abierta = e.fechaHora();
            } else {
                if (abierta == null) {
                    dias.add(sinPareja(null, e.fechaHora(), "Salida sin entrada"));
                } else if (java.time.Duration.between(abierta, e.fechaHora()).toHours() >= 20) {
                    dias.add(sinPareja(abierta, e.fechaHora(), "Jornada de más de 20 horas: revise las marcaciones"));
                    abierta = null;
                } else {
                    dias.add(jornada(abierta, e.fechaHora(), p));
                    abierta = null;
                }
            }
        }
        if (abierta != null) {
            dias.add(sinPareja(abierta, null, "Entrada sin salida"));
        }
        // Varias jornadas el mismo dia: las horas ordinarias se reparten en orden
        Map<LocalDate, BigDecimal> usadas = new LinkedHashMap<>();
        List<Dia> ajustados = new ArrayList<>();
        for (Dia d : dias) {
            if (d.inconsistencia() != null || d.horas().signum() == 0) {
                ajustados.add(d);
                continue;
            }
            BigDecimal previas = usadas.getOrDefault(d.fecha(), BigDecimal.ZERO);
            usadas.put(d.fecha(), previas.add(d.horas()));
            ajustados.add(previas.signum() == 0 ? d : jornadaConPrevias(d, previas, p));
        }
        BigDecimal h = BigDecimal.ZERO, ed = BigDecimal.ZERO, en = BigDecimal.ZERO, edd = BigDecimal.ZERO;
        int inconsistencias = 0;
        for (Dia d : ajustados) {
            h = h.add(d.horas());
            ed = ed.add(d.extraDiurna());
            en = en.add(d.extraNocturna());
            edd = edd.add(d.extraDominical());
            if (d.inconsistencia() != null) {
                inconsistencias++;
            }
        }
        return new Resumen(ajustados, h, ed, en, edd, usadas.size(), inconsistencias);
    }

    private static Dia jornada(LocalDateTime entrada, LocalDateTime salida, Parametros p) {
        return contar(entrada, salida, 0, p);
    }

    private static Dia jornadaConPrevias(Dia d, BigDecimal horasPrevias, Parametros p) {
        return contar(d.entrada(), d.salida(), horasPrevias.multiply(BigDecimal.valueOf(60)).intValue(), p);
    }

    /** Recorre la jornada minuto a minuto: primero las ordinarias, luego las extra clasificadas. */
    private static Dia contar(LocalDateTime entrada, LocalDateTime salida, int minutosPrevios, Parametros p) {
        LocalDate fecha = entrada.toLocalDate();
        boolean dominical = fecha.getDayOfWeek() == DayOfWeek.SUNDAY || Festivos.esFestivo(fecha);
        int ordinarios = Math.max(0, p.horasDiarias() * 60 - minutosPrevios);
        int total = 0, diurna = 0, nocturna = 0, dom = 0;
        for (LocalDateTime t = entrada; t.isBefore(salida); t = t.plusMinutes(1)) {
            total++;
            if (total <= ordinarios) {
                continue;
            }
            if (dominical) {
                dom++;
            } else if (esNocturno(t.getHour(), p)) {
                nocturna++;
            } else {
                diurna++;
            }
        }
        return new Dia(fecha, entrada, salida, horas(total), horas(diurna), horas(nocturna), horas(dom), dominical, null);
    }

    static boolean esNocturno(int hora, Parametros p) {
        return p.inicioNocturno() > p.finNocturno() ? hora >= p.inicioNocturno() || hora < p.finNocturno()
                : hora >= p.inicioNocturno() && hora < p.finNocturno();
    }

    private static Dia sinPareja(LocalDateTime entrada, LocalDateTime salida, String motivo) {
        LocalDate fecha = (entrada != null ? entrada : salida).toLocalDate();
        BigDecimal cero = BigDecimal.ZERO.setScale(2);
        return new Dia(fecha, entrada, salida, cero, cero, cero, cero, false, motivo);
    }

    private static BigDecimal horas(int minutos) {
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
