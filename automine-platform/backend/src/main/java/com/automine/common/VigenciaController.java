package com.automine.common;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Valores de parametros por fecha de vigencia (salario minimo de cada año, jornada, recargos). */
@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
public class VigenciaController {

    public record VigenciaRequest(@NotBlank String clave, @NotNull LocalDate desde, @NotBlank String valor,
            @Size(max = 200) String norma) {
    }

    private final ParametroService parametros;

    @GetMapping("/Settings/Validity")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public List<ParametroVigencia> listar() {
        return parametros.vigencias();
    }

    @Operation(summary = "Agregar un valor que rige desde una fecha (p. ej. salario mínimo del próximo año)")
    @PostMapping("/Settings/Validity")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ParametroVigencia agregar(@Valid @RequestBody VigenciaRequest req) {
        return parametros.agregarVigencia(req.clave(), req.desde(), req.valor(), req.norma());
    }

    @DeleteMapping("/Settings/Validity/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void borrar(@PathVariable Long id) {
        parametros.borrarVigencia(id);
    }
}
