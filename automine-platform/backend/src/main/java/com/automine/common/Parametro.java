package com.automine.common;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "parametros")
@Getter
@Setter
public class Parametro {

    @Id
    private String clave;
    private String valor;
    private String descripcion;
}
