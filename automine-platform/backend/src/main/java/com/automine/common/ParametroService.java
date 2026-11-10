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
    private final ParametroVigencia.Repo vigencias;

    public BigDecimal decimal(String clave) {
        return repository.findById(clave)
                .map(p -> new BigDecimal(p.getValor().trim()))
                .orElseThrow(() -> new IllegalStateException("Falta el parámetro " + clave));
    }

    /** Valor vigente en una fecha: la vigencia mas reciente anterior o igual, o el valor general. */
    public BigDecimal decimal(String clave, java.time.LocalDate fecha) {
        return vigencias.findFirstByClaveAndDesdeLessThanEqualOrderByDesdeDesc(clave, fecha)
                .map(v -> new BigDecimal(v.getValor().trim()))
                .orElseGet(() -> decimal(clave));
    }

    public List<ParametroVigencia> vigencias() {
        return vigencias.findAllByOrderByClaveAscDesdeAsc();
    }

    @Transactional
    public ParametroVigencia agregarVigencia(String clave, java.time.LocalDate desde, String valor, String norma) {
        if (repository.findById(clave).isEmpty()) {
            throw ApiException.notFound("Parámetro " + clave);
        }
        try {
            new BigDecimal(valor.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw ApiException.badRequest("El valor debe ser numérico (use punto decimal)");
        }
        if (desde == null) {
            throw ApiException.badRequest("Indique desde qué fecha rige");
        }
        if (vigencias.existsByClaveAndDesde(clave, desde)) {
            throw ApiException.conflict("Ya existe un valor de " + clave + " desde esa fecha");
        }
        ParametroVigencia v = new ParametroVigencia();
        v.setClave(clave);
        v.setDesde(desde);
        v.setValor(valor.trim());
        v.setNorma(norma == null || norma.isBlank() ? null : norma.trim());
        vigencias.save(v);
        audit.registrar("VIGENCIA", "Parámetro", clave, valor + " desde " + desde);
        return v;
    }

    @Transactional
    public void borrarVigencia(Long id) {
        ParametroVigencia v = vigencias.findById(id).orElseThrow(() -> ApiException.notFound("Vigencia"));
        vigencias.delete(v);
        audit.registrar("BORRAR_VIGENCIA", "Parámetro", v.getClave(), v.getValor() + " desde " + v.getDesde());
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
