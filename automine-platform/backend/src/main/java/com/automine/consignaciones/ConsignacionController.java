package com.automine.consignaciones;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.automine.consignaciones.ConsignacionDtos.AnularRequest;
import com.automine.consignaciones.ConsignacionDtos.ConsignacionRequest;
import com.automine.consignaciones.ConsignacionDtos.ConsignacionResponse;
import com.automine.consignaciones.ConsignacionDtos.LoteRequest;
import com.automine.consignaciones.ConsignacionDtos.LoteResponse;
import com.automine.security.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "4. Consignaciones")
public class ConsignacionController {

    private final ConsignacionService service;

    @Operation(summary = "RegisterTransfer - registrar consignación (RN002)")
    @PostMapping("/Transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ConsignacionResponse create(@Valid @RequestBody ConsignacionRequest req) {
        return service.registrar(req);
    }

    @Operation(summary = "Registrar consignaciones de todo un periodo aprobado")
    @PostMapping("/Transfer/Batch/{periodoId}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public LoteResponse batch(@PathVariable Long periodoId, @Valid @RequestBody LoteRequest req) {
        return service.registrarLote(periodoId, req.fechaPago(), req.prefijoReferencia());
    }

    @Operation(summary = "Consultar consignaciones (todas)")
    @GetMapping("/Transfers")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR','GERENTE')")
    public List<ConsignacionResponse> list(@RequestParam(required = false) Long periodoId,
            @RequestParam(required = false) String estado) {
        return service.listar(periodoId, estado);
    }

    @Operation(summary = "ListTransfers - consignaciones de un empleado")
    @GetMapping("/Transfers/{idEmpleado}")
    public List<ConsignacionResponse> byEmployee(@PathVariable Long idEmpleado) {
        UsuarioActual.verificarEmpleado(idEmpleado, Roles.CONTADOR, Roles.GERENTE);
        return service.delEmpleado(idEmpleado);
    }

    @GetMapping("/Transfer/{id}")
    public ConsignacionResponse get(@PathVariable Long id) {
        ConsignacionResponse c = service.obtener(id);
        UsuarioActual.verificarEmpleado(c.empleadoId(), Roles.CONTADOR, Roles.GERENTE);
        return c;
    }

    @Operation(summary = "Aprobar pago (RN005: solo Contabilidad)")
    @PutMapping("/Transfer/{id}/Approve")
    @PreAuthorize("hasRole('CONTADOR')")
    public ConsignacionResponse approve(@PathVariable Long id) {
        return service.aprobar(id);
    }

    @PutMapping("/Transfer/{id}/Void")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ConsignacionResponse voidTransfer(@PathVariable Long id, @Valid @RequestBody AnularRequest req) {
        return service.anular(id, req.motivo());
    }

    @Operation(summary = "Subir comprobante bancario (PDF/JPG/PNG)")
    @PostMapping(value = "/Transfer/{id}/Receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ConsignacionResponse upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return service.subirComprobante(id, file);
    }

    @GetMapping("/Transfer/{id}/Receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        ConsignacionResponse c = service.obtener(id);
        UsuarioActual.verificarEmpleado(c.empleadoId(), Roles.CONTADOR, Roles.GERENTE);
        Consignacion.Comprobante comp = service.comprobante(id);
        return Files.download(com.automine.common.ArchivoStore.contenido(comp.getDatos()), comp.getNombre(), MediaType.parseMediaType(comp.getContentType()),
                false);
    }
}
