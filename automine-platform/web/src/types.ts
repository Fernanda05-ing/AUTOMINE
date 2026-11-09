// Tipos de las respuestas de la API usadas en varias pantallas.

export interface Kpi {
  clave: string;
  nombre: string;
  valor: number;
  unidad: string;
  anterior: number | null;
  variacion: number | null;
}

export interface Valor {
  etiqueta: string;
  valor: number;
}

export interface Cumplimiento {
  frente: string;
  toneladas: number;
  meta: number;
  porcentaje: number;
}

export interface Indicadores {
  desde: string;
  hasta: string;
  totalToneladas: number;
  promedioDiario: number;
  toneladasPorHora: number;
  toneladasPorTrabajador: number;
  diasConRegistro: number;
  registros: number;
  porTurno: Valor[];
  porFrente: Cumplimiento[];
  serieDiaria: Valor[];
}

export interface TendenciaMes {
  anio: number;
  mes: number;
  ingresos: number;
  gastos: number;
  utilidad: number;
}

export interface ResumenSst {
  accidentesMes: number;
  incidentesMes: number;
  diasIncapacidadMes: number;
  eventosAbiertos: number;
  diasSinAccidentes: number;
  riesgosAltos: number;
  capacitacionesProximas: number;
}

export interface Alerta {
  tipo: string;
  nivel: string;
  mensaje: string;
}

export interface Dashboard {
  fecha: string;
  kpis: Kpi[];
  produccionMes: Indicadores;
  finanzas: TendenciaMes[];
  sst: ResumenSst;
  alertas: Alerta[];
}

export interface EmpleadoOpcion {
  id: number;
  cedula: string;
  nombre: string;
  cargo: string;
  estado: string;
}

export interface Empleado {
  id: number;
  cedula: string;
  nombres: string;
  apellidos: string;
  nombreCompleto: string;
  email: string | null;
  telefono: string | null;
  direccion: string | null;
  fechaNacimiento: string | null;
  cargo: string;
  area: string | null;
  salario: number;
  fechaIngreso: string;
  fechaRetiro: string | null;
  tipoContrato: string;
  banco: string | null;
  tipoCuenta: string | null;
  numeroCuenta: string | null;
  eps: string | null;
  fondoPension: string | null;
  arl: string | null;
  estado: string;
  usuarioId: number | null;
  passwordTemporal: string | null;
}

export interface Nomina {
  id: number;
  periodoId: number;
  anio: number;
  mes: number;
  periodoEstado: string;
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  salarioBase: number;
  diasTrabajados: number;
  salarioDevengado: number;
  horasExtraDiurnas: number;
  horasExtraNocturnas: number;
  horasExtraDominicales: number;
  valorHorasExtra: number;
  bonificaciones: number;
  auxilioTransporte: number;
  totalDevengado: number;
  salud: number;
  pension: number;
  fondoSolidaridad: number;
  otrosDescuentos: number;
  detalleDescuentos: string | null;
  totalDescuentos: number;
  netoPagar: number;
  estado: string;
}

export interface Periodo {
  id: number;
  anio: number;
  mes: number;
  estado: string;
  liquidaciones: number;
  totalDevengado: number;
  totalDescuentos: number;
  totalNeto: number;
  aprobadoPor: string | null;
  fechaAprobacion: string | null;
}

export interface Consignacion {
  id: number;
  nominaId: number;
  empleadoId: number;
  cedula: string;
  empleado: string;
  anio: number;
  mes: number;
  netoNomina: number;
  banco: string;
  cuenta: string;
  valor: number;
  fechaPago: string;
  referencia: string;
  estado: string;
  tieneComprobante: boolean;
  comprobanteNombre: string | null;
  observaciones: string | null;
  registradoPor: string;
  aprobadoPor: string | null;
}

export interface Certificado {
  id: number;
  empleadoId: number;
  cedula: string;
  empleado: string;
  codigoValidacion: string;
  dirigidoA: string;
  incluyeSalario: boolean;
  estado: string;
  emitidoPor: string;
  fechaEmision: string;
  urlValidacion: string;
}

export interface TurnoEmpleado {
  produccionId: number;
  fecha: string;
  turno: string;
  frente: string;
  labor: string;
  toneladasTurno: number;
}
