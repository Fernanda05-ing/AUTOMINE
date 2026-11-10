package com.automine.prestaciones;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Festivos de Colombia (Ley 51 de 1983 "Ley Emiliani"): fijos, trasladables al lunes
 * siguiente y los que dependen de la Pascua.
 */
public final class Festivos {

    private static final Map<Integer, Set<LocalDate>> CACHE = new ConcurrentHashMap<>();

    private Festivos() {
    }

    public static boolean esFestivo(LocalDate d) {
        return delAnio(d.getYear()).contains(d);
    }

    public static Set<LocalDate> delAnio(int anio) {
        return CACHE.computeIfAbsent(anio, Festivos::calcular);
    }

    private static Set<LocalDate> calcular(int anio) {
        Set<LocalDate> f = new TreeSet<>();
        // Fijos
        f.add(LocalDate.of(anio, 1, 1));
        f.add(LocalDate.of(anio, 5, 1));
        f.add(LocalDate.of(anio, 7, 20));
        f.add(LocalDate.of(anio, 8, 7));
        f.add(LocalDate.of(anio, 12, 8));
        f.add(LocalDate.of(anio, 12, 25));
        // Trasladables al lunes siguiente
        f.add(lunes(LocalDate.of(anio, 1, 6)));
        f.add(lunes(LocalDate.of(anio, 3, 19)));
        f.add(lunes(LocalDate.of(anio, 6, 29)));
        f.add(lunes(LocalDate.of(anio, 8, 15)));
        f.add(lunes(LocalDate.of(anio, 10, 12)));
        f.add(lunes(LocalDate.of(anio, 11, 1)));
        f.add(lunes(LocalDate.of(anio, 11, 11)));
        // Dependientes de la Pascua
        LocalDate pascua = pascua(anio);
        f.add(pascua.minusDays(3)); // Jueves Santo
        f.add(pascua.minusDays(2)); // Viernes Santo
        f.add(lunes(pascua.plusDays(39))); // Ascension
        f.add(lunes(pascua.plusDays(60))); // Corpus Christi
        f.add(lunes(pascua.plusDays(68))); // Sagrado Corazon
        return f;
    }

    private static LocalDate lunes(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.MONDAY ? d : d.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /** Domingo de Pascua (algoritmo anonimo gregoriano). */
    static LocalDate pascua(int y) {
        int a = y % 19;
        int b = y / 100;
        int c = y % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, mes, dia);
    }

    /** Dia habil: no es domingo ni festivo (ni sabado si la empresa no trabaja sabados). */
    public static boolean esHabil(LocalDate d, boolean sabadoHabil) {
        DayOfWeek w = d.getDayOfWeek();
        if (w == DayOfWeek.SUNDAY || (w == DayOfWeek.SATURDAY && !sabadoHabil)) {
            return false;
        }
        return !esFestivo(d);
    }

    /**
     * Ultimo dia de descanso al disfrutar {@code habiles} dias habiles desde {@code inicio}:
     * incluye los domingos y festivos que siguen, hasta la vispera del regreso.
     */
    public static LocalDate finVacaciones(LocalDate inicio, int habiles, boolean sabadoHabil) {
        LocalDate d = inicio;
        int contados = 0;
        LocalDate ultimo = inicio;
        while (contados < habiles) {
            if (esHabil(d, sabadoHabil)) {
                contados++;
                ultimo = d;
            }
            d = d.plusDays(1);
        }
        return siguienteHabil(ultimo, sabadoHabil).minusDays(1);
    }

    public static LocalDate siguienteHabil(LocalDate d, boolean sabadoHabil) {
        LocalDate x = d.plusDays(1);
        while (!esHabil(x, sabadoHabil)) {
            x = x.plusDays(1);
        }
        return x;
    }
}
