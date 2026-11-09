package com.automine.security;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Usuario> findByResetToken(String resetToken);

    Optional<Usuario> findFirstByEmpleadoId(Long empleadoId);

    List<Usuario> findAllByOrderByNombreAsc();

    List<Usuario> findByRolCodigoInAndEstado(java.util.Collection<String> roles, String estado);
}
