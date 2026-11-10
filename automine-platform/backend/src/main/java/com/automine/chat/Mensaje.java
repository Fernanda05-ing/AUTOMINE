package com.automine.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Mensaje del chat interno entre dos usuarios de AUTOMINE. */
@Entity
@Table(name = "mensajes")
@Getter
@Setter
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long remitenteId;
    private Long destinatarioId;
    private String texto;
    private Boolean leido;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (leido == null) {
            leido = false;
        }
    }

    public interface Repo extends JpaRepository<Mensaje, Long> {

        /** Mensajes entre dos usuarios, del mas reciente al mas antiguo, anteriores a un id. */
        @Query("select m from Mensaje m where ((m.remitenteId = :yo and m.destinatarioId = :otro) "
                + "or (m.remitenteId = :otro and m.destinatarioId = :yo)) and m.id < :antes order by m.id desc")
        List<Mensaje> conversacion(Long yo, Long otro, Long antes, Pageable pageable);

        /** Mensajes nuevos de una conversacion (para actualizar la pantalla abierta). */
        @Query("select m from Mensaje m where ((m.remitenteId = :yo and m.destinatarioId = :otro) "
                + "or (m.remitenteId = :otro and m.destinatarioId = :yo)) and m.id > :despues order by m.id")
        List<Mensaje> nuevos(Long yo, Long otro, Long despues);

        @Query("select m from Mensaje m where m.remitenteId = :yo or m.destinatarioId = :yo order by m.id desc")
        List<Mensaje> recientes(Long yo, Pageable pageable);

        @Query("select m.remitenteId, count(m) from Mensaje m where m.destinatarioId = :yo and m.leido = false "
                + "group by m.remitenteId")
        List<Object[]> noLeidosPorRemitente(Long yo);

        long countByDestinatarioIdAndLeidoFalse(Long destinatarioId);

        long countByRemitenteIdAndDestinatarioIdAndLeidoFalse(Long remitenteId, Long destinatarioId);

        @Modifying
        @Query("update Mensaje m set m.leido = true where m.destinatarioId = :yo and m.remitenteId = :otro "
                + "and m.leido = false")
        int marcarLeidos(Long yo, Long otro);
    }
}
