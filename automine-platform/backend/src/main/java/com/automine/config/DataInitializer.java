package com.automine.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.automine.common.Roles;
import com.automine.consignaciones.ConsignacionService;
import com.automine.contabilidad.ContabilidadDtos.CajaRequest;
import com.automine.contabilidad.ContabilidadDtos.MovimientoRequest;
import com.automine.contabilidad.ContabilidadService;
import com.automine.contabilidad.MovimientoContable;
import com.automine.empleados.EmpleadoDtos.EmpleadoRequest;
import com.automine.empleados.EmpleadoDtos.EmpleadoResponse;
import com.automine.empleados.EmpleadoService;
import com.automine.inventario.InventarioDtos.MaquinariaRequest;
import com.automine.inventario.InventarioDtos.ProductoRequest;
import com.automine.inventario.InventarioDtos.ProductoResponse;
import com.automine.inventario.InventarioService;
import com.automine.nomina.NominaDtos.NovedadesRequest;
import com.automine.nomina.NominaDtos.PeriodoResponse;
import com.automine.nomina.NominaService;
import com.automine.produccion.FrenteTrabajo;
import com.automine.produccion.ProduccionDtos.AsignacionRequest;
import com.automine.produccion.ProduccionDtos.FrenteRequest;
import com.automine.produccion.ProduccionDtos.ProduccionRequest;
import com.automine.produccion.ProduccionService;
import com.automine.security.Rol;
import com.automine.security.RolRepository;
import com.automine.security.Usuario;
import com.automine.security.UsuarioRepository;
import com.automine.sgsst.SgsstDtos.CapacitacionRequest;
import com.automine.sgsst.SgsstDtos.EppRequest;
import com.automine.sgsst.SgsstDtos.EventoRequest;
import com.automine.sgsst.SgsstDtos.RiesgoRequest;
import com.automine.sgsst.SgsstDtos.SeguimientoRequest;
import com.automine.sgsst.SgsstService;

import lombok.RequiredArgsConstructor;

