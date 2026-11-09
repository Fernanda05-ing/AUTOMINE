package com.automine.certificados;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.certificados.CertificadoService.CertificadoResponse;
import com.automine.certificados.CertificadoService.ValidacionResponse;
import com.automine.common.Files;
import com.automine.common.Roles;
import com.automine.security.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "5. Certificados laborales")
public class CertificadoController {

    private final CertificadoService service;

    public record SolicitudRequest(@NotNull Long empleadoId, @Size(max = 150) String dirigidoA, Boolean incluyeSalario) {
    }

    @Operation(summary = "WorkCertificate - genera y descarga el certificado laboral en PDF")
    @GetMapping("/Certificate/{idEmpleado}")
    public ResponseEntity<byte[]> workCertificate(@PathVariable Long idEmpleado,
            @RequestParam(required = false) String dirigidoA,
            @RequestParam(defaultValue = "true") boolean incluyeSalario) {
        UsuarioActual.verificarEmpleado(idEmpleado, Roles.RRHH);
        CertificadoResponse c = service.generar(idEmpleado, dirigidoA, incluyeSalario);
        return Files.pdf(service.pdf(c.id()), "certificado-laboral-" + c.codigoValidacion() + ".pdf");
    }

    @Operation(summary = "Solicitar certificado (queda registrado y se descarga después)")
    @PostMapping("/Certificate")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificadoResponse request(@Valid @RequestBody SolicitudRequest req) {
        UsuarioActual.verificarEmpleado(req.empleadoId(), Roles.RRHH);
        return service.generar(req.empleadoId(), req.dirigidoA(), !Boolean.FALSE.equals(req.incluyeSalario()));
    }

    @GetMapping("/Certificate/Detail/{id}/Pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        CertificadoResponse c = service.obtener(id);
        UsuarioActual.verificarEmpleado(c.empleadoId(), Roles.RRHH);
        return Files.pdf(service.pdf(id), "certificado-laboral-" + c.codigoValidacion() + ".pdf");
    }

    @Operation(summary = "Certificados emitidos (RRHH: todos; empleado: los propios)")
    @GetMapping("/Certificates")
    public List<CertificadoResponse> list(@RequestParam(required = false) Long empleadoId) {
        UsuarioActual u = UsuarioActual.get();
        if (!u.tieneRol(Roles.ADMIN, Roles.RRHH, Roles.GERENTE)) {
            if (u.empleadoId() == null) {
                return List.of();
            }
            return service.listar(u.empleadoId());
        }
        return service.listar(empleadoId);
    }

    @PutMapping("/Certificate/{id}/Void")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public CertificadoResponse voidCertificate(@PathVariable Long id) {
        return service.anular(id);
    }

    @Operation(summary = "Validar un certificado por su código (público)")
    @GetMapping("/Certificate/Validate/{codigo}")
    public ValidacionResponse validate(@PathVariable String codigo) {
        return service.validar(codigo);
    }
}
