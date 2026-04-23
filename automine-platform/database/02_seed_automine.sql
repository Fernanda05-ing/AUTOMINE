USE automine;

INSERT IGNORE INTO roles (id, code, name, description, active) VALUES
(1, 'ADMIN', 'Administrador', 'Control total de plataforma', TRUE),
(2, 'RRHH', 'Recursos Humanos', 'Gestion de empleados y nomina', TRUE),
(3, 'FINANCE', 'Finanzas', 'Control contable y consignaciones', TRUE),
(4, 'SUPERVISOR', 'Supervisor', 'Operacion minera y reportes de turno', TRUE),
(5, 'SST', 'Seguridad y Salud en el Trabajo', 'Gestion SG-SST', TRUE),
(6, 'WAREHOUSE', 'Almacen', 'Gestion de inventario y EPP', TRUE);

INSERT IGNORE INTO users (id, role_id, username, email, password_hash, email_verified, status)
VALUES (1, 1, 'admin', 'admin@automine.co', '$2a$10$8MefV4qf2A3vW0q2D9EE6eYzvz9M67Q/5u4wLhM3y7Bo53F8wAKUe', TRUE, 'ACTIVE');
