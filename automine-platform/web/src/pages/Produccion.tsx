import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { CumplimientoFrentes, PorTurno, ProduccionDiaria } from '../components/Charts';
import { ActionButton, Badge, DataTable, Field, FormModal, PageHeader, Spinner, Stat, Tabs, useForm, useLoad, useToast } from '../components/ui';
import { daysAgo, fmt, today } from '../format';
import type { EmpleadoOpcion, Indicadores } from '../types';

interface Frente {
  id: number;
  nombre: string;
  ubicacion: string | null;
  mineral: string;
  metaDiariaToneladas: number;
  activo: boolean;
}
interface Asignacion {
  empleadoId: number;
  empleado: string;
  cedula: string;
  labor: string;
}
interface Registro {
  id: number;
  fecha: string;
  turno: string;
  frenteId: number;
  frente: string;
  mineral: string;
  toneladas: number;
  horasOperacion: number;
  observaciones: string | null;
  registradoPor: string;
  personal: number;
  asignaciones: Asignacion[];
}
interface ReporteDiario {
  fecha: string;
  totalToneladas: number;
  metaDia: number;
  cumplimiento: number;
  personal: number;
  lineas: { frente: string; turno: string; toneladas: number; horas: number; personal: number; observaciones: string | null }[];
}

const TURNOS = ['MANANA', 'TARDE', 'NOCHE'];
const LABORES = ['Perforación', 'Voladura', 'Cargue', 'Acarreo', 'Sostenimiento', 'Operación de equipo', 'Supervisión'];

function RegistroForm({ registro, frentes, onClose, onSaved }: { registro?: Registro; frentes: Frente[]; onClose: () => void; onSaved: () => void }) {
  const empleados = useLoad(() => api.get<EmpleadoOpcion[]>('/Employees/Options'));
  const f = useForm({
    fecha: registro?.fecha ?? today(),
    turno: registro?.turno ?? 'MANANA',
    frenteId: String(registro?.frenteId ?? frentes.find((x) => x.activo)?.id ?? ''),
    toneladas: registro ? String(registro.toneladas) : '',
    horasOperacion: registro ? String(registro.horasOperacion) : '8',
    observaciones: registro?.observaciones ?? '',
  });
  const [asig, setAsig] = useState<{ empleadoId: string; labor: string }[]>(
    registro?.asignaciones.map((a) => ({ empleadoId: String(a.empleadoId), labor: a.labor })) ?? [{ empleadoId: '', labor: LABORES[0] }],
  );
  return (
    <FormModal
      title={registro ? 'Editar registro de producción' : 'Registrar producción del turno'}
      wide
      onClose={onClose}
      onSubmit={async () => {
        const body = {
          ...f.values,
          frenteId: Number(f.values.frenteId),
          toneladas: Number(f.values.toneladas),
          horasOperacion: Number(f.values.horasOperacion),
          asignaciones: asig.filter((a) => a.empleadoId).map((a) => ({ empleadoId: Number(a.empleadoId), labor: a.labor })),
        };
        if (registro) await api.put(`/Production/${registro.id}`, body);
        else await api.post('/Production', body);
        onSaved();
      }}
    >
      {(err) => (
        <>
          <div className="form-grid">
            <Field label="Fecha *" error={err.fecha}><input type="date" max={today()} required {...f.bind('fecha')} /></Field>
            <Field label="Turno *"><select {...f.bind('turno')}>{TURNOS.map((t) => <option key={t} value={t}>{fmt.label(t)}</option>)}</select></Field>
            <Field label="Frente de trabajo *">
              <select required {...f.bind('frenteId')}>{frentes.filter((x) => x.activo || String(x.id) === f.values.frenteId).map((x) => <option key={x.id} value={x.id}>{x.nombre} ({x.mineral})</option>)}</select>
            </Field>
            <Field label="Toneladas extraídas *" error={err.toneladas}><input type="number" step="0.1" min={0} required {...f.bind('toneladas')} /></Field>
            <Field label="Horas de operación" error={err.horasOperacion}><input type="number" step="0.5" min={0} max={12} {...f.bind('horasOperacion')} /></Field>
            <Field label="Observaciones"><input {...f.bind('observaciones')} /></Field>
          </div>
          <h3 className="mt" style={{ fontSize: 14 }}>Personal asignado al turno</h3>
          {asig.map((a, i) => (
            <div className="actions" key={i} style={{ marginBottom: 6 }}>
              <select style={{ flex: 2 }} value={a.empleadoId} onChange={(e) => setAsig(asig.map((x, j) => (j === i ? { ...x, empleadoId: e.target.value } : x)))} aria-label="Trabajador">
                <option value="">Seleccione trabajador...</option>
                {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cargo}</option>)}
              </select>
              <select style={{ flex: 1 }} value={a.labor} onChange={(e) => setAsig(asig.map((x, j) => (j === i ? { ...x, labor: e.target.value } : x)))} aria-label="Labor">
                {LABORES.map((l) => <option key={l}>{l}</option>)}
              </select>
              <button type="button" className="btn small" onClick={() => setAsig(asig.filter((_, j) => j !== i))}>Quitar</button>
            </div>
          ))}
          <button type="button" className="btn small" onClick={() => setAsig([...asig, { empleadoId: '', labor: LABORES[0] }])}>+ Agregar trabajador</button>
        </>
      )}
    </FormModal>
  );
}

