-- Evaluaciones medicas ocupacionales (Resolucion 1843 de 2025 / 2346 de 2007).
-- Solo se guarda el concepto de aptitud y las restricciones: la historia clinica la custodia la IPS.
CREATE TABLE examenes_medicos (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id      BIGINT        NOT NULL,
    tipo             VARCHAR(30)   NOT NULL,
    fecha            DATE          NOT NULL,
    concepto         VARCHAR(30)   NOT NULL,
    restricciones    VARCHAR(1000),
    recomendaciones  VARCHAR(1000),
    ips              VARCHAR(150),
    medico           VARCHAR(150),
    proxima_fecha    DATE,
    soporte_nombre   VARCHAR(200),
    soporte_tipo     VARCHAR(100),
    soporte          LONGBLOB,
    registrado_por   VARCHAR(150)  NOT NULL,
    created_at       DATETIME      NOT NULL,
    CONSTRAINT fk_examen_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id)
);
CREATE INDEX ix_examenes_empleado ON examenes_medicos (empleado_id, fecha);
CREATE INDEX ix_examenes_proxima ON examenes_medicos (proxima_fecha);

-- Inspecciones preoperacionales de maquinaria, vehiculos y equipos
CREATE TABLE inspecciones (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    maquinaria_id   BIGINT,
    equipo          VARCHAR(150)  NOT NULL,
    tipo            VARCHAR(30)   NOT NULL,
    fecha_hora      DATETIME      NOT NULL,
    horometro       DECIMAL(12,1),
    operador_id     BIGINT,
    items           TEXT          NOT NULL,
    fallas          INT           NOT NULL,
    fallas_criticas INT           NOT NULL,
    resultado       VARCHAR(20)   NOT NULL,
    observaciones   VARCHAR(1000),
    inspector       VARCHAR(150)  NOT NULL,
    created_at      DATETIME      NOT NULL,
    CONSTRAINT fk_inspeccion_maquinaria FOREIGN KEY (maquinaria_id) REFERENCES maquinaria (id),
    CONSTRAINT fk_inspeccion_operador FOREIGN KEY (operador_id) REFERENCES empleados (id)
);
CREATE INDEX ix_inspecciones_fecha ON inspecciones (fecha_hora);

CREATE TABLE fotos_inspeccion (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspeccion_id  BIGINT        NOT NULL,
    item           VARCHAR(150),
    content_type   VARCHAR(100)  NOT NULL,
    datos          LONGBLOB      NOT NULL,
    created_at     DATETIME      NOT NULL,
    CONSTRAINT fk_foto_inspeccion FOREIGN KEY (inspeccion_id) REFERENCES inspecciones (id)
);
