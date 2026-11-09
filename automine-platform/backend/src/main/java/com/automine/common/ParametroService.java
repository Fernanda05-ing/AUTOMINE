package com.automine.common;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Parametros configurables del sistema (alcance "Administrativo > Configuracion"). */
@Service
@RequiredArgsConstructor
public class ParametroService {

    private final ParametroRepository repository;
    private final AuditService audit;

    public BigDecimal decimal(String clave) {
        return repository.findById(clave)
                .map(p -> new BigDecimal(p.getValor().trim()))
                .orElseThrow(() -> new IllegalStateException("Falta el parámetro " + clave));
    }

    public List<Parametro> listar() {
        return repository.findAll(Sort.by("clave"));
    }

    @Transactional
    public Parametro actualizar(String clave, String valor) {
        Parametro p = repository.findById(clave).orElseThrow(() -> ApiException.notFound("Parámetro"));
        try {
            new BigDecimal(valor.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("El valor debe ser numerico (use punto decimal)");
        }
        String anterior = p.getValor();
        p.setValor(valor.trim());
        audit.registrar("ACTUALIZAR", "Parámetro", clave, anterior + " -> " + p.getValor());
        return p;
    }
}