/**
 * Al arrancar: si no hay usuarios crea el administrador. Con DEMO_DATA=true y la
 * base vacia, carga datos de ejemplo de todos los modulos.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppProperties props;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final EmpleadoService empleados;
    private final NominaService nomina;
    private final ConsignacionService consignaciones;
    private final InventarioService inventario;
    private final ProduccionService produccion;
    private final SgsstService sgsst;
    private final ContabilidadService contabilidad;

    private final Random random = new Random(2026);

    @Override
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) {
            return;
        }
        var boot = props.bootstrap();
        crearUsuario("Administrador AUTOMINE", boot.adminEmail(), Roles.ADMIN, null);
        log.info("Usuario administrador creado: {}", boot.adminEmail());
        if (boot.demoData() && !props.permiteDemo()) {
            log.warn("DEMO_DATA se ignora con APP_ENV=production (use APP_ENV=demo para una demostración en línea)");
        } else if (boot.demoData()) {
            try {
                com.automine.notificaciones.NotificacionService.suspender(true);
                cargarDemo();
                log.info("Datos de demostración cargados");
            } catch (RuntimeException e) {
                log.error("No se pudieron cargar los datos de demostración", e);
            } finally {
                com.automine.notificaciones.NotificacionService.suspender(false);
            }
        }
    }

    private void crearUsuario(String nombre, String email, String rol, Long empleadoId) {
        Rol r = roles.findByCodigo(rol).orElseThrow();
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(encoder.encode(props.bootstrap().adminPassword()));
        u.setRol(r);
        u.setEmpleadoId(empleadoId);
        u.setEstado(Usuario.ACTIVO);
        // En produccion la contraseña inicial viene de una variable de entorno: se exige cambiarla al entrar.
        u.setDebeCambiarPassword(props.produccion());
        usuarios.save(u);
    }

    private void cargarDemo() {
        LocalDate hoy = LocalDate.now();

        // ------------------------------ Empleados ------------------------------
        String[][] datos = {
                { "1017234567", "Carlos Andrés", "Restrepo Gil", "Gerente General", "Gerencia", "9500000", "INDEFINIDO" },
                { "43876543", "María Fernanda", "López Ríos", "Contadora", "Contabilidad", "5200000", "INDEFINIDO" },
                { "1036654321", "Juliana", "Mejía Osorio", "Analista de Talento Humano", "Recursos Humanos", "4100000", "INDEFINIDO" },
                { "71234890", "Jorge Iván", "Zapata Mesa", "Supervisor de Mina", "Operaciones", "4800000", "INDEFINIDO" },
                { "1040111222", "Paula Andrea", "Cardona Ruiz", "Coordinadora SG-SST", "SG-SST", "4300000", "INDEFINIDO" },
                { "98765432", "Luis Alberto", "Gómez Arango", "Almacenista", "Almacén", "2600000", "INDEFINIDO" },
                { "1001234001", "Andrés Felipe", "Ramírez Cano", "Minero Perforador", "Operaciones", "2300000", "FIJO" },
                { "1001234002", "Diego Alejandro", "Henao Vélez", "Operador de Maquinaria", "Operaciones", "2900000", "INDEFINIDO" },
                { "1001234003", "Sebastián", "Ospina Toro", "Minero de Frente", "Operaciones", "1900000", "OBRA_LABOR" },
                { "1001234004", "John Fredy", "Agudelo Marín", "Ayudante de Mina", "Operaciones", "1423500", "OBRA_LABOR" },
                { "1001234005", "Yeison", "Montoya Lopera", "Minero de Frente", "Operaciones", "1900000", "FIJO" },
                { "1001234006", "Camilo", "Arboleda Serna", "Electromecánico", "Mantenimiento", "3100000", "INDEFINIDO" },
                { "1001234007", "Natalia", "Vásquez Duque", "Auxiliar Administrativa", "Administración", "1800000", "FIJO" },
                { "1001234008", "Esteban", "Quintero Pino", "Ayudante de Mina", "Operaciones", "1423500", "OBRA_LABOR" },
        };
        String[] bancos = { "Bancolombia", "Banco de Bogotá", "Davivienda", "BBVA" };
        List<EmpleadoResponse> lista = new ArrayList<>();
        for (int i = 0; i < datos.length; i++) {
            String[] d = datos[i];
            String email = "empleado" + (i + 1) + "@automine.co";
            lista.add(empleados.crear(new EmpleadoRequest(d[0], d[1], d[2], email, "300" + (1000000 + i * 7919),
                    "Calle " + (10 + i) + " # " + (20 + i) + "-15, Segovia", LocalDate.of(1980 + i, 1 + i % 12, 5 + i),
                    d[3], d[4], new BigDecimal(d[5]), hoy.minusMonths(30 - i).withDayOfMonth(1), d[6],
                    bancos[i % bancos.length], i % 3 == 0 ? "CORRIENTE" : "AHORROS", "0" + (45000000 + i * 1311),
                    "Sura EPS", "Porvenir", "ARL Sura", false)));
        }
        crearUsuario("Carlos Restrepo", "gerente@automine.co", Roles.GERENTE, lista.get(0).id());
        crearUsuario("María Fernanda López", "contador@automine.co", Roles.CONTADOR, lista.get(1).id());
        crearUsuario("Juliana Mejía", "rrhh@automine.co", Roles.RRHH, lista.get(2).id());
        crearUsuario("Jorge Zapata", "supervisor@automine.co", Roles.SUPERVISOR, lista.get(3).id());
        crearUsuario("Paula Cardona", "sgsst@automine.co", Roles.SGSST, lista.get(4).id());
        crearUsuario("Luis Gómez", "almacen@automine.co", Roles.ALMACENISTA, lista.get(5).id());
        crearUsuario("Andrés Ramírez", "empleado@automine.co", Roles.EMPLEADO, lista.get(6).id());
        List<EmpleadoResponse> mineros = lista.subList(6, lista.size());

        // ------------------------------ Inventario ------------------------------
        Object[][] productos = {
                { "HER-001", "Martillo perforador neumatico", "HERRAMIENTA", "und", 6, 2, 4800000 },
                { "HER-002", "Pala minera punta cuadrada", "HERRAMIENTA", "und", 25, 10, 85000 },
                { "HER-003", "Barra de acero 1.8 m", "HERRAMIENTA", "und", 14, 6, 120000 },
                { "EQU-001", "Detector multigas", "EQUIPO", "und", 3, 3, 3200000 },
                { "EQU-002", "Lampara minera recargable", "EQUIPO", "und", 30, 12, 260000 },
                { "INS-001", "Broca de botones 45 mm", "INSUMO", "und", 40, 15, 210000 },
                { "INS-002", "ACPM", "INSUMO", "gal", 380, 150, 10800 },
                { "INS-003", "Aceite hidráulico", "INSUMO", "gal", 22, 25, 68000 },
                { "EPP-001", "Casco minero con portalampara", "EPP", "und", 18, 10, 95000 },
                { "EPP-002", "Botas de caucho punta de acero", "EPP", "par", 12, 10, 120000 },
                { "EPP-003", "Respirador media cara", "EPP", "und", 35, 20, 78000 },
                { "EPP-004", "Guantes de nitrilo", "EPP", "par", 8, 30, 12000 },
                { "REP-001", "Manguera de alta presión 1\"", "REPUESTO", "m", 60, 20, 45000 },
        };
        List<ProductoResponse> prods = new ArrayList<>();
        for (Object[] p : productos) {
            prods.add(inventario.crear(new ProductoRequest((String) p[0], (String) p[1], (String) p[2], (String) p[3],
                    BigDecimal.valueOf((Integer) p[4]), BigDecimal.valueOf((Integer) p[5]),
                    BigDecimal.valueOf((Integer) p[6]), "Bodega principal")));
        }
        inventario.movimiento(new com.automine.inventario.InventarioDtos.MovimientoRequest(prods.get(6).id(), "SALIDA", BigDecimal.valueOf(120), null,
                "Consumo de combustible de la semana", "Diego Henao"));
        inventario.movimiento(new com.automine.inventario.InventarioDtos.MovimientoRequest(prods.get(5).id(), "SALIDA", BigDecimal.valueOf(12), null,
                "Perforación frente Veta El Silencio", "Andrés Ramírez"));
        inventario.movimiento(new com.automine.inventario.InventarioDtos.MovimientoRequest(prods.get(6).id(), "ENTRADA", BigDecimal.valueOf(200),
                BigDecimal.valueOf(11200), "Compra a Distribuidora Antioquia", "Luis Gómez"));

        // ------------------------------ Produccion ------------------------------
        FrenteTrabajo f1 = produccion.guardarFrente(null,
                new FrenteRequest("Veta El Silencio", "Nivel 3 - Galería norte", "Oro", BigDecimal.valueOf(60), true));
        FrenteTrabajo f2 = produccion.guardarFrente(null,
                new FrenteRequest("Túnel La Esperanza", "Nivel 2 - Galería sur", "Oro", BigDecimal.valueOf(45), true));
        FrenteTrabajo f3 = produccion.guardarFrente(null,
                new FrenteRequest("Cantera San Juan", "Superficie - Sector oriental", "Carbón", BigDecimal.valueOf(80), true));
        inventario.crearMaquina(new MaquinariaRequest("MAQ-001", "Cargador de bajo perfil LHD", "Cargador", "Epiroc",
                "ST7", "EP-7781", "OPERATIVA", BigDecimal.valueOf(5230), hoy.plusDays(5), f1.getId(), null));
        inventario.crearMaquina(new MaquinariaRequest("MAQ-002", "Jumbo de perforación", "Perforadora", "Sandvik",
                "DD311", "SV-3110", "OPERATIVA", BigDecimal.valueOf(8810), hoy.plusDays(40), f2.getId(), null));
        inventario.crearMaquina(new MaquinariaRequest("MAQ-003", "Volqueta articulada", "Transporte", "Volvo",
                "A30G", "VL-3022", "MANTENIMIENTO", BigDecimal.valueOf(12040), hoy.minusDays(2), f3.getId(),
                "Cambio de transmisión"));
        inventario.crearMaquina(new MaquinariaRequest("MAQ-004", "Compresor de aire", "Compresor", "Atlas Copco",
                "XAS 185", "AC-1850", "OPERATIVA", BigDecimal.valueOf(3120), hoy.plusDays(90), f1.getId(), null));

        FrenteTrabajo[] frentes = { f1, f2, f3 };
        String[] turnos = { "MANANA", "TARDE", "NOCHE" };
        String[] labores = { "Perforación", "Cargue", "Voladura", "Acarreo", "Sostenimiento" };
        for (int dia = 60; dia >= 0; dia--) {
            LocalDate fecha = hoy.minusDays(dia);
            for (FrenteTrabajo f : frentes) {
                for (String turno : turnos) {
                    if ("NOCHE".equals(turno) && f == f3) {
                        continue;
                    }
                    double base = f.getMetaDiariaToneladas().doubleValue() / (f == f3 ? 2 : 3);
                    double ton = base * (0.75 + random.nextDouble() * 0.45);
                    List<AsignacionRequest> as = new ArrayList<>();
                    int n = 2 + random.nextInt(2);
                    for (int k = 0; k < n; k++) {
                        var emp = mineros.get((dia + k * 3 + turno.length()) % mineros.size());
                        if (as.stream().noneMatch(a -> a.empleadoId().equals(emp.id()))) {
                            as.add(new AsignacionRequest(emp.id(), labores[(k + dia) % labores.length]));
                        }
                    }
                    produccion.registrar(new ProduccionRequest(fecha, turno, f.getId(),
                            BigDecimal.valueOf(Math.round(ton * 10) / 10.0), BigDecimal.valueOf(8),
                            random.nextInt(9) == 0 ? "Retraso por mantenimiento de ventilación" : null, as));
                }
            }
        }

        // -------------------------------- Nomina --------------------------------
        YearMonth actual = YearMonth.from(hoy);
        for (int m = 3; m >= 1; m--) {
            YearMonth ym = actual.minusMonths(m);
            var gen = nomina.generarPeriodo(ym.getYear(), ym.getMonthValue());
            nomina.detallePeriodo(gen.periodo().id()).forEach(n -> {
                if (n.cargo().contains("Minero") || n.cargo().contains("Operador")) {
                    nomina.actualizar(n.id(), new NovedadesRequest(30, BigDecimal.valueOf(6 + random.nextInt(10)),
                            BigDecimal.valueOf(random.nextInt(8)), BigDecimal.valueOf(random.nextInt(4)),
                            BigDecimal.valueOf(150000), BigDecimal.ZERO, null));
                }
            });
            PeriodoResponse p = nomina.aprobar(gen.periodo().id());
            if (m > 1) {
                LocalDate pago = ym.atEndOfMonth();
                consignaciones.registrarLote(p.id(), pago, "NOM");
                consignaciones.listar(p.id(), "REGISTRADA").forEach(c -> consignaciones.aprobar(c.id()));
            }
        }
        nomina.generarPeriodo(actual.getYear(), actual.getMonthValue());

        // ------------------------------ Contabilidad ------------------------------
        String[] clientes = { "Fundición Antioqueña S.A.", "Comercializadora Oro Andino", "Metales del Cauca" };
        for (int m = 5; m >= 0; m--) {
            YearMonth ym = actual.minusMonths(m);
            for (int k = 0; k < 3; k++) {
                LocalDate f = ym.atDay(5 + k * 9);
                if (f.isAfter(hoy)) {
                    continue;
                }
                contabilidad.registrar(MovimientoContable.INGRESO, new MovimientoRequest(
                        BigDecimal.valueOf(95_000_000L + random.nextInt(40_000_000)),
                        "Venta de mineral a " + clientes[k], f, "Venta de mineral", "FV-" + ym.getMonthValue() + k));
            }
            Object[][] gastos = { { "Combustibles y lubricantes", 18_000_000 }, { "Explosivos y voladura", 22_000_000 },
                    { "Servicios públicos", 6_500_000 }, { "Arrendamiento de equipos", 12_000_000 },
                    { "Regalías y contraprestaciones", 9_000_000 } };
            for (int k = 0; k < gastos.length; k++) {
                LocalDate f = ym.atDay(3 + k * 5);
                if (f.isAfter(hoy)) {
                    continue;
                }
                int base = (Integer) gastos[k][1];
                contabilidad.registrar(MovimientoContable.GASTO, new MovimientoRequest(
                        BigDecimal.valueOf(base + random.nextInt(base / 4)), (String) gastos[k][0] + " "
                                + ym.getMonthValue() + "/" + ym.getYear(),
                        f, (String) gastos[k][0], null));
            }
        }
        contabilidad.movimientoCaja(new CajaRequest("REPOSICION", hoy.minusDays(20), "Apertura de caja menor",
                BigDecimal.valueOf(1_500_000), null));
        contabilidad.movimientoCaja(new CajaRequest("GASTO", hoy.minusDays(15), "Papelería y fotocopias",
                BigDecimal.valueOf(85_000), "R-001"));
        contabilidad.movimientoCaja(new CajaRequest("GASTO", hoy.minusDays(9), "Refrigerios capacitación",
                BigDecimal.valueOf(240_000), "R-002"));
        contabilidad.movimientoCaja(new CajaRequest("GASTO", hoy.minusDays(3), "Transporte de muestras al laboratorio",
                BigDecimal.valueOf(120_000), "R-003"));

        // -------------------------------- SG-SST --------------------------------
        var acc = sgsst.registrarEvento(new EventoRequest("ACCIDENTE", mineros.get(2).id(),
                LocalDateTime.of(hoy.minusDays(18), java.time.LocalTime.of(10, 40)), "Veta El Silencio - Nivel 3",
                "Golpe en la mano derecha con barra de acero durante el desabombe", "LEVE", 3,
                "Herramienta manipulada sin guante de protección", null));
        sgsst.seguimiento(acc.id(), new SeguimientoRequest(null,
                "Charla de 5 minutos sobre manipulación de herramientas y reposición de guantes", null, "CERRADO"));
        sgsst.registrarEvento(new EventoRequest("INCIDENTE", null,
                LocalDateTime.of(hoy.minusDays(6), java.time.LocalTime.of(15, 10)), "Túnel La Esperanza",
                "Desprendimiento menor de roca en el techo sin personas afectadas", "MODERADO", 0,
                "Sostenimiento insuficiente", "Se reforzo el sostenimiento con pernos y malla"));
        sgsst.registrarEvento(new EventoRequest("ACCIDENTE", mineros.get(4).id(),
                LocalDateTime.of(hoy.minusDays(2), java.time.LocalTime.of(7, 55)), "Cantera San Juan",
                "Torcedura de tobillo al descender de la volqueta", "MODERADO", 5, null, null));
        for (int i = 0; i < 5; i++) {
            sgsst.entregarEpp(new EppRequest(mineros.get(i).id(), prods.get(8 + i % 3).id(), null, BigDecimal.ONE,
                    hoy.minusDays(10 + i), "Dotación periódica"));
        }
        Object[][] riesgos = {
                { "Extracción subterranea", "Perforación", "Ruido del martillo perforador", "FISICO", 4, 3, "Hipoacusia", "Protectores auditivos y rotación de personal" },
                { "Extracción subterranea", "Desabombe", "Caida de rocas", "LOCATIVO", 3, 5, "Lesiones graves, muerte", "Desabombe sistemático, sostenimiento con pernos" },
                { "Voladura", "Carga de explosivos", "Manipulación de explosivos", "QUIMICO", 2, 5, "Quemaduras, muerte", "Personal certificado y protocolo de voladura" },
                { "Ventilación", "Trabajo en galerias", "Gases toxicos y falta de oxigeno", "QUIMICO", 3, 4, "Intoxicación, asfixia", "Monitoreo con detector multigas" },
                { "Transporte", "Operación de volqueta", "Atropellamiento", "MECANICO", 2, 4, "Lesiones multiples", "Senalización y alarma de reversa" },
                { "Administración", "Trabajo en oficina", "Posturas prolongadas", "BIOMECANICO", 3, 2, "Lesiones osteomusculares", "Pausas activas" },
        };
        for (Object[] r : riesgos) {
            sgsst.guardarRiesgo(null, new RiesgoRequest((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                    (String) r[6], (Integer) r[4], (Integer) r[5], (String) r[7], "Coordinadora SG-SST"));
        }
        List<Long> convocados = mineros.stream().map(EmpleadoResponse::id).toList();
        var cap = sgsst.programar(new CapacitacionRequest("Manejo seguro de explosivos", "Norma de voladura controlada",
                hoy.minusDays(12).atTime(8, 0), BigDecimal.valueOf(4), "Ing. Ricardo Mesa", "Sala de capacitación",
                convocados));
        sgsst.asistencia(cap.id(), convocados.stream()
                .map(id -> new com.automine.sgsst.SgsstDtos.AsistenciaItem(id, id % 5 != 0)).toList());
        sgsst.programar(new CapacitacionRequest("Primeros auxilios en mina subterranea", "Brigada de emergencias",
                hoy.plusDays(8).atTime(14, 0), BigDecimal.valueOf(3), "Cruz Roja Colombiana", "Campamento",
                convocados.subList(0, 5)));
    }
}