export default function Produccion() {
  const { can } = useAuth();
  const toast = useToast();
  const escribe = can('SUPERVISOR');
  const [tab, setTab] = useState<'registros' | 'diario' | 'indicadores' | 'frentes'>('registros');
  const [desde, setDesde] = useState(daysAgo(7));
  const [hasta, setHasta] = useState(today());
  const [frenteId, setFrenteId] = useState('');
  const [fechaDia, setFechaDia] = useState(today());
  const frentes = useLoad(() => api.get<Frente[]>('/Fronts'));
  const registros = useLoad(() => api.get<Registro[]>('/Production', { desde, hasta, frenteId }), [desde, hasta, frenteId]);
  const diario = useLoad(() => api.get<ReporteDiario>('/Production/DailyReport', { fecha: fechaDia }), [fechaDia]);
  const ind = useLoad(() => api.get<Indicadores>('/Production/Indicators', { desde, hasta }), [desde, hasta]);
  const [form, setForm] = useState<{ registro?: Registro } | null>(null);
  const [frenteForm, setFrenteForm] = useState<{ frente?: Frente } | null>(null);
  const ff = useForm({ nombre: '', ubicacion: '', mineral: 'Oro', metaDiariaToneladas: '50', activo: true });

  const rango = (
    <>
      <Field label="Desde"><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></Field>
      <Field label="Hasta"><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></Field>
    </>
  );

  return (
    <>
      <PageHeader title="Producción minera" subtitle="Toneladas por frente y turno, personal asignado e indicadores de rendimiento">
        <button className="btn" onClick={() => api.download('/Reports/produccion/excel', { desde, hasta }).catch((e) => toast.error(e.message))}>Excel</button>
        <button className="btn" onClick={() => api.download('/Reports/produccion/pdf', { desde, hasta }).catch((e) => toast.error(e.message))}>PDF</button>
        {escribe && <button className="btn primary" onClick={() => setForm({})}>Registrar producción</button>}
      </PageHeader>
      <Tabs
        tabs={[
          { key: 'registros', label: 'Registros' },
          { key: 'diario', label: 'Reporte diario' },
          { key: 'indicadores', label: 'Indicadores' },
          { key: 'frentes', label: 'Frentes de trabajo' },
        ]}
        active={tab}
        onChange={setTab}
      />
      {tab === 'registros' && (
        <>
          <div className="toolbar">
            {rango}
            <Field label="Frente">
              <select value={frenteId} onChange={(e) => setFrenteId(e.target.value)}>
                <option value="">Todos</option>
                {frentes.data?.map((x) => <option key={x.id} value={x.id}>{x.nombre}</option>)}
              </select>
            </Field>
          </div>
          <DataTable<Registro>
            rows={registros.data}
            search={(r) => `${r.frente} ${r.turno} ${r.observaciones ?? ''} ${r.asignaciones.map((a) => a.empleado).join(' ')}`}
            searchPlaceholder="Buscar por frente, turno, trabajador u observación"
            columns={[
              { key: 'f', label: 'Fecha', render: (r) => fmt.date(r.fecha) },
              { key: 't', label: 'Turno', render: (r) => fmt.label(r.turno) },
              { key: 'fr', label: 'Frente', render: (r) => r.frente },
              { key: 'to', label: 'Toneladas', num: true, render: (r) => <strong>{fmt.num(r.toneladas)}</strong> },
              { key: 'h', label: 'Horas', num: true, render: (r) => fmt.num(r.horasOperacion) },
              { key: 'p', label: 'Personal', render: (r) => <span title={r.asignaciones.map((a) => `${a.empleado} (${a.labor})`).join('\n')}>{r.personal}</span> },
              { key: 'o', label: 'Observaciones', clamp: true, render: (r) => r.observaciones },
              {
                key: 'a',
                label: '',
                render: (r) =>
                  escribe && (
                    <div className="actions">
                      <button className="btn small" onClick={() => setForm({ registro: r })}>Editar</button>
                      <ActionButton className="btn small danger" confirm="¿Eliminar el registro?" action={() => api.del(`/Production/${r.id}`)} onDone={registros.reload}>Eliminar</ActionButton>
                    </div>
                  ),
              },
            ]}
          />
        </>
      )}
      {tab === 'diario' && (
        <>
          <div className="toolbar"><Field label="Fecha"><input type="date" value={fechaDia} onChange={(e) => setFechaDia(e.target.value)} /></Field></div>
          {diario.loading ? <Spinner /> : diario.data && (
            <>
              <div className="grid grid-4 mb">
                <Stat label="Toneladas del día" value={fmt.num(diario.data.totalToneladas)} />
                <Stat label="Meta del día" value={fmt.num(diario.data.metaDia)} />
                <Stat label="Cumplimiento" value={`${fmt.num(diario.data.cumplimiento)} %`} />
                <Stat label="Trabajadores asignados" value={diario.data.personal} />
              </div>
              <DataTable
                rows={diario.data.lineas}
                rowKey={(_, i) => i}
                columns={[
                  { key: 'frente', label: 'Frente' },
                  { key: 't', label: 'Turno', render: (l) => fmt.label(l.turno) },
                  { key: 'to', label: 'Toneladas', num: true, render: (l) => fmt.num(l.toneladas) },
                  { key: 'h', label: 'Horas', num: true, render: (l) => fmt.num(l.horas) },
                  { key: 'personal', label: 'Personal', num: true },
                  { key: 'o', label: 'Observaciones', render: (l) => l.observaciones },
                ]}
              />
            </>
          )}
        </>
      )}
      {tab === 'indicadores' && (
        <>
          <div className="toolbar">{rango}</div>
          {ind.loading ? <Spinner /> : ind.data && (
            <>
              <div className="grid grid-4 mb">
                <Stat label="Total extraído" value={`${fmt.num(ind.data.totalToneladas)} t`} />
                <Stat label="Promedio diario" value={`${fmt.num(ind.data.promedioDiario)} t`} />
                <Stat label="Toneladas por hora" value={fmt.num(ind.data.toneladasPorHora)} />
                <Stat label="Toneladas por trabajador-turno" value={fmt.num(ind.data.toneladasPorTrabajador)} />
              </div>
              <div className="grid grid-2">
                <div className="card"><h3>Producción diaria</h3><ProduccionDiaria data={ind.data.serieDiaria} /></div>
                <div className="card"><h3>Cumplimiento de meta por frente</h3><CumplimientoFrentes data={ind.data.porFrente} /></div>
              </div>
              <div className="grid grid-2 mt">
                <div className="card"><h3>Toneladas por turno</h3><PorTurno data={ind.data.porTurno} /></div>
                <div className="card">
                  <h3>Cumplimiento</h3>
                  <DataTable
                    rows={ind.data.porFrente}
                    rowKey={(c) => c.frente}
                    columns={[
                      { key: 'frente', label: 'Frente' },
                      { key: 't', label: 'Producido', num: true, render: (c) => fmt.num(c.toneladas) },
                      { key: 'm', label: 'Meta', num: true, render: (c) => fmt.num(c.meta) },
                      { key: 'p', label: '%', num: true, render: (c) => <Badge value={`${fmt.num(c.porcentaje)} %`} tone={c.porcentaje >= 100 ? 'ok' : c.porcentaje >= 85 ? 'warn' : 'danger'} /> },
                    ]}
                  />
                </div>
              </div>
            </>
          )}
        </>
      )}
      {tab === 'frentes' && (
        <>
          {escribe && (
            <div className="actions mb">
              <button className="btn" onClick={() => { ff.setValues({ nombre: '', ubicacion: '', mineral: 'Oro', metaDiariaToneladas: '50', activo: true }); setFrenteForm({}); }}>Nuevo frente</button>
            </div>
          )}
          <DataTable<Frente>
            rows={frentes.data}
            columns={[
              { key: 'nombre', label: 'Frente' },
              { key: 'ubicacion', label: 'Ubicación' },
              { key: 'mineral', label: 'Mineral' },
              { key: 'm', label: 'Meta diaria (t)', num: true, render: (x) => fmt.num(x.metaDiariaToneladas) },
              { key: 'a', label: 'Estado', render: (x) => <Badge value={x.activo ? 'ACTIVO' : 'INACTIVO'} /> },
              {
                key: 'e',
                label: '',
                render: (x) =>
                  escribe && (
                    <button className="btn small" onClick={() => { ff.setValues({ nombre: x.nombre, ubicacion: x.ubicacion ?? '', mineral: x.mineral, metaDiariaToneladas: String(x.metaDiariaToneladas), activo: x.activo }); setFrenteForm({ frente: x }); }}>
                      Editar
                    </button>
                  ),
              },
            ]}
          />
        </>
      )}
      {form && frentes.data && (
        <RegistroForm
          registro={form.registro}
          frentes={frentes.data}
          onClose={() => setForm(null)}
          onSaved={() => { setForm(null); toast.ok('Producción guardada'); registros.reload(); ind.reload(); diario.reload(); }}
        />
      )}
      {frenteForm && (
        <FormModal
          title={frenteForm.frente ? 'Editar frente' : 'Nuevo frente de trabajo'}
          onClose={() => setFrenteForm(null)}
          onSubmit={async () => {
            const body = { ...ff.values, metaDiariaToneladas: Number(ff.values.metaDiariaToneladas) };
            if (frenteForm.frente) await api.put(`/Fronts/${frenteForm.frente.id}`, body);
            else await api.post('/Fronts', body);
            setFrenteForm(null);
            frentes.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Nombre *" error={err.nombre}><input required {...ff.bind('nombre')} /></Field>
              <Field label="Mineral *"><input required {...ff.bind('mineral')} /></Field>
              <Field label="Ubicación"><input {...ff.bind('ubicacion')} /></Field>
              <Field label="Meta diaria (toneladas) *"><input type="number" min={0} required {...ff.bind('metaDiariaToneladas')} /></Field>
              <label className="check"><input type="checkbox" {...ff.check('activo')} /> Activo</label>
            </div>
          )}
        </FormModal>
      )}
    </>
  );
}
