package com.automine.security;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RF004: un token cerrado con Logout no vuelve a ser aceptado aunque no haya vencido. */
@Entity
@Table(name = "tokens_revocados")
@Getter
@Setter
@NoArgsConstructor
public class TokenRevocado {

    @Id
    private String jti;
    private LocalDateTime expira;

    public TokenRevocado(String jti, LocalDateTime expira) {
        this.jti = jti;
        this.expira = expira;
    }

    public interface Repo extends JpaRepository<TokenRevocado, String> {

        @Modifying
        @Query("delete from TokenRevocado t where t.expira < :ahora")
        int purgar(LocalDateTime ahora);
    }
}
