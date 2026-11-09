import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, Field, FormModal, PageHeader, Stat, Tabs, useForm, useLoad, useToast } from '../components/ui';
import { daysAgo, fmt, nowLocal, today } from '../format';
import type { EmpleadoOpcion, ResumenSst } from '../types';

interface Evento {
  id: number;
  tipo: string;
  empleadoId: number | null;
  empleado: string | null;
  fecha: string;
  lugar: string;
  descripcion: string;
  gravedad: string;
  diasIncapacidad: number;
  causas: string | null;
  acciones: string | null;
  estado: string;
}
interface Epp {
  id: number;
  empleado: string;
  cedula: string;
  elemento: string;
  cantidad: number;
  fecha: string;
  observaciones: string | null;
  entregadoPor: string;
}
interface Capacitacion {
  id: number;
  tema: string;
  descripcion: string | null;
  fecha: string;
  duracionHoras: number;
  instructor: string;
  lugar: string | null;
  estado: string;
  convocados: number;
  asistentes: number;
  participantes: { empleadoId: number; empleado: string; cedula: string; asistio: boolean }[];
}
interface Riesgo {
  id: number;
  proceso: string;
  actividad: string;
  peligro: string;
  clasificacion: string;
  efectos: string | null;
  probabilidad: number;
  severidad: number;
  nivel: number;
  interpretacion: string;
  controles: string | null;
  responsable: string | null;
}
interface ProductoEpp {
  id: number;
  nombre: string;
  cantidad: number;
  unidad: string;
}

const CLASIF = ['FISICO', 'QUIMICO', 'BIOLOGICO', 'BIOMECANICO', 'PSICOSOCIAL', 'MECANICO', 'ELECTRICO', 'LOCATIVO', 'PUBLICO', 'NATURAL'];
type Tab = 'eventos' | 'epp' | 'capacitaciones' | 'riesgos';

