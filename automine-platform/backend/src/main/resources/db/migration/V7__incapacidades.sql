-- Incapacidades medicas y licencias con su soporte (certificado de la EPS/ARL).
CREATE TABLE incapacidades (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id        BIGINT        NOT NULL,
    tipo               VARCHAR(30)   NOT NULL,
    fecha_inicio       DATE          NOT NULL,
    fecha_fin          DATE          NOT NULL,
    dias               INT           NOT NULL,
    prorroga           BOOLEAN       NOT NULL,
    entidad            VARCHAR(120),
    numero_certificado VARCHAR(60),
    observaciones      VARCHAR(1000),
    estado             VARCHAR(20)   NOT NULL,
    motivo_rechazo     VARCHAR(500),
    soporte_nombre     VARCHAR(200),
    registrado_por     VARCHAR(150)  NOT NULL,
    revisado_por       VARCHAR(150),
    fecha_revision     DATETIME,
    created_at         DATETIME      NOT NULL,
    CONSTRAINT fk_incapacidad_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id)
);
CREATE INDEX ix_incapacidades_empleado ON incapacidades (empleado_id, estado);
CREATE INDEX ix_incapacidades_fechas ON incapacidades (fecha_inicio, fecha_fin);

CREATE TABLE soportes_incapacidad (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    incapacidad_id BIGINT       NOT NULL,
    nombre         VARCHAR(200) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    datos          LONGBLOB     NOT NULL,
    created_at     DATETIME     NOT NULL,
    CONSTRAINT uk_soporte_incapacidad UNIQUE (incapacidad_id),
    CONSTRAINT fk_soporte_incapacidad FOREIGN KEY (incapacidad_id) REFERENCES incapacidades (id)
);

-- La nomina muestra aparte los dias y el valor reconocido por incapacidad
ALTER TABLE nominas ADD COLUMN dias_incapacidad INT NOT NULL DEFAULT 0;
ALTER TABLE nominas ADD COLUMN valor_incapacidad DECIMAL(15,2) NOT NULL DEFAULT 0;
