-- Usuarios con contraseña temporal (creados por otra persona) deben cambiarla al primer ingreso.
ALTER TABLE usuarios ADD COLUMN debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE;
