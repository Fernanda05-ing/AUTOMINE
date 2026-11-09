const money = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const number = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });

export const fmt = {
  money: (v?: number | null) => (v === null || v === undefined ? '' : money.format(v)),
  num: (v?: number | null) => (v === null || v === undefined ? '' : number.format(v)),
  date: (v?: string | null) => {
    if (!v) return '';
    const [y, m, d] = v.substring(0, 10).split('-');
    return `${d}/${m}/${y}`;
  },
  dateTime: (v?: string | null) => {
    if (!v) return '';
    return `${fmt.date(v)} ${v.substring(11, 16)}`;
  },
  mes: (m: number) =>
    ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'][m - 1] ?? String(m),
  /** Convierte un codigo interno (MANANA, EXAMEN_MEDICO...) en texto legible con tildes. */
  label: (v?: string | null) => {
    if (!v) return '';
    if (ETIQUETAS[v]) return ETIQUETAS[v];
    const t = v
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/[a-z]+/g, (w) => TILDES[w] ?? w);
    return t.charAt(0).toUpperCase() + t.slice(1);
  },
};

const ETIQUETAS: Record<string, string> = {
  SGSST: 'SG-SST',
  LOGIN_OK: 'Inicio de sesión',
  LOGIN_FALLIDO: 'Intento fallido',
  LOGIN_BLOQUEADO: 'Acceso bloqueado',
  LOGOUT: 'Cierre de sesión',
  CAMBIO_PASSWORD: 'Cambio de contraseña',
  RECUPERACION_SOLICITADA: 'Recuperación solicitada',
  OBRA_LABOR: 'Obra o labor',
  PRESTACION_SERVICIOS: 'Prestación de servicios',
  FUERA_SERVICIO: 'Fuera de servicio',
  LLAMADO_ATENCION: 'Llamado de atención',
  HOJA_DE_VIDA: 'Hoja de vida',
  CERTIFICADO_ESTUDIO: 'Certificado de estudio',
  EXAMEN_MEDICO: 'Examen médico',
  ACCIDENTE_LABORAL: 'Accidente laboral',
  CAJA_MENOR: 'Caja menor',
};

const TILDES: Record<string, string> = {
  manana: 'mañana', observacion: 'observación', atencion: 'atención', capacitacion: 'capacitación',
  fisico: 'físico', quimico: 'químico', biologico: 'biológico', biomecanico: 'biomecánico', mecanico: 'mecánico',
  electrico: 'eléctrico', publico: 'público', critico: 'crítico', cedula: 'cédula', medico: 'médico',
  afiliacion: 'afiliación', reposicion: 'reposición', nomina: 'nómina', prestacion: 'prestación',
  recuperacion: 'recuperación', produccion: 'producción', anulacion: 'anulación', contrasena: 'contraseña',
  almacen: 'almacén', perforacion: 'perforación', operacion: 'operación',
};

export function today(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().substring(0, 10);
}

export function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().substring(0, 10);
}

export function firstOfMonth(): string {
  return today().substring(0, 8) + '01';
}

export function nowLocal(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().substring(0, 16);
}
