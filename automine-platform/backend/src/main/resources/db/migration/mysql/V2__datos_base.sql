INSERT INTO roles (codigo, nombre, descripcion) VALUES
('ADMIN',       'Administrador',      'Acceso total y configuracion del sistema'),
('GERENTE',     'Gerente',            'Dashboard gerencial, reportes y produccion'),
('CONTADOR',    'Contador',           'Nomina, consignaciones, contabilidad y reportes'),
('RRHH',        'Recursos Humanos',   'Empleados, contratos y certificados'),
('SUPERVISOR',  'Supervisor de mina', 'Registro de produccion y turnos'),
('SGSST',       'Coordinador SG-SST', 'Accidentes, incidentes, EPP, capacitaciones y riesgos'),
('ALMACENISTA', 'Almacenista',        'Inventario, kardex y maquinaria'),
('EMPLEADO',    'Empleado',           'Consulta de pagos, certificados y turnos propios');

-- Valores legales (Colombia). Se editan desde Configuracion sin recompilar.
INSERT INTO parametros (clave, valor, descripcion) VALUES
('SALARIO_MINIMO',          '1423500', 'Salario minimo mensual legal vigente'),
('AUXILIO_TRANSPORTE',      '200000',  'Auxilio de transporte mensual'),
('HORAS_MES',               '220',     'Horas laborales al mes para calcular el valor hora'),
('RECARGO_EXTRA_DIURNA',    '1.25',    'Factor hora extra diurna'),
('RECARGO_EXTRA_NOCTURNA',  '1.75',    'Factor hora extra nocturna'),
('RECARGO_EXTRA_DOMINICAL', '2.00',    'Factor hora extra dominical o festiva'),
('PORC_SALUD',              '0.04',    'Aporte del empleado a salud'),
('PORC_PENSION',            '0.04',    'Aporte del empleado a pension'),
('PORC_SOLIDARIDAD',        '0.01',    'Fondo de solidaridad pensional (desde 4 SMMLV)'),
('CAJA_MENOR_TOPE',         '2000000', 'Monto maximo de la caja menor');