export default function Sgsst() {
  const { can } = useAuth();
  const toast = useToast();
  const sst = can('SGSST');
  const reporta = can('SGSST', 'SUPERVISOR');
  const [tab, setTab] = useState<Tab>('eventos');
  const [desde, setDesde] = useState(daysAgo(180));
  const [hasta, setHasta] = useState(today());
  const resumen = useLoad(() => api.get<ResumenSst>('/Sst/Summary'));
  const eventos = useLoad(() => api.get<Evento[]>('/Accidents', { desde, hasta }), [desde, hasta]);
  const epp = useLoad(() => api.get<Epp[]>('/Epp', { desde, hasta }), [desde, hasta]);
  const caps = useLoad(() => api.get<Capacitacion[]>('/Training'));
  const riesgos = useLoad(() => api.get<Riesgo[]>('/Risks'));
  const empleados = useLoad(() => (reporta ? api.get<EmpleadoOpcion[]>('/Employees/Options') : Promise.resolve([])));
  const productosEpp = useLoad(() => (sst ? api.get<ProductoEpp[]>('/Inventory', { tipo: 'EPP', activos: true }) : Promise.resolve([])));

  const [modal, setModal] = useState<'evento' | 'epp' | 'cap' | 'riesgo' | null>(null);
  const [seguir, setSeguir] = useState<Evento | null>(null);
  const [editRiesgo, setEditRiesgo] = useState<Riesgo | null>(null);
  const [asistencia, setAsistencia] = useState<Capacitacion | null>(null);
  const [marcas, setMarcas] = useState<Record<number, boolean>>({});
  const ef = useForm({ tipo: 'ACCIDENTE', empleadoId: '', fecha: nowLocal(), lugar: '', descripcion: '', gravedad: 'LEVE', diasIncapacidad: '0', causas: '', acciones: '' });
  const sf = useForm({ causas: '', acciones: '', diasIncapacidad: '0', estado: 'ABIERTO' });
  const pf = useForm({ empleadoId: '', productoId: '', elemento: '', cantidad: '1', fecha: today(), observaciones: '' });
  const cf = useForm({ tema: '', descripcion: '', fecha: nowLocal(), duracionHoras: '2', instructor: '', lugar: '' });
  const [convocados, setConvocados] = useState<number[]>([]);
  const rf = useForm({ proceso: '', actividad: '', peligro: '', clasificacion: 'FISICO', efectos: '', probabilidad: '3', severidad: '3', controles: '', responsable: '' });
  const r = resumen.data;

  const rango = (
    <div className="toolbar">
      <Field label="Desde"><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></Field>
      <Field label="Hasta"><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></Field>
    </div>
  );

  return (
    <>
      <PageHeader title="SG-SST" subtitle="Seguridad y salud en el trabajo: accidentes, incidentes, EPP, capacitaciones y matriz de riesgos">
        <button className="btn" onClick={() => api.download('/Reports/sgsst/pdf', { desde, hasta }).catch((e) => toast.error(e.message))}>PDF</button>
        <button className="btn" onClick={() => api.download('/Reports/sgsst/excel', { desde, hasta }).catch((e) => toast.error(e.message))}>Excel</button>
        {reporta && tab === 'eventos' && <button className="btn primary" onClick={() => { ef.reset(); setModal('evento'); }}>Reportar accidente / incidente</button>}
        {sst && tab === 'epp' && <button className="btn primary" onClick={() => { pf.reset(); setModal('epp'); }}>Registrar entrega de EPP</button>}
        {sst && tab === 'capacitaciones' && <button className="btn primary" onClick={() => { cf.reset(); setConvocados([]); setModal('cap'); }}>Programar capacitación</button>}
        {sst && tab === 'riesgos' && <button className="btn primary" onClick={() => { rf.reset(); setEditRiesgo(null); setModal('riesgo'); }}>Agregar riesgo</button>}
      </PageHeader>
      <div className="grid grid-4 mb">
        <Stat label="Días sin accidentes" value={r ? (r.diasSinAccidentes < 0 ? '—' : r.diasSinAccidentes) : '—'} />
        <Stat label="Accidentes / incidentes del mes" value={r ? `${r.accidentesMes} / ${r.incidentesMes}` : '—'} />
        <Stat label="Días de incapacidad del mes" value={r?.diasIncapacidadMes ?? '—'} />
        <Stat label="Riesgos altos o críticos" value={r?.riesgosAltos ?? '—'} hint={r ? `${r.eventosAbiertos} casos abiertos` : undefined} />
      </div>
      <Tabs<Tab>
        tabs={[{ key: 'eventos', label: 'Accidentes e incidentes' }, { key: 'epp', label: 'Entregas de EPP' }, { key: 'capacitaciones', label: 'Capacitaciones' }, { key: 'riesgos', label: 'Matriz de riesgos' }]}
        active={tab}
        onChange={setTab}
      />
      {tab === 'eventos' && (
        <>
          {rango}
          <DataTable<Evento>
            rows={eventos.data}
            columns={[
              { key: 'f', label: 'Fecha', render: (e) => fmt.dateTime(e.fecha) },
              { key: 't', label: 'Tipo', render: (e) => <Badge value={e.tipo} /> },
              { key: 'e', label: 'Trabajador', render: (e) => e.empleado ?? '—' },
              { key: 'l', label: 'Lugar', render: (e) => e.lugar },
              { key: 'd', label: 'Descripción', render: (e) => e.descripcion },
              { key: 'g', label: 'Gravedad', render: (e) => <Badge value={e.gravedad} /> },
              { key: 'i', label: 'Incap.', num: true, render: (e) => e.diasIncapacidad },
              { key: 's', label: 'Estado', render: (e) => <Badge value={e.estado} /> },
              { key: 'a', label: '', render: (e) => sst && <button className="btn small" onClick={() => { sf.setValues({ causas: e.causas ?? '', acciones: e.acciones ?? '', diasIncapacidad: String(e.diasIncapacidad), estado: e.estado }); setSeguir(e); }}>Seguimiento</button> },
            ]}
          />
        </>
      )}
      {tab === 'epp' && (
        <>
          {rango}
          <DataTable<Epp>
            rows={epp.data}
            columns={[
              { key: 'f', label: 'Fecha', render: (e) => fmt.date(e.fecha) },
              { key: 'e', label: 'Trabajador', render: (e) => <>{e.empleado}<div className="muted">{e.cedula}</div></> },
              { key: 'el', label: 'Elemento', render: (e) => e.elemento },
              { key: 'c', label: 'Cantidad', num: true, render: (e) => fmt.num(e.cantidad) },
              { key: 'o', label: 'Observaciones', render: (e) => e.observaciones },
              { key: 'p', label: 'Entregado por', render: (e) => e.entregadoPor },
            ]}
          />
        </>
      )}
      {tab === 'capacitaciones' && (
        <DataTable<Capacitacion>
          rows={caps.data}
          columns={[
            { key: 'f', label: 'Fecha', render: (c) => fmt.dateTime(c.fecha) },
            { key: 't', label: 'Tema', render: (c) => <><strong>{c.tema}</strong><div className="muted">{c.descripcion}</div></> },
            { key: 'i', label: 'Instructor', render: (c) => c.instructor },
            { key: 'h', label: 'Horas', num: true, render: (c) => fmt.num(c.duracionHoras) },
            { key: 'a', label: 'Asistencia', num: true, render: (c) => `${c.asistentes} / ${c.convocados}` },
            { key: 's', label: 'Estado', render: (c) => <Badge value={c.estado} /> },
            {
              key: 'ac',
              label: '',
              render: (c) =>
                sst && c.estado !== 'CANCELADA' && (
                  <div className="actions">
                    <button className="btn small" onClick={() => { setMarcas(Object.fromEntries(c.participantes.map((p) => [p.empleadoId, c.estado === 'REALIZADA' ? p.asistio : true]))); setAsistencia(c); }}>Asistencia</button>
                    {c.estado === 'PROGRAMADA' && <ActionButton className="btn small danger" confirm="¿Cancelar la capacitación?" action={() => api.put(`/Training/${c.id}/Status`, { estado: 'CANCELADA' })} onDone={caps.reload}>Cancelar</ActionButton>}
                  </div>
                ),
            },
          ]}
        />
      )}
      {tab === 'riesgos' && (
        <DataTable<Riesgo>
          rows={riesgos.data}
          columns={[
            { key: 'p', label: 'Proceso / actividad', render: (x) => <><strong>{x.proceso}</strong><div className="muted">{x.actividad}</div></> },
            { key: 'pe', label: 'Peligro', render: (x) => <>{x.peligro}<div className="muted">{fmt.label(x.clasificacion)}</div></> },
            { key: 'ef', label: 'Efectos', render: (x) => x.efectos },
            { key: 'pr', label: 'P', num: true, render: (x) => x.probabilidad },
            { key: 'se', label: 'S', num: true, render: (x) => x.severidad },
            { key: 'n', label: 'Nivel', render: (x) => <Badge value={x.interpretacion} /> },
            { key: 'c', label: 'Controles', render: (x) => x.controles },
            {
              key: 'a',
              label: '',
              render: (x) =>
                sst && (
                  <div className="actions">
                    <button className="btn small" onClick={() => { rf.setValues({ proceso: x.proceso, actividad: x.actividad, peligro: x.peligro, clasificacion: x.clasificacion, efectos: x.efectos ?? '', probabilidad: String(x.probabilidad), severidad: String(x.severidad), controles: x.controles ?? '', responsable: x.responsable ?? '' }); setEditRiesgo(x); setModal('riesgo'); }}>Editar</button>
                    <ActionButton className="btn small danger" confirm="¿Eliminar el riesgo de la matriz?" action={() => api.del(`/Risks/${x.id}`)} onDone={riesgos.reload}>Eliminar</ActionButton>
                  </div>
                ),
            },
          ]}
        />
      )}

      {modal === 'evento' && (
        <FormModal
          title="Reportar accidente o incidente"
          wide
          onClose={() => setModal(null)}
          onSubmit={async () => {
            const v = ef.values;
            await api.post('/Accident', { ...v, empleadoId: v.empleadoId ? Number(v.empleadoId) : null, diasIncapacidad: Number(v.diasIncapacidad), fecha: v.fecha + ':00' });
            toast.ok('Evento registrado');
            setModal(null);
            eventos.reload();
            resumen.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Tipo"><select {...ef.bind('tipo')}><option value="ACCIDENTE">Accidente de trabajo</option><option value="INCIDENTE">Incidente (sin lesión)</option></select></Field>
              <Field label={ef.values.tipo === 'ACCIDENTE' ? 'Trabajador afectado *' : 'Trabajador involucrado'}>
                <select required={ef.values.tipo === 'ACCIDENTE'} {...ef.bind('empleadoId')}>
                  <option value="">—</option>
                  {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cargo}</option>)}
                </select>
              </Field>
              <Field label="Fecha y hora *" error={err.fecha}><input type="datetime-local" required {...ef.bind('fecha')} /></Field>
              <Field label="Lugar *" error={err.lugar}><input required {...ef.bind('lugar')} /></Field>
              <Field label="Gravedad"><select {...ef.bind('gravedad')}>{['LEVE', 'MODERADO', 'GRAVE', 'MORTAL'].map((g) => <option key={g} value={g}>{fmt.label(g)}</option>)}</select></Field>
              <Field label="Días de incapacidad"><input type="number" min={0} {...ef.bind('diasIncapacidad')} /></Field>
              <Field label="Descripción *" error={err.descripcion} full><textarea required {...ef.bind('descripcion')} /></Field>
              <Field label="Causas"><textarea {...ef.bind('causas')} /></Field>
              <Field label="Acciones inmediatas"><textarea {...ef.bind('acciones')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {seguir && (
        <FormModal
          title={`Seguimiento del ${seguir.tipo.toLowerCase()} del ${fmt.date(seguir.fecha)}`}
          onClose={() => setSeguir(null)}
          onSubmit={async () => {
            await api.put(`/Accident/${seguir.id}`, { ...sf.values, diasIncapacidad: Number(sf.values.diasIncapacidad) });
            toast.ok('Seguimiento guardado');
            setSeguir(null);
            eventos.reload();
            resumen.reload();
          }}
        >
          {() => (
            <div className="form-grid">
              <Field label="Causas (investigación)" full><textarea {...sf.bind('causas')} /></Field>
              <Field label="Acciones correctivas" full><textarea {...sf.bind('acciones')} /></Field>
              <Field label="Días de incapacidad"><input type="number" min={0} {...sf.bind('diasIncapacidad')} /></Field>
              <Field label="Estado"><select {...sf.bind('estado')}><option value="ABIERTO">Abierto</option><option value="CERRADO">Cerrado</option></select></Field>
            </div>
          )}
        </FormModal>
      )}
      {modal === 'epp' && (
        <FormModal
          title="Registrar entrega de EPP"
          onClose={() => setModal(null)}
          onSubmit={async () => {
            const v = pf.values;
            await api.post('/Epp', { empleadoId: Number(v.empleadoId), productoId: v.productoId ? Number(v.productoId) : null, elemento: v.elemento, cantidad: Number(v.cantidad), fecha: v.fecha, observaciones: v.observaciones });
            toast.ok('Entrega registrada');
            setModal(null);
            epp.reload();
            productosEpp.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Trabajador *" full>
                <select required {...pf.bind('empleadoId')}>
                  <option value="">Seleccione...</option>
                  {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cargo}</option>)}
                </select>
              </Field>
              <Field label="Elemento del almacén (descuenta inventario)" full>
                <select {...pf.bind('productoId')}>
                  <option value="">— Otro elemento (no está en inventario) —</option>
                  {productosEpp.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre} (disponible {fmt.num(p.cantidad)} {p.unidad})</option>)}
                </select>
              </Field>
              {!pf.values.productoId && <Field label="Elemento entregado *" full><input required {...pf.bind('elemento')} /></Field>}
              <Field label="Cantidad *" error={err.cantidad}><input type="number" min={1} required {...pf.bind('cantidad')} /></Field>
              <Field label="Fecha *"><input type="date" max={today()} required {...pf.bind('fecha')} /></Field>
              <Field label="Observaciones" full><input {...pf.bind('observaciones')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {modal === 'cap' && (
        <FormModal
          title="Programar capacitación"
          wide
          onClose={() => setModal(null)}
          onSubmit={async () => {
            await api.post('/Training', { ...cf.values, fecha: cf.values.fecha + ':00', duracionHoras: Number(cf.values.duracionHoras), empleadoIds: convocados });
            toast.ok('Capacitación programada');
            setModal(null);
            caps.reload();
          }}
        >
          {(err) => (
            <>
              <div className="form-grid">
                <Field label="Tema *" error={err.tema} full><input required {...cf.bind('tema')} /></Field>
                <Field label="Descripción" full><input {...cf.bind('descripcion')} /></Field>
                <Field label="Fecha y hora *"><input type="datetime-local" required {...cf.bind('fecha')} /></Field>
                <Field label="Duración (horas) *"><input type="number" min={0.5} step="0.5" required {...cf.bind('duracionHoras')} /></Field>
                <Field label="Instructor *" error={err.instructor}><input required {...cf.bind('instructor')} /></Field>
                <Field label="Lugar"><input {...cf.bind('lugar')} /></Field>
              </div>
              <h3 className="mt" style={{ fontSize: 14 }}>Convocados ({convocados.length})</h3>
              <div style={{ maxHeight: 200, overflowY: 'auto', border: '1px solid var(--border)', borderRadius: 8, padding: 8 }}>
                {empleados.data?.map((e) => (
                  <label key={e.id} className="check">
                    <input type="checkbox" checked={convocados.includes(e.id)} onChange={(ev) => setConvocados(ev.target.checked ? [...convocados, e.id] : convocados.filter((x) => x !== e.id))} />
                    {e.nombre} <span className="muted">· {e.cargo}</span>
                  </label>
                ))}
              </div>
            </>
          )}
        </FormModal>
      )}
      {asistencia && (
        <FormModal
          title={`Asistencia · ${asistencia.tema}`}
          submitLabel="Guardar asistencia"
          onClose={() => setAsistencia(null)}
          onSubmit={async () => {
            await api.put(`/Training/${asistencia.id}/Attendance`, { asistentes: Object.entries(marcas).map(([id, asistio]) => ({ empleadoId: Number(id), asistio })) });
            toast.ok('Asistencia registrada; la capacitación queda como realizada');
            setAsistencia(null);
            caps.reload();
          }}
        >
          {() => (
            <>
              {asistencia.participantes.length === 0 && <p className="muted">No hay convocados.</p>}
              {asistencia.participantes.map((p) => (
                <label key={p.empleadoId} className="check" style={{ padding: '4px 0' }}>
                  <input type="checkbox" checked={!!marcas[p.empleadoId]} onChange={(e) => setMarcas({ ...marcas, [p.empleadoId]: e.target.checked })} />
                  {p.empleado} <span className="muted">· {p.cedula}</span>
                </label>
              ))}
            </>
          )}
        </FormModal>
      )}
      {modal === 'riesgo' && (
        <FormModal
          title={editRiesgo ? 'Editar riesgo' : 'Agregar riesgo a la matriz'}
          wide
          onClose={() => setModal(null)}
          onSubmit={async () => {
            const body = { ...rf.values, probabilidad: Number(rf.values.probabilidad), severidad: Number(rf.values.severidad) };
            if (editRiesgo) await api.put(`/Risks/${editRiesgo.id}`, body);
            else await api.post('/Risks', body);
            toast.ok('Matriz actualizada');
            setModal(null);
            riesgos.reload();
            resumen.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Proceso *" error={err.proceso}><input required {...rf.bind('proceso')} /></Field>
              <Field label="Actividad *" error={err.actividad}><input required {...rf.bind('actividad')} /></Field>
              <Field label="Peligro *" error={err.peligro}><input required {...rf.bind('peligro')} /></Field>
              <Field label="Clasificación"><select {...rf.bind('clasificacion')}>{CLASIF.map((c) => <option key={c} value={c}>{fmt.label(c)}</option>)}</select></Field>
              <Field label="Efectos posibles" full><input {...rf.bind('efectos')} /></Field>
              <Field label="Probabilidad (1 muy baja - 5 muy alta)"><select {...rf.bind('probabilidad')}>{[1, 2, 3, 4, 5].map((n) => <option key={n}>{n}</option>)}</select></Field>
              <Field label="Severidad (1 leve - 5 catastrófica)"><select {...rf.bind('severidad')}>{[1, 2, 3, 4, 5].map((n) => <option key={n}>{n}</option>)}</select></Field>
              <p className="muted full" style={{ margin: 0 }}>Nivel = {Number(rf.values.probabilidad) * Number(rf.values.severidad)} (1-4 bajo, 5-9 medio, 10-16 alto, 17-25 crítico)</p>
              <Field label="Controles" full><textarea {...rf.bind('controles')} /></Field>
              <Field label="Responsable"><input {...rf.bind('responsable')} /></Field>
            </div>
          )}
        </FormModal>
      )}
    </>
  );
}
