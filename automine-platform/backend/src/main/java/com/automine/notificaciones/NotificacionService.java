package com.automine.notificaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.Fmt;
import com.automine.common.MailService;
import com.automine.common.MailService.Correo;
import com.automine.common.ParametroService;
import com.automine.common.Roles;
import com.automine.config.AppProperties;
import com.automine.empleados.Empleado;
import com.automine.inventario.Maquinaria;
import com.automine.inventario.Producto;
import com.automine.security.Usuario;
import com.automine.security.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Avisos por correo de los eventos del negocio. Se apagan con el parametro
 * NOTIFICACIONES_CORREO = 0. Los correos de cuenta (bienvenida, recuperacion)
 * se envian siempre.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final MailService mail;
    private final UsuarioRepository usuarios;
    private final ParametroService parametros;
    private final Producto.Repo productos;
    private final Maquinaria.Repo maquinas;
    private final AppProperties props;

    // ------------------------------ Cuenta ------------------------------

    public void bienvenida(String email, String nombre, String passwordTemporal) {
        mail.enviar(email, Correo.de("Bienvenido a AUTOMINE", "Hola, " + primerNombre(nombre),
                "Ingresar a AUTOMINE", props.publicUrl() + "/login",
                "Se creó su cuenta en AUTOMINE, la plataforma de gestión de la empresa.",
                "Usuario: " + email + "\nContraseña temporal: " + passwordTemporal,
                "Al ingresar por primera vez el sistema le pedirá crear una contraseña personal. "
                        + "También puede usar la aplicación móvil con los mismos datos."));
    }

    public void recuperacion(String email, String nombre, String enlace) {
        mail.enviar(email, Correo.de("Recuperación de contraseña", "Restablezca su contraseña",
                "Crear nueva contraseña", enlace,
                "Hola, " + primerNombre(nombre) + ". Recibimos una solicitud para restablecer su contraseña de AUTOMINE.",
                "El enlace es válido durante 30 minutos. Si usted no hizo la solicitud, ignore este mensaje: "
                        + "su contraseña actual sigue funcionando."));
    }

    // ------------------------------ Negocio ------------------------------

    public void accidenteReportado(String tipo, String gravedad, String lugar, String trabajador, String descripcion) {
        if (!activas()) {
            return;
        }
        String titulo = ("ACCIDENTE".equals(tipo) ? "Accidente" : "Incidente") + " reportado: " + gravedad.toLowerCase();
        Correo c = Correo.de("SG-SST: " + titulo, titulo, "Ver en SG-SST", props.publicUrl() + "/sgsst",
                "Lugar: " + lugar + (trabajador == null ? "" : "\nTrabajador: " + trabajador),
                descripcion,
                "Registre la investigación y las acciones correctivas en el módulo SG-SST.");
        aRoles(c, Roles.SGSST, Roles.GERENTE);
    }

    public void nominaPorAprobar(int anio, int mes, int liquidaciones, BigDecimal neto) {
        if (!activas()) {
            return;
        }
        Correo c = Correo.de("Nómina de " + Fmt.mes(mes) + " " + anio + " lista para aprobar",
                "Nómina pendiente de aprobación", "Revisar nómina", props.publicUrl() + "/nomina",
                "Se liquidó la nómina de " + Fmt.mes(mes) + " " + anio + ": " + liquidaciones + " empleados por "
                        + Fmt.money(neto) + " netos.",
                "Revise las novedades y apruébela para habilitar las consignaciones.");
        aRoles(c, Roles.CONTADOR);
    }

    /** A cada empleado del periodo: su desprendible ya esta disponible. */
    public void nominaAprobada(int anio, int mes, List<Empleado> empleados) {
        if (!activas()) {
            return;
        }
        for (Empleado e : empleados) {
            mail.enviar(correoDe(e), Correo.de("Su desprendible de " + Fmt.mes(mes) + " está disponible",
                    "Desprendible de " + Fmt.mes(mes) + " " + anio, "Ver mis pagos", props.publicUrl() + "/mi-portal",
                    "Hola, " + e.getNombres() + ". La nómina de " + Fmt.mes(mes) + " " + anio
                            + " fue aprobada. Ya puede consultar y descargar su desprendible de pago.",
                    "También lo encuentra en la aplicación móvil AUTOMINE, en la sección Mis pagos."));
        }
    }

    public void pagoAprobado(Empleado e, BigDecimal valor, String banco, String cuenta, LocalDate fecha, String referencia) {
        if (!activas()) {
            return;
        }
        mail.enviar(correoDe(e), Correo.de("Pago consignado: " + Fmt.money(valor), "Su pago fue consignado",
                "Ver mis pagos", props.publicUrl() + "/mi-portal",
                "Hola, " + e.getNombres() + ". Se consignó su pago de nómina.",
                "Valor: " + Fmt.money(valor) + "\nBanco: " + banco + " " + cuenta + "\nFecha: " + Fmt.date(fecha)
                        + "\nReferencia: " + referencia));
    }

    /** Resumen diario (6:30 a. m.) de stock bajo y mantenimientos proximos para almacen. */
    @Scheduled(cron = "0 30 6 * * *", zone = "America/Bogota")
    @Transactional(readOnly = true)
    public void resumenDiarioAlmacen() {
        if (!activas()) {
            return;
        }
        List<Producto> bajos = productos.bajoStock();
        List<Maquinaria> mant = maquinas.mantenimientoProximo(LocalDate.now().plusDays(7));
        if (bajos.isEmpty() && mant.isEmpty()) {
            return;
        }
        StringBuilder stock = new StringBuilder();
        bajos.forEach(p -> stock.append("• ").append(p.getNombre()).append(": ").append(Fmt.num(p.getCantidad()))
                .append(' ').append(p.getUnidad()).append(" (mínimo ").append(Fmt.num(p.getStockMinimo())).append(")\n"));
        StringBuilder maq = new StringBuilder();
        mant.forEach(m -> maq.append("• ").append(m.getCodigo()).append(' ').append(m.getNombre()).append(": ")
                .append(Fmt.date(m.getProximoMantenimiento())).append('\n'));
        Correo c = Correo.de("Alertas de inventario y maquinaria", "Resumen diario de alertas", "Abrir inventario",
                props.publicUrl() + "/inventario",
                bajos.isEmpty() ? "Sin productos bajo el stock mínimo." : "Productos bajo el stock mínimo:\n" + stock.toString().trim(),
                mant.isEmpty() ? "Sin mantenimientos en los próximos 7 días."
                        : "Mantenimientos vencidos o en los próximos 7 días:\n" + maq.toString().trim());
        aRoles(c, Roles.ALMACENISTA, Roles.ADMIN);
    }

    // ------------------------------ Utilidades ------------------------------

    /** Mientras se cargan datos de demostracion no se envian correos de negocio. */
    private static volatile boolean suspendidas = false;

    public static void suspender(boolean valor) {
        suspendidas = valor;
    }

    private boolean activas() {
        if (suspendidas) {
            return false;
        }
        try {
            return parametros.decimal("NOTIFICACIONES_CORREO").signum() > 0;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void aRoles(Correo c, String... roles) {
        Set<String> destinos = new LinkedHashSet<>();
        usuarios.findByRolCodigoInAndEstado(List.of(roles), Usuario.ACTIVO).forEach(u -> destinos.add(u.getEmail()));
        if (destinos.isEmpty()) {
            log.debug("Sin destinatarios para '{}'", c.asunto());
        }
        destinos.forEach(d -> mail.enviar(d, c));
    }

    private String correoDe(Empleado e) {
        if (e.getEmail() != null && !e.getEmail().isBlank()) {
            return e.getEmail();
        }
        return usuarios.findFirstByEmpleadoId(e.getId()).map(Usuario::getEmail).orElse(null);
    }

    private static String primerNombre(String nombre) {
        return nombre == null ? "" : nombre.trim().split("\\s+")[0];
    }
}
