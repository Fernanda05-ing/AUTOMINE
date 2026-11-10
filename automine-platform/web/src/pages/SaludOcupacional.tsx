import { FilePlus2, FileText } from 'lucide-react';
import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api';
import { useAuth } from '../auth';
import { Badge, DataTable, ErrorBox, Field, FormModal, PageHeader, Spinner, Stat, Tabs, useForm, useLoad, useToast } from '../components/ui';
import { fmt, today } from '../format';
import type { EmpleadoOpcion } from '../types';

interface Estado {
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  estado: string;
  ultimoExamen: string | null;
  ultimoTipo: string | null;
  concepto: string | null;
  restricciones: string | null;
  proximaFecha: string | null;
  diasRestantes: number | null;
}
interface Examen {
  id: number;
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  tipo: string;
  fecha: string;
  concepto: string;
  restricciones: string | null;
  recomendaciones: string | null;
  ips: string | null;
  medico: string | null;
  proximaFecha: string | null;
  soporteNombre: string | null;
  registradoPor: string;
  vigencia: string;
}
interface IndicadorMes {
  anio: number;
  mes: number;
  trabajadores: number;
  accidentes: number;
  diasPerdidosAt: number;
  accidentesMortales: number;
  diasIncapacidad: number;
  diasProgramados: number;
  frecuencia: number;
  severidad: number;
  ausentismo: number;
}
interface Indicadores {
  anio: number;
  meses: IndicadorMes[];
  frecuenciaAnual: number;
  severidadAnual: number;
  mortalidad: number;
  ausentismoAnual: number;
  accidentes: number;
  diasIncapacidad: number;
  diasPorTipo: Record<string, number>;
}

type Vista = 'ESTADO' | 'HISTORIAL' | 'INDICADORES';
const TIPOS = ['INGRESO', 'PERIODICO', 'RETIRO', 'POST_INCAPACIDAD', 'CAMBIO_OCUPACION', 'ALTURAS'];
const CONCEPTOS = ['APTO', 'APTO_CON_RESTRICCIONES', 'APLAZADO', 'NO_APTO'];
const TONO: Record<string, string> = { VIGENTE: 'ok', POR_VENCER: 'warn', VENCIDO: 'danger', SIN_EXAMEN: 'danger', SIN_FECHA: '' };
const TONO_CONCEPTO: Record<string, string> = { APTO: 'ok', APTO_CON_RESTRICCIONES: 'warn', APLAZADO: 'warn', NO_APTO: 'danger' };
const AXIS = { fontSize: 11, fill: 'var(--muted)' };
const mesCorto = (m: number) => fmt.mes(m).substring(0, 3);
const ANIO_ACTUAL = new Date().getFullYear();

function orden(e: Estado) {
  return ({ VENCIDO: 0, SIN_EXAMEN: 1, POR_VENCER: 2, SIN_FECHA: 3, VIGENTE: 4 } as Record<string, number>)[e.estado] ?? 5;
}

