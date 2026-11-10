-- Valores de parametros que cambian en una fecha (salario minimo, auxilio, jornada, recargos).
-- La nomina usa el valor vigente en el ultimo dia del mes liquidado; si no hay vigencia, el de "parametros".
CREATE TABLE parametros_vigencia (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    clave        VARCHAR(60)   NOT NULL,
    desde        DATE          NOT NULL,
    valor        VARCHAR(100)  NOT NULL,
    norma        VARCHAR(200),
    created_at   DATETIME      NOT NULL,
    CONSTRAINT uk_parametro_vigencia UNIQUE (clave, desde)
);

INSERT INTO parametros_vigencia (clave, desde, valor, norma, created_at) VALUES
('SALARIO_MINIMO',          '2025-01-01', '1423500', 'Decreto 1572 de 2024', CURRENT_TIMESTAMP),
('AUXILIO_TRANSPORTE',      '2025-01-01', '200000',  'Decreto 1573 de 2024', CURRENT_TIMESTAMP),
('SALARIO_MINIMO',          '2026-01-01', '1750905', 'Decreto de salario mínimo 2026', CURRENT_TIMESTAMP),
('AUXILIO_TRANSPORTE',      '2026-01-01', '249095',  'Decreto de auxilio de transporte 2026', CURRENT_TIMESTAMP),
('HORAS_MES',               '2025-07-15', '220',     'Ley 2101 de 2021 (44 h semanales)', CURRENT_TIMESTAMP),
('HORAS_MES',               '2026-07-15', '210',     'Ley 2101 de 2021 (42 h semanales)', CURRENT_TIMESTAMP),
('RECARGO_EXTRA_DOMINICAL', '2025-07-01', '2.05',    'Ley 2466 de 2025 (dominical 80 %)', CURRENT_TIMESTAMP),
('RECARGO_EXTRA_DOMINICAL', '2026-07-01', '2.15',    'Ley 2466 de 2025 (dominical 90 %)', CURRENT_TIMESTAMP),
('RECARGO_EXTRA_DOMINICAL', '2027-07-01', '2.25',    'Ley 2466 de 2025 (dominical 100 %)', CURRENT_TIMESTAMP);

-- Celulares registrados para notificaciones push (Firebase Cloud Messaging)
CREATE TABLE dispositivos (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT        NOT NULL,
    token       VARCHAR(400)  NOT NULL,
    plataforma  VARCHAR(20)   NOT NULL,
    created_at  DATETIME      NOT NULL,
    CONSTRAINT uk_dispositivo_token UNIQUE (token),
    CONSTRAINT fk_dispositivo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);
