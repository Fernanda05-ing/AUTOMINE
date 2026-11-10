-- Tildes y eñes en los textos que se muestran desde la base de datos.
UPDATE roles SET nombre = 'Supervisor de mina', descripcion = 'Registro de producción y turnos' WHERE codigo = 'SUPERVISOR';
UPDATE roles SET descripcion = 'Acceso total y configuración del sistema' WHERE codigo = 'ADMIN';
UPDATE roles SET descripcion = 'Dashboard gerencial, reportes y producción' WHERE codigo = 'GERENTE';
UPDATE roles SET descripcion = 'Nómina, consignaciones, contabilidad y reportes' WHERE codigo = 'CONTADOR';
UPDATE roles SET descripcion = 'Inventario, kárdex y maquinaria' WHERE codigo = 'ALMACENISTA';

UPDATE parametros SET descripcion = 'Salario mínimo mensual legal vigente' WHERE clave = 'SALARIO_MINIMO';
UPDATE parametros SET descripcion = 'Horas laborales al mes para calcular el valor de la hora' WHERE clave = 'HORAS_MES';
UPDATE parametros SET descripcion = 'Factor hora extra dominical o festiva' WHERE clave = 'RECARGO_EXTRA_DOMINICAL';
UPDATE parametros SET descripcion = 'Aporte del empleado a pensión' WHERE clave = 'PORC_PENSION';
UPDATE parametros SET descripcion = 'Monto máximo de la caja menor' WHERE clave = 'CAJA_MENOR_TOPE';

-- Categorías y textos generados antes de la corrección (datos existentes)
UPDATE movimientos_contables SET categoria = 'Nómina' WHERE categoria = 'Nomina';
UPDATE movimientos_contables SET concepto = REPLACE(concepto, 'Pago nomina', 'Pago nómina') WHERE concepto LIKE 'Pago nomina%';
UPDATE movimientos_contables SET categoria = 'Regalías y contraprestaciones' WHERE categoria = 'Regalias y contraprestaciones';
UPDATE movimientos_contables SET categoria = 'Servicios públicos' WHERE categoria = 'Servicios publicos';
UPDATE movimientos_contables SET concepto = REPLACE(concepto, 'Servicios publicos', 'Servicios públicos') WHERE concepto LIKE 'Servicios publicos%';
UPDATE movimientos_contables SET concepto = REPLACE(concepto, 'Regalias', 'Regalías') WHERE concepto LIKE 'Regalias%';
UPDATE movimientos_contables SET concepto = REPLACE(concepto, 'Antioquena', 'Antioqueña') WHERE concepto LIKE '%Antioquena%';
UPDATE frentes_trabajo SET nombre = 'Túnel La Esperanza', ubicacion = 'Nivel 2 - Galería sur' WHERE nombre = 'Tunel La Esperanza';
UPDATE frentes_trabajo SET ubicacion = 'Nivel 3 - Galería norte' WHERE ubicacion = 'Nivel 3 - Galeria norte';
UPDATE frentes_trabajo SET mineral = 'Carbón' WHERE mineral = 'Carbon';
UPDATE productos SET nombre = 'Aceite hidráulico' WHERE nombre = 'Aceite hidraulico';

-- Nombres de los datos de demostración
UPDATE empleados SET nombres = REPLACE(nombres, 'Ramirez', 'Ramírez') WHERE nombres LIKE '%Ramirez%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Ramirez', 'Ramírez') WHERE apellidos LIKE '%Ramirez%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Ramirez', 'Ramírez') WHERE nombre LIKE '%Ramirez%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Gomez', 'Gómez') WHERE nombres LIKE '%Gomez%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Gomez', 'Gómez') WHERE apellidos LIKE '%Gomez%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Gomez', 'Gómez') WHERE nombre LIKE '%Gomez%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Lopez', 'López') WHERE nombres LIKE '%Lopez%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Lopez', 'López') WHERE apellidos LIKE '%Lopez%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Lopez', 'López') WHERE nombre LIKE '%Lopez%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Mejia', 'Mejía') WHERE nombres LIKE '%Mejia%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Mejia', 'Mejía') WHERE apellidos LIKE '%Mejia%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Mejia', 'Mejía') WHERE nombre LIKE '%Mejia%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Vasquez', 'Vásquez') WHERE nombres LIKE '%Vasquez%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Vasquez', 'Vásquez') WHERE apellidos LIKE '%Vasquez%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Vasquez', 'Vásquez') WHERE nombre LIKE '%Vasquez%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Marin', 'Marín') WHERE nombres LIKE '%Marin%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Marin', 'Marín') WHERE apellidos LIKE '%Marin%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Marin', 'Marín') WHERE nombre LIKE '%Marin%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Velez', 'Vélez') WHERE nombres LIKE '%Velez%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Velez', 'Vélez') WHERE apellidos LIKE '%Velez%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Velez', 'Vélez') WHERE nombre LIKE '%Velez%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Rios', 'Ríos') WHERE nombres LIKE '%Rios%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Rios', 'Ríos') WHERE apellidos LIKE '%Rios%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Rios', 'Ríos') WHERE nombre LIKE '%Rios%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Andres', 'Andrés') WHERE nombres LIKE '%Andres%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Andres', 'Andrés') WHERE apellidos LIKE '%Andres%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Andres', 'Andrés') WHERE nombre LIKE '%Andres%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Sebastian', 'Sebastián') WHERE nombres LIKE '%Sebastian%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Sebastian', 'Sebastián') WHERE apellidos LIKE '%Sebastian%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Sebastian', 'Sebastián') WHERE nombre LIKE '%Sebastian%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Ivan', 'Iván') WHERE nombres LIKE '%Ivan%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Ivan', 'Iván') WHERE apellidos LIKE '%Ivan%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Ivan', 'Iván') WHERE nombre LIKE '%Ivan%';
UPDATE empleados SET nombres = REPLACE(nombres, 'Maria', 'María') WHERE nombres LIKE '%Maria%';
UPDATE empleados SET apellidos = REPLACE(apellidos, 'Maria', 'María') WHERE apellidos LIKE '%Maria%';
UPDATE usuarios SET nombre = REPLACE(nombre, 'Maria', 'María') WHERE nombre LIKE '%Maria%';
