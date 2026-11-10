package com.automine.inspecciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.empleados.Empleado;
import com.automine.inventario.Maquinaria;
import com.automine.notificaciones.NotificacionService;
import com.automine.security.UsuarioActual;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Inspecciones preoperacionales con lista de chequeo por tipo de equipo. Una falla en un
 * punto critico deja el equipo NO APTO, lo pasa a mantenimiento y avisa a almacen y supervision.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspeccionService {

    public record Punto(String item, boolean critico) {
    }

    public record Respuesta(String item, boolean critico, String estado, String observacion) {
    }

    public record InspeccionResponse(Long id, Long maquinariaId, String equipo, String tipo, LocalDateTime fechaHora,
            BigDecimal horometro, Long operadorId, String operador, List<Respuesta> items, int fallas, int fallasCriticas,
            String resultado, String observaciones, String inspector, List<Long> fotos) {
    }

    /** Listas de chequeo por tipo de equipo (* = critico: si falla, el equipo no puede operar). */
    public static final Map<String, List<Punto>> LISTAS = new LinkedHashMap<>();

    static {
        LISTAS.put("MAQUINARIA_PESADA", List.of(p("Frenos de servicio y de parqueo", true),
                p("Sistema hidráulico sin fugas", true), p("Alarma de reversa", true), p("Luces de trabajo", true),
                p("Cinturón de seguridad", true), p("Cabina y estructura ROPS/FOPS sin daños", true),
                p("Mandos y palancas responden bien", true), p("Orugas o llantas en buen estado", true),
                p("Extintor cargado y vigente", true), p("Espejos y cámaras", false),
                p("Niveles de aceite, refrigerante e hidráulico", false), p("Balde o implemento sin fisuras", false),
                p("Limpieza de cabina y escalones", false)));
        LISTAS.put("VEHICULO", List.of(p("Frenos", true), p("Dirección", true), p("Luces delanteras, traseras y de freno", true),
                p("Pito y alarma de reversa", true), p("Llantas (presión y labrado) y repuesto", true),
                p("Cinturones de seguridad", true), p("Sin fugas de fluidos", true), p("Extintor vigente", true),
                p("SOAT y revisión técnico-mecánica vigentes", true), p("Direccionales y estacionarias", false),
                p("Espejos", false), p("Limpiabrisas", false), p("Botiquín y kit de carretera", false),
                p("Niveles de aceite, refrigerante y frenos", false)));
        LISTAS.put("HERRAMIENTA", List.of(p("Guardas de protección", true), p("Cable, enchufe o mangueras sin daños", true),
                p("Acoples y conexiones firmes", true), p("Gatillo o interruptor funciona", true),
                p("Broca, barreno o disco en buen estado", false), p("Lubricación", false), p("Limpieza", false)));
        LISTAS.put("EQUIPO_ALTURAS", List.of(p("Arnés: costuras y reatas sin cortes", true), p("Hebillas y argollas", true),
                p("Eslinga y absorbedor de energía", true), p("Mosquetones con seguro", true),
                p("Línea de vida y punto de anclaje", true), p("Etiqueta legible y dentro de vida útil", false)));
    }

    private static Punto p(String item, boolean critico) {
        return new Punto(item, critico);
    }

    private static final Set<String> ESTADOS = Set.of("OK", "FALLA", "NA");
    private static final Set<String> IMAGENES = Set.of("image/jpeg", "image/png");

    private final Inspeccion.Repo inspecciones;
    private final Inspeccion.Foto.Repo fotos;
    private final Maquinaria.Repo maquinas;
    private final Empleado.Repo empleados;
    private final NotificacionService notificaciones;
    private final AuditService audit;
    private final ObjectMapper json;

    @Transactional
    public InspeccionResponse registrar(Long maquinariaId, String equipo, String tipo, BigDecimal horometro,
            Long operadorId, String observaciones, String itemsJson, List<MultipartFile> archivos) {
        List<Punto> lista = LISTAS.get(tipo);
        if (lista == null) {
            throw ApiException.badRequest("Tipo de equipo no válido");
        }
        Maquinaria maq = maquinariaId == null ? null
                : maquinas.findById(maquinariaId).orElseThrow(() -> ApiException.notFound("Equipo"));
        String nombreEquipo = maq != null ? maq.getCodigo() + " " + maq.getNombre()
                : equipo == null || equipo.isBlank() ? null : equipo.trim();
        if (nombreEquipo == null) {
            throw ApiException.badRequest("Seleccione el equipo inspeccionado");
        }
        List<Map<String, String>> resp;
        try {
            resp = json.readValue(itemsJson, new TypeReference<List<Map<String, String>>>() {
            });
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("Las respuestas de la lista de chequeo no son válidas");
        }
        Map<String, Map<String, String>> porItem = new LinkedHashMap<>();
        resp.forEach(r -> porItem.put(r.get("item"), r));
        List<Respuesta> items = new ArrayList<>();
        int fallas = 0, criticas = 0;
        for (Punto pt : lista) {
            Map<String, String> r = porItem.get(pt.item());
            String estado = r == null ? null : r.get("estado");
            if (estado == null || !ESTADOS.contains(estado)) {
                throw ApiException.badRequest("Falta revisar: " + pt.item());
            }
            String obs = r.get("observacion") == null || r.get("observacion").isBlank() ? null : r.get("observacion").trim();
            if ("FALLA".equals(estado)) {
                fallas++;
                if (pt.critico()) {
                    criticas++;
                }
                if (obs == null) {
                    throw ApiException.badRequest("Describa la falla en: " + pt.item());
                }
            }
            items.add(new Respuesta(pt.item(), pt.critico(), estado, obs));
        }
        List<MultipartFile> fotosValidas = archivos == null ? List.of() : archivos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (fotosValidas.size() > 10) {
            throw ApiException.badRequest("Máximo 10 fotos por inspección");
        }
        if (fallas > 0 && fotosValidas.isEmpty()) {
            throw ApiException.badRequest("Adjunte al menos una foto de las fallas encontradas");
        }
        for (MultipartFile f : fotosValidas) {
            if (!IMAGENES.contains(f.getContentType())) {
                throw ApiException.badRequest("Las fotos deben ser JPG o PNG");
            }
        }
        Inspeccion i = new Inspeccion();
        i.setMaquinaria(maq);
        i.setEquipo(nombreEquipo);
        i.setTipo(tipo);
        i.setFechaHora(LocalDateTime.now());
        i.setHorometro(horometro);
        i.setOperador(operadorId == null ? null : empleados.findById(operadorId).orElse(null));
        try {
            i.setItems(json.writeValueAsString(items));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        i.setFallas(fallas);
        i.setFallasCriticas(criticas);
        i.setResultado(criticas > 0 ? Inspeccion.NO_APTO : fallas > 0 ? Inspeccion.APTO_CON_OBSERVACIONES : Inspeccion.APTO);
        i.setObservaciones(observaciones == null || observaciones.isBlank() ? null : observaciones.trim());
        i.setInspector(UsuarioActual.get().nombre());
        inspecciones.save(i);
        for (MultipartFile f : fotosValidas) {
            Inspeccion.Foto foto = new Inspeccion.Foto();
            foto.setInspeccionId(i.getId());
            foto.setContentType(f.getContentType());
            try {
                foto.setDatos(f.getBytes());
            } catch (java.io.IOException e) {
                throw ApiException.badRequest("No se pudo leer una foto");
            }
            fotos.save(foto);
        }
        if (horometro != null && maq != null && (maq.getHorometro() == null || horometro.compareTo(maq.getHorometro()) > 0)) {
            maq.setHorometro(horometro);
        }
        if (Inspeccion.NO_APTO.equals(i.getResultado())) {
            if (maq != null && Maquinaria.OPERATIVA.equals(maq.getEstado())) {
                maq.setEstado(Maquinaria.MANTENIMIENTO);
            }
            List<String> fallasCriticas = items.stream().filter(x -> x.critico() && "FALLA".equals(x.estado()))
                    .map(x -> x.item() + ": " + x.observacion()).toList();
            notificaciones.equipoNoApto(nombreEquipo, i.getInspector(), fallasCriticas);
        }
        audit.registrar("INSPECCION", "Equipo", maq == null ? null : maq.getId(), nombreEquipo + " " + i.getResultado());
        return of(i);
    }

    public List<InspeccionResponse> listar(LocalDate desde, LocalDate hasta, Long maquinariaId) {
        return inspecciones.buscar(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay(), maquinariaId).stream()
                .map(this::of).toList();
    }

    public InspeccionResponse obtener(Long id) {
        return of(inspecciones.findWithMaquinariaById(id).orElseThrow(() -> ApiException.notFound("Inspección")));
    }

    public Inspeccion.Foto foto(Long id, Long fotoId) {
        return fotos.findByIdAndInspeccionId(fotoId, id).orElseThrow(() -> ApiException.notFound("Foto"));
    }

    private InspeccionResponse of(Inspeccion i) {
        List<Respuesta> items;
        try {
            items = json.readValue(i.getItems(), new TypeReference<List<Respuesta>>() {
            });
        } catch (java.io.IOException e) {
            items = List.of();
        }
        return new InspeccionResponse(i.getId(), i.getMaquinaria() == null ? null : i.getMaquinaria().getId(), i.getEquipo(),
                i.getTipo(), i.getFechaHora(), i.getHorometro(), i.getOperador() == null ? null : i.getOperador().getId(),
                i.getOperador() == null ? null : i.getOperador().getNombreCompleto(), items, i.getFallas(),
                i.getFallasCriticas(), i.getResultado(), i.getObservaciones(), i.getInspector(), fotos.idsDe(i.getId()));
    }
}
