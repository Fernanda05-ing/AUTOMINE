-- Insert roles
INSERT INTO roles (nombre, descripcion) VALUES
('ADMIN', 'Administrador del sistema'),
('USER', 'Usuario regular');

-- Insert admin user (password is 'admin' hashed with BCrypt)
INSERT INTO usuarios (nombre, contrasena, rol_id, estado) VALUES
('admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 1, 'ACTIVO');

-- Insert sample employees
INSERT INTO empleados (cedula, nombres, apellidos, telefono, direccion, cargo, area, salario, fecha_ingreso, estado) VALUES
('1234567890', 'Juan', 'Pérez', '0987654321', 'Calle 123', 'Desarrollador', 'TI', 2500.00, '2023-01-15', 'ACTIVO'),
('0987654321', 'María', 'García', '0987654322', 'Avenida 456', 'Analista', 'RRHH', 2200.00, '2023-02-01', 'ACTIVO');