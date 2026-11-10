import { CalendarPlus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, ErrorBox, Field, FormModal, PageHeader, Spinner, Stat, Tabs, useForm, useLoad, useToast } from '../components/ui';
import { fmt } from '../format';
import type { EmpleadoOpcion } from '../types';

interface Vacacion {
  id: number;
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  fechaInicio: string;
  fechaFin: string;
  fechaRegreso: string;
  diasHabiles: number;
  observaciones: string | null;
  estado: string;
  motivoRechazo: string | null;
  solicitadoPor: string;
  revisadoPor: string | null;
  aviso: string | null;
}
interface Saldo {
  empleadoId: number;
  empleado: string;
  fechaIngreso: string;
  diasLaborados: number;
  diasCausados: number;
  diasDisfrutados: number;
  diasSolicitados: number;
  diasDisponibles: number;
  aplica: boolean;
}
interface Simulacion {
  fechaInicio: string;
  fechaFin: string;
  fechaRegreso: string;
  diasHabiles: number;
  diasCalendario: number;
}
interface PagoPrestacion {
  id: number;
  tipo: string;
  anio: number;
  periodo: number;
  dias: number;
  valor: number;
  fechaDesde: string;
  fechaHasta: string;
}

type Filtro = 'PENDIENTE' | 'TODAS';
const dec = (n: number) => fmt.num(Math.floor(n * 100) / 100);

function enUnaSemana() {
  const d = new Date();
  d.setDate(d.getDate() + 7);
  return d.toISOString().substring(0, 10);
}

/** Vacaciones: saldo, solicitud con fechas calculadas (festivos de Colombia) y aprobación por Talento Humano. */
export default function Vacaciones() {
  const { user, can } = useAuth();
  const toast = useToast();
  const gestiona = can('RRHH');
  const veTodas = can('RRHH', 'GERENTE', 'CONTADOR', 'SUPERVISOR');
  const [filtro, setFiltro] = useState<Filtro>(gestiona ? 'PENDIENTE' : 'TODAS');
  const [nueva, setNueva] = useState(false);
  const [rechazar, setRechazar] = useState<Vacacion | null>(null);
  const [motivo, setMotivo] = useState('');
  const [sim, setSim] = useState<Simulacion | null>(null);
  const [simError, setSimError] = useState<string | null>(null);

  const lista = useLoad(() => api.get<Vacacion[]>('/Vacations', { estado: filtro === 'PENDIENTE' ? 'PENDIENTE' : undefined }), [filtro]);
  const miSaldo = useLoad(() => (user?.empleadoId ? api.get<Saldo>(`/Vacations/Balance/${user.empleadoId}`) : Promise.resolve(null)), [nueva]);
  const misPrestaciones = useLoad(() => (user?.empleadoId ? api.get<PagoPrestacion[]>(`/Benefits/Employee/${user.empleadoId}`) : Promise.resolve([])));
  const empleados = useLoad(() => (gestiona ? api.get<EmpleadoOpcion[]>('/Employees/Options') : Promise.resolve([])));
  const form = useForm({ empleadoId: '', fechaInicio: enUnaSemana(), diasHabiles: '15', observaciones: '' });
  const saldoOtro = useLoad(
    () => (gestiona && form.values.empleadoId ? api.get<Saldo>(`/Vacations/Balance/${form.values.empleadoId}`) : Promise.resolve(null)),
    [form.values.empleadoId],
  );

  // Vista previa de las fechas (dias habiles, domingos y festivos)
  useEffect(() => {
    if (!nueva) return;
    const dias = Number(form.values.diasHabiles);
    if (!form.values.fechaInicio || !dias) {
      setSim(null);
      return;
    }
    const t = setTimeout(() => {
      api
        .get<Simulacion>('/Vacations/Simulate', { fechaInicio: form.values.fechaInicio, diasHabiles: dias })
        .then((s) => {
          setSim(s);
          setSimError(null);
        })
        .catch((e) => {
          setSim(null);
          setSimError((e as Error).message);
        });
    }, 300);
    return () => clearTimeout(t);
  }, [nueva, form.values.fechaInicio, form.values.diasHabiles]);

  const s = miSaldo.data;
  const saldoForm = gestiona && form.values.empleadoId ? saldoOtro.data : s;
  const filas = lista.data ?? [];

  return (
    <>
      <PageHeader title="Vacaciones" subtitle="15 días hábiles por cada año trabajado (Código Sustantivo del Trabajo, art. 186)">
        <button
          className="btn primary"
          disabled={!gestiona && (!s || !s.aplica)}
          onClick={() => {
            form.reset();
            setSim(null);
            setNueva(true);
          }}
        >
          <CalendarPlus /> Solicitar vacaciones
        </button>
      </PageHeader>

      {s && (
        <div className="grid grid-4 mb">
          <Stat label="Días disponibles" value={dec(s.diasDisponibles)} />
          <Stat label="Días causados" value={dec(s.diasCausados)} />
          <Stat label="Días disfrutados" value={s.diasDisfrutados} />
          <Stat label="En solicitud" value={s.diasSolicitados} />
        </div>
      )}
      {s && !s.aplica && <div className="alert info mb">Su tipo de contrato no genera vacaciones.</div>}

      {veTodas && (
        <Tabs<Filtro> tabs={[{ key: 'PENDIENTE', label: 'Por aprobar' }, { key: 'TODAS', label: 'Todas' }]} active={filtro} onChange={setFiltro} />
      )}
      <ErrorBox error={lista.error} />
      {lista.loading && !lista.data ? (
        <Spinner />
      ) : (
        <DataTable<Vacacion>
          rows={filas}
          empty={filtro === 'PENDIENTE' ? 'No hay solicitudes por aprobar' : 'No hay vacaciones registradas'}
          search={(v) => `${v.empleado} ${v.cedula}`}
          searchPlaceholder="Buscar trabajador"
          columns={[
            ...(veTodas ? [{ key: 'e', label: 'Trabajador', render: (v: Vacacion) => <><strong>{v.empleado}</strong><div className="muted">{v.cedula} · {v.cargo}</div></> }] : []),
            { key: 'f', label: 'Período', render: (v) => <>{fmt.date(v.fechaInicio)} – {fmt.date(v.fechaFin)}<div className="muted">Regresa el {fmt.date(v.fechaRegreso)}</div></> },
            { key: 'd', label: 'Días hábiles', num: true, render: (v) => v.diasHabiles },
            {
              key: 's',
              label: 'Estado',
              render: (v) => (
                <>
                  <Badge value={v.estado} />
                  {v.motivoRechazo && <div className="muted clamp" title={v.motivoRechazo}>{v.motivoRechazo}</div>}
                </>
              ),
            },
            {
              key: 'a',
              label: '',
              render: (v) => (
                <div className="actions">
                  {gestiona && v.estado === 'PENDIENTE' && (
                    <>
                      <ActionButton
                        className="btn small primary"
                        action={async () => {
                          const r = await api.put<Vacacion>(`/Vacation/${v.id}/Review`, { aprobar: true });
                          if (r.aviso) toast.error(r.aviso);
                        }}
                        success="Vacaciones aprobadas"
                        onDone={lista.reload}
                      >
                        Aprobar
                      </ActionButton>
                      <button className="btn small danger" onClick={() => { setMotivo(''); setRechazar(v); }}>Rechazar</button>
                    </>
                  )}
                  {((v.estado === 'PENDIENTE' && v.empleadoId === user?.empleadoId) || (gestiona && v.estado === 'APROBADA')) && (
                    <ActionButton
                      action={async () => {
                        const r = await api.put<Vacacion>(`/Vacation/${v.id}/Cancel`, {});
                        if (r.aviso) toast.error(r.aviso);
                      }}
                      confirm="¿Cancelar estas vacaciones?"
                      success="Vacaciones canceladas"
                      onDone={() => { lista.reload(); miSaldo.reload(); }}
                    >
                      Cancelar
                    </ActionButton>
                  )}
                </div>
              ),
            },
          ]}
        />
      )}

      {user?.empleadoId && (misPrestaciones.data?.length ?? 0) > 0 && (
        <>
          <h3 className="mt">Mis prestaciones sociales</h3>
          <DataTable<PagoPrestacion>
            rows={misPrestaciones.data}
            columns={[
              { key: 't', label: 'Concepto', render: (p) => (p.tipo === 'PRIMA' ? `Prima ${p.periodo === 1 ? 'junio' : 'diciembre'}` : p.tipo === 'CESANTIAS' ? 'Cesantías' : 'Intereses de cesantías') },
              { key: 'a', label: 'Año', render: (p) => p.anio },
              { key: 'd', label: 'Días', num: true, render: (p) => p.dias },
              { key: 'v', label: 'Valor', num: true, render: (p) => <strong>{fmt.money(p.valor)}</strong> },
            ]}
          />
        </>
      )}

      {nueva && (
        <FormModal
          title="Solicitar vacaciones"
          submitLabel="Enviar solicitud"
          wide
          onClose={() => setNueva(false)}
          onSubmit={async () => {
            const v = form.values;
            await api.post('/Vacation', {
              empleadoId: gestiona && v.empleadoId ? Number(v.empleadoId) : null,
              fechaInicio: v.fechaInicio,
              diasHabiles: Number(v.diasHabiles),
              observaciones: v.observaciones,
            });
            toast.ok('Solicitud enviada a Talento Humano');
            setNueva(false);
            lista.reload();
            miSaldo.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              {gestiona && (
                <Field label="Trabajador (vacío = usted)" full>
                  <select {...form.bind('empleadoId')}>
                    <option value="">Seleccione...</option>
                    {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cedula}</option>)}
                  </select>
                </Field>
              )}
              {saldoForm && (
                <div className="alert info full">
                  Disponibles: <strong>{dec(saldoForm.diasDisponibles)} días hábiles</strong> (causados {dec(saldoForm.diasCausados)} desde {fmt.date(saldoForm.fechaIngreso)})
                </div>
              )}
              <Field label="Primer día de vacaciones *" error={err.fechaInicio}><input type="date" required {...form.bind('fechaInicio')} /></Field>
              <Field label="Días hábiles *" error={err.diasHabiles}><input type="number" min={1} max={60} required {...form.bind('diasHabiles')} /></Field>
              <div className="full">
                {sim && (
                  <div className="alert success">
                    Descansa del <strong>{fmt.date(sim.fechaInicio)}</strong> al <strong>{fmt.date(sim.fechaFin)}</strong> ({sim.diasCalendario} días calendario) y regresa el <strong>{fmt.date(sim.fechaRegreso)}</strong>.
                    <div className="muted">Los domingos y festivos no se descuentan.</div>
                  </div>
                )}
                {simError && <div className="alert warn">{simError}</div>}
              </div>
              <Field label="Observaciones" full><textarea rows={2} maxLength={500} {...form.bind('observaciones')} /></Field>
            </div>
          )}
        </FormModal>
      )}

      {rechazar && (
        <FormModal
          title={`Rechazar vacaciones de ${rechazar.empleado}`}
          submitLabel="Rechazar"
          onClose={() => setRechazar(null)}
          onSubmit={async () => {
            await api.put(`/Vacation/${rechazar.id}/Review`, { aprobar: false, motivo });
            toast.ok('Solicitud rechazada');
            setRechazar(null);
            lista.reload();
          }}
        >
          {() => (
            <Field label="Motivo *" full>
              <textarea required rows={3} maxLength={500} value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej.: temporada de alta producción; proponga otra fecha" />
            </Field>
          )}
        </FormModal>
      )}
    </>
  );
}
