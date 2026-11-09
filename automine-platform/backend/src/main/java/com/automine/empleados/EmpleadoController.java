package com.automine.empleados;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.Files;
import com.automine.common.Roles;
import com.automine.empleados.EmpleadoDtos.ContratoRequest;
import com.automine.empleados.EmpleadoDtos.EmpleadoOpcion;
import com.automine.empleados.EmpleadoDtos.EmpleadoRequest;
import com.automine.empleados.EmpleadoDtos.EmpleadoResponse;
import com.automine.empleados.EmpleadoDtos.InactivarRequest;
import com.automine.empleados.EmpleadoDtos.NovedadRequest;
import com.automine.empleados.EmpleadoDtos.ResumenEmpleados;
import com.automine.security.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "2. Empleados")
public class EmpleadoController {

    private final EmpleadoService service;

    @Operation(summary = "CreateEmployee - registrar empleado (RN004: RRHH)")
    @PostMapping("/Employee")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public EmpleadoResponse create(@Valid @RequestBody EmpleadoRequest req) {
        return service.crear(req);
    }

    @Operation(summary = "ListEmployees - consultar empleados")
    @GetMapping("/Employees")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','GERENTE')")
    public List<EmpleadoResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String estado) {
        return service.listar(q, estado);
    }

    @Operation(summary = "Lista corta de empleados para selectores")
    @GetMapping("/Employees/Options")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','GERENTE','CONTADOR','SUPERVISOR','SGSST','ALMACENISTA')")
    public List<EmpleadoOpcion> options(@RequestParam(defaultValue = "true") boolean activos) {
        return service.opciones(activos);
    }

    @GetMapping("/Employees/Summary")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','GERENTE')")
    public ResumenEmpleados summary() {
        return service.resumen();
    }

    @GetMapping("/Employee/{id}")
    public EmpleadoResponse get(@PathVariable Long id) {
        UsuarioActual.verificarEmpleado(id, Roles.RRHH, Roles.GERENTE, Roles.CONTADOR);
        return service.obtener(id);
    }

    @PutMapping("/Employee/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public EmpleadoResponse update(@PathVariable Long id, @Valid @RequestBody EmpleadoRequest req) {
        return service.editar(id, req);
    }

    @PutMapping("/Employee/{id}/Deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public EmpleadoResponse deactivate(@PathVariable Long id, @Valid @RequestBody InactivarRequest req) {
        return service.inactivar(id, req);
    }

    @PutMapping("/Employee/{id}/Reactivate")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public EmpleadoResponse reactivate(@PathVariable Long id) {
        return service.reactivar(id);
    }

    @GetMapping("/Employee/{id}/History")
    public List<HistorialLaboral> history(@PathVariable Long id) {
        UsuarioActual.verificarEmpleado(id, Roles.RRHH, Roles.GERENTE);
        return service.historial(id);
    }

    @PostMapping("/Employee/{id}/History")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public HistorialLaboral addHistory(@PathVariable Long id, @Valid @RequestBody NovedadRequest req) {
        return service.novedad(id, req);
    }

    @GetMapping("/Employee/{id}/Contracts")
    public List<Contrato> contracts(@PathVariable Long id) {
        UsuarioActual.verificarEmpleado(id, Roles.RRHH, Roles.GERENTE);
        return service.contratos(id);
    }

    @PostMapping("/Employee/{id}/Contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public Contrato addContract(@PathVariable Long id, @Valid @RequestBody ContratoRequest req) {
        return service.nuevoContrato(id, req);
    }

    @GetMapping("/Employee/{id}/Documents")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public List<DocumentoEmpleado.Resumen> documents(@PathVariable Long id) {
        return service.documentos(id);
    }

    @Operation(summary = "Adjuntar documento al empleado (PDF, imagen o Word, max 10 MB)")
    @PostMapping(value = "/Employee/{id}/Documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public DocumentoEmpleado.Resumen upload(@PathVariable Long id, @RequestParam(required = false) String tipo,
            @RequestPart("file") MultipartFile file) {
        return service.subirDocumento(id, tipo, file);
    }

    @GetMapping("/Employee/{id}/Documents/{documentoId}")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long documentoId) {
        DocumentoEmpleado d = service.documento(id, documentoId);
        return Files.download(d.getDatos(), d.getNombre(), MediaType.parseMediaType(d.getContentType()), false);
    }

    @DeleteMapping("/Employee/{id}/Documents/{documentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public void deleteDocument(@PathVariable Long id, @PathVariable Long documentoId) {
        service.eliminarDocumento(id, documentoId);
    }
}
