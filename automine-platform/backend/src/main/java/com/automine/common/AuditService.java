package com.automine.common;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** RNF005: registro de auditoria de las acciones que modifican datos. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditoriaRepository repository;

    public static String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "sistema" : auth.getName();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void registrar(String accion, String entidad, Object entidadId, String detalle) {
        Auditoria a = new Auditoria();
        a.setUsuario(usuarioActual());
        a.setAccion(accion);
        a.setEntidad(entidad);
        a.setEntidadId(entidadId == null ? null : String.valueOf(entidadId));
        a.setDetalle(detalle == null ? null : detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
        a.setFecha(LocalDateTime.now());
        repository.save(a);
    }
}