/** Salud ocupacional: evaluaciones médicas con vencimientos e indicadores del SG-SST (Res. 0312 de 2019). */
export default function SaludOcupacional() {
  const { can } = useAuth();
  const toast = useToast();
  const gestiona = can('SGSST', 'RRHH');
  const [vista, setVista] = useState<Vista>('ESTADO');
  const [anio, setAnio] = useState(ANIO_ACTUAL);
  const [nuevo, setNuevo] = useState(false);
  const [archivo, setArchivo] = useState<File | null>(null);

  const estado = useLoad(() => (vista === 'ESTADO' ? api.get<Estado[]>('/MedicalExams/Status') : Promise.resolve([])), [vista]);
  const historial = useLoad(() => (vista === 'HISTORIAL' ? api.get<Examen[]>('/MedicalExams') : Promise.resolve([])), [vista]);
  const indicadores = useLoad(() => (vista === 'INDICADORES' ? api.get<Indicadores>('/Sst/Indicators', { anio }) : Promise.resolve(null)), [vista, anio]);
  const empleados = useLoad(() => (gestiona ? api.get<EmpleadoOpcion[]>('/Employees/Options') : Promise.resolve([])));
  const f = useForm({ empleadoId: '', tipo: 'PERIODICO', fecha: today(), concepto: 'APTO', restricciones: '', recomendaciones: '', ips: '', medico: '', proximaFecha: '' });

  const est = estado.data ?? [];
  const cuenta = (e: string) => est.filter((x) => x.estado === e).length;
  const ind = indicadores.data;

  return (
    <>
      <PageHeader title="Salud ocupacional" subtitle="Evaluaciones médicas ocupacionales e indicadores del SG-SST">
        {gestiona && (
          <button className="btn primary" onClick={() => { f.reset(); setArchivo(null); setNuevo(true); }}>
            <FilePlus2 /> Registrar examen
          </button>
        )}
      </PageHeader>
      <Tabs<Vista>
        tabs={[
          { key: 'ESTADO', label: 'Estado por trabajador' },
          { key: 'HISTORIAL', label: 'Historial de exámenes' },
          { key: 'INDICADORES', label: 'Indicadores' },
        ]}
        active={vista}
        onChange={setVista}
      />

      {vista === 'ESTADO' && (
        <>
          <div className="grid grid-4 mb">
            <Stat label="Vigentes" value={cuenta('VIGENTE')} />
            <Stat label="Por vencer (30 días)" value={cuenta('POR_VENCER')} />
            <Stat label="Vencidos" value={cuenta('VENCIDO')} />
            <Stat label="Sin examen registrado" value={cuenta('SIN_EXAMEN')} />
          </div>
          <ErrorBox error={estado.error} />
          {estado.loading && !estado.data ? (
            <Spinner />
          ) : (
            <DataTable<Estado>
              rows={[...est].sort((a, b) => orden(a) - orden(b))}
              search={(e) => `${e.empleado} ${e.cedula} ${e.cargo}`}
              searchPlaceholder="Buscar trabajador"
              columns={[
                { key: 'e', label: 'Trabajador', render: (e) => <><strong>{e.empleado}</strong><div className="muted">{e.cedula} · {e.cargo}</div></> },
                { key: 's', label: 'Estado', render: (e) => <Badge value={e.estado} tone={TONO[e.estado]} /> },
                { key: 'u', label: 'Último examen', render: (e) => (e.ultimoExamen ? <>{fmt.date(e.ultimoExamen)}<div className="muted">{fmt.label(e.ultimoTipo)}</div></> : '—') },
                { key: 'c', label: 'Concepto', render: (e) => (e.concepto ? <Badge value={e.concepto} tone={TONO_CONCEPTO[e.concepto]} /> : '') },
                { key: 'r', label: 'Restricciones', clamp: true, render: (e) => e.restricciones ?? '' },
                {
                  key: 'p',
                  label: 'Próxima evaluación',
                  render: (e) =>
                    e.proximaFecha ? (
                      <>
                        {fmt.date(e.proximaFecha)}
                        {e.diasRestantes !== null && <div className="muted">{e.diasRestantes < 0 ? `vencido hace ${-e.diasRestantes} días` : `en ${e.diasRestantes} días`}</div>}
                      </>
                    ) : (
                      '—'
                    ),
                },
              ]}
            />
          )}
          <p className="muted mt">SG-SST recibe cada lunes un correo con los exámenes vencidos, por vencer y faltantes.</p>
        </>
      )}

      {vista === 'HISTORIAL' && (
        <>
          <ErrorBox error={historial.error} />
          <DataTable<Examen>
            rows={historial.data}
            empty="No hay exámenes registrados"
            search={(x) => `${x.empleado} ${x.cedula} ${x.ips ?? ''}`}
            searchPlaceholder="Buscar trabajador o IPS"
            columns={[
              { key: 'f', label: 'Fecha', render: (x) => fmt.date(x.fecha) },
              { key: 'e', label: 'Trabajador', render: (x) => <><strong>{x.empleado}</strong><div className="muted">{x.cedula}</div></> },
              { key: 't', label: 'Tipo', render: (x) => fmt.label(x.tipo) },
              { key: 'c', label: 'Concepto', render: (x) => <Badge value={x.concepto} tone={TONO_CONCEPTO[x.concepto]} /> },
              { key: 'r', label: 'Restricciones / recomendaciones', clamp: true, render: (x) => [x.restricciones, x.recomendaciones].filter(Boolean).join(' · ') },
              { key: 'i', label: 'IPS', render: (x) => x.ips ?? '' },
              { key: 'p', label: 'Próxima', render: (x) => (x.proximaFecha ? fmt.date(x.proximaFecha) : '—') },
              {
                key: 'a',
                label: '',
                render: (x) =>
                  x.soporteNombre && (
                    <button className="btn small" onClick={() => api.open(`/MedicalExam/${x.id}/Support`).catch((e) => toast.error(e.message))}>
                      <FileText /> Certificado
                    </button>
                  ),
              },
            ]}
          />
        </>
      )}

      {vista === 'INDICADORES' && (
        <>
          <div className="toolbar mb">
            <select value={anio} onChange={(e) => setAnio(Number(e.target.value))} aria-label="Año">
              {[ANIO_ACTUAL - 1, ANIO_ACTUAL].map((a) => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>
          <ErrorBox error={indicadores.error} />
          {indicadores.loading && !ind ? (
            <Spinner />
          ) : (
            ind && (
              <>
                <div className="grid grid-4 mb">
                  <Stat label="Frecuencia de accidentalidad" value={`${fmt.num(ind.frecuenciaAnual)} %`} hint={`${ind.accidentes} accidentes de trabajo`} />
                  <Stat label="Severidad de accidentalidad" value={`${fmt.num(ind.severidadAnual)} %`} hint="Días perdidos por AT / trabajadores" />
                  <Stat label="Mortalidad" value={`${fmt.num(ind.mortalidad)} %`} hint="AT mortales / AT del año" />
                  <Stat label="Ausentismo por causa médica" value={`${fmt.num(ind.ausentismoAnual)} %`} hint={`${ind.diasIncapacidad} días de incapacidad`} />
                </div>
                <div className="grid grid-2 mb">
                  <div className="card">
                    <h3>Accidentalidad por mes</h3>
                    <div className="chart-box">
                      <ResponsiveContainer>
                        <BarChart data={ind.meses.map((m) => ({ mes: mesCorto(m.mes), Frecuencia: m.frecuencia, Severidad: m.severidad }))}>
                          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                          <XAxis dataKey="mes" tick={AXIS} />
                          <YAxis tick={AXIS} width={40} />
                          <Tooltip formatter={(v: number) => `${fmt.num(v)} %`} />
                          <Legend />
                          <Bar dataKey="Frecuencia" fill="var(--primary)" radius={[4, 4, 0, 0]} />
                          <Bar dataKey="Severidad" fill="#b45309" radius={[4, 4, 0, 0]} />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                  <div className="card">
                    <h3>Ausentismo por causa médica</h3>
                    <div className="chart-box">
                      <ResponsiveContainer>
                        <LineChart data={ind.meses.map((m) => ({ mes: mesCorto(m.mes), Ausentismo: m.ausentismo }))}>
                          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                          <XAxis dataKey="mes" tick={AXIS} />
                          <YAxis tick={AXIS} width={40} />
                          <Tooltip formatter={(v: number) => `${fmt.num(v)} %`} />
                          <Line dataKey="Ausentismo" stroke="var(--primary)" strokeWidth={2} dot />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                </div>
                <DataTable<IndicadorMes>
                  rows={ind.meses}
                  pageSize={25}
                  columns={[
                    { key: 'm', label: 'Mes', render: (m) => fmt.mes(m.mes) },
                    { key: 't', label: 'Trabajadores', num: true, render: (m) => m.trabajadores },
                    { key: 'a', label: 'Accidentes', num: true, render: (m) => m.accidentes },
                    { key: 'd', label: 'Días perdidos AT', num: true, render: (m) => m.diasPerdidosAt },
                    { key: 'i', label: 'Días incapacidad', num: true, render: (m) => m.diasIncapacidad },
                    { key: 'f', label: 'Frecuencia', num: true, render: (m) => `${fmt.num(m.frecuencia)} %` },
                    { key: 's', label: 'Severidad', num: true, render: (m) => `${fmt.num(m.severidad)} %` },
                    { key: 'au', label: 'Ausentismo', num: true, render: (m) => `${fmt.num(m.ausentismo)} %` },
                  ]}
                />
                {Object.keys(ind.diasPorTipo).length > 0 && (
                  <p className="muted mt">
                    Días de incapacidad por causa: {Object.entries(ind.diasPorTipo).map(([k, v]) => `${fmt.label(k)} ${v}`).join(' · ')}
                  </p>
                )}
                <p className="muted">Fórmulas de la Resolución 0312 de 2019 (art. 30). Días programados = trabajadores × 30 días del mes.</p>
              </>
            )
          )}
        </>
      )}

      {nuevo && (
        <FormModal
          title="Registrar evaluación médica ocupacional"
          wide
          onClose={() => setNuevo(false)}
          onSubmit={async () => {
            const fd = new FormData();
            Object.entries(f.values).forEach(([k, val]) => {
              if (val) fd.append(k, String(val));
            });
            if (archivo) fd.append('soporte', archivo);
            await api.form('POST', '/MedicalExam', fd);
            toast.ok('Examen registrado');
            setNuevo(false);
            estado.reload();
            historial.reload();
          }}
        >
          {() => (
            <div className="form-grid">
              <Field label="Trabajador *" full>
                <select required {...f.bind('empleadoId')}>
                  <option value="">Seleccione...</option>
                  {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cedula}</option>)}
                </select>
              </Field>
              <Field label="Tipo de evaluación *"><select {...f.bind('tipo')}>{TIPOS.map((t) => <option key={t} value={t}>{fmt.label(t)}</option>)}</select></Field>
              <Field label="Fecha *"><input type="date" required max={today()} {...f.bind('fecha')} /></Field>
              <Field label="Concepto de aptitud *"><select {...f.bind('concepto')}>{CONCEPTOS.map((t) => <option key={t} value={t}>{fmt.label(t)}</option>)}</select></Field>
              <Field label="Próxima evaluación (vacío = en un año)"><input type="date" {...f.bind('proximaFecha')} /></Field>
              <Field label={f.values.concepto === 'APTO_CON_RESTRICCIONES' ? 'Restricciones *' : 'Restricciones'} full>
                <textarea rows={2} maxLength={1000} required={f.values.concepto === 'APTO_CON_RESTRICCIONES'} {...f.bind('restricciones')} />
              </Field>
              <Field label="Recomendaciones" full><textarea rows={2} maxLength={1000} {...f.bind('recomendaciones')} /></Field>
              <Field label="IPS"><input maxLength={150} {...f.bind('ips')} /></Field>
              <Field label="Médico"><input maxLength={150} {...f.bind('medico')} /></Field>
              <Field label="Certificado de aptitud (PDF o foto)" full>
                <input type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} />
              </Field>
              <p className="muted full">Solo se registra el concepto de aptitud: la historia clínica la guarda la IPS (reserva médica).</p>
            </div>
          )}
        </FormModal>
      )}
    </>
  );
}
