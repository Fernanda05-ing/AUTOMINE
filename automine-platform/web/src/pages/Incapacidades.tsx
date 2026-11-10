import { FileText, Plus, Upload } from 'lucide-react';
import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, ErrorBox, Field, FormModal, PageHeader, Spinner, Stat, Tabs, useForm, useLoad, useToast } from '../components/ui';
import WhatsApp from '../components/WhatsApp';
import { fmt, today } from '../format';
import type { EmpleadoOpcion } from '../types';

interface Incapacidad {
  id: number;
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  telefono: string | null;
  tipo: string;
  fechaInicio: string;
  fechaFin: string;
  dias: number;
  prorroga: boolean;
  entidad: string | null;
  numeroCertificado: string | null;
  observaciones: string | null;
  estado: string;
  motivoRechazo: string | null;
  soporteNombre: string | null;
  registradoPor: string;
  revisadoPor: string | null;
  fechaRevision: string | null;
  createdAt: string;
  aviso: string | null;
}

const TIPOS: { value: string; label: string; quien: string }[] = [
  { value: 'ENFERMEDAD_GENERAL', label: 'Enfermedad general', quien: 'EPS' },
  { value: 'ACCIDENTE_TRABAJO', label: 'Accidente de trabajo', quien: 'ARL' },
  { value: 'ENFERMEDAD_LABORAL', label: 'Enfermedad laboral', quien: 'ARL' },
  { value: 'LICENCIA_MATERNIDAD', label: 'Licencia de maternidad', quien: 'EPS' },
  { value: 'LICENCIA_PATERNIDAD', label: 'Licencia de paternidad', quien: 'EPS' },
];
const nombreTipo = (t: string) => TIPOS.find((x) => x.value === t)?.label ?? fmt.label(t);

const ACEPTA = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';

function diasEntre(a: string, b: string) {
  if (!a || !b) return 0;
  const d = (new Date(b + 'T00:00:00').getTime() - new Date(a + 'T00:00:00').getTime()) / 86400000 + 1;
  return d > 0 ? Math.round(d) : 0;
}

type Filtro = 'PENDIENTE' | 'TODAS';

/**
 * Incapacidades: el trabajador registra la suya con el certificado de la EPS/ARL;
 * SG-SST y Talento Humano la revisan. Las aprobadas se reflejan en la nómina del mes.
 */
export default function Incapacidades() {
  const { user, can } = useAuth();
  const toast = useToast();
  const revisa = can('SGSST', 'RRHH');
  const registraParaOtros = can('SGSST', 'RRHH', 'SUPERVISOR');
  const veTodas = can('SGSST', 'RRHH', 'SUPERVISOR', 'GERENTE', 'CONTADOR');
  const [filtro, setFiltro] = useState<Filtro>(revisa ? 'PENDIENTE' : 'TODAS');
  const [nueva, setNueva] = useState(false);
  const [rechazar, setRechazar] = useState<Incapacidad | null>(null);
  const [anular, setAnular] = useState<Incapacidad | null>(null);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [motivo, setMotivo] = useState('');

  const lista = useLoad(() => api.get<Incapacidad[]>('/Disabilities', { estado: filtro === 'PENDIENTE' ? 'PENDIENTE' : undefined }), [filtro]);
  const empleados = useLoad(() => (registraParaOtros ? api.get<EmpleadoOpcion[]>('/Employees/Options') : Promise.resolve([])));
  const form = useForm({ empleadoId: '', tipo: 'ENFERMEDAD_GENERAL', fechaInicio: today(), fechaFin: today(), prorroga: false, entidad: '', numeroCertificado: '', observaciones: '' });

  const propia = (i: Incapacidad) => user?.empleadoId === i.empleadoId;
  const puedeVerSoporte = (i: Incapacidad) => !!i.soporteNombre && (revisa || propia(i));
  const puedeCambiarSoporte = (i: Incapacidad) => (i.estado === 'PENDIENTE' || i.estado === 'RECHAZADA') && (propia(i) || registraParaOtros);

  const cambiarSoporte = async (i: Incapacidad, f: File) => {
    const fd = new FormData();
    fd.append('soporte', f);
    try {
      const r = await api.form<Incapacidad>('PUT', `/Disability/${i.id}/Support`, fd);
      toast.ok(r.estado === 'PENDIENTE' && i.estado === 'RECHAZADA' ? 'Soporte enviado: la incapacidad vuelve a revisión' : 'Soporte actualizado');
      lista.reload();
    } catch (err) {
      toast.error((err as Error).message);
    }
  };

  const filas = lista.data ?? [];
  const pendientes = filas.filter((i) => i.estado === 'PENDIENTE').length;
  const tipoActual = TIPOS.find((t) => t.value === form.values.tipo);
  const dias = diasEntre(form.values.fechaInicio, form.values.fechaFin);

  return (
    <>
      <PageHeader
        title="Incapacidades"
        subtitle={veTodas ? 'Certificados médicos y licencias de los trabajadores' : 'Registre aquí sus incapacidades con el certificado de la EPS o ARL'}
      >
        <button
          className="btn primary"
          onClick={() => {
            form.reset();
            setArchivo(null);
            setNueva(true);
          }}
          disabled={!registraParaOtros && !user?.empleadoId}
        >
          <Plus /> Registrar incapacidad
        </button>
      </PageHeader>

      {!veTodas && (
        <div className="alert info mb">
          La incapacidad la expide el médico de su EPS (enfermedad general, licencias) o de la ARL (accidente o enfermedad laboral).
          Regístrela y adjunte el certificado en PDF o una foto legible; SG-SST o Talento Humano la revisarán y se tendrá en cuenta en su nómina.
        </div>
      )}

      {revisa && (
        <div className="grid grid-4 mb">
          <Stat label="Por revisar" value={filtro === 'PENDIENTE' ? filas.length : pendientes} />
          <Stat label="Días en la vista actual" value={filas.filter((i) => i.estado !== 'ANULADA' && i.estado !== 'RECHAZADA').reduce((s, i) => s + i.dias, 0)} />
        </div>
      )}

      {veTodas && (
        <Tabs<Filtro>
          tabs={[
            { key: 'PENDIENTE', label: 'Por revisar' },
            { key: 'TODAS', label: 'Todas' },
          ]}
          active={filtro}
          onChange={setFiltro}
        />
      )}

      <ErrorBox error={lista.error} />
      {lista.loading && !lista.data ? (
        <Spinner />
      ) : (
        <DataTable<Incapacidad>
          rows={filas}
          empty={filtro === 'PENDIENTE' ? 'No hay incapacidades por revisar' : 'No hay incapacidades registradas'}
          search={(i) => `${i.empleado} ${i.cedula} ${nombreTipo(i.tipo)} ${i.entidad ?? ''} ${i.numeroCertificado ?? ''}`}
          searchPlaceholder="Buscar por trabajador, cédula, tipo o EPS"
          columns={[
            ...(veTodas
              ? [{ key: 'e', label: 'Trabajador', render: (i: Incapacidad) => <><strong>{i.empleado}</strong><div className="muted">{i.cedula} · {i.cargo}</div></> }]
              : []),
            { key: 't', label: 'Tipo', render: (i) => <>{nombreTipo(i.tipo)}{i.prorroga && <div className="muted">Prórroga</div>}</> },
            { key: 'f', label: 'Fechas', render: (i) => <>{fmt.date(i.fechaInicio)} – {fmt.date(i.fechaFin)}<div className="muted">{i.dias} {i.dias === 1 ? 'día' : 'días'}</div></> },
            { key: 'en', label: 'EPS / ARL', render: (i) => <>{i.entidad ?? '—'}{i.numeroCertificado && <div className="muted">N.º {i.numeroCertificado}</div>}</> },
            {
              key: 's',
              label: 'Estado',
              render: (i) => (
                <>
                  <Badge value={i.estado} />
                  {i.motivoRechazo && <div className="muted clamp" title={i.motivoRechazo}>{i.motivoRechazo}</div>}
                  {i.revisadoPor && <div className="muted">por {i.revisadoPor}</div>}
                </>
              ),
            },
            {
              key: 'a',
              label: '',
              render: (i) => (
                <div className="actions">
                  {puedeVerSoporte(i) && (
                    <button className="btn small" onClick={() => api.open(`/Disability/${i.id}/Support`).catch((e) => toast.error(e.message))} title={i.soporteNombre ?? ''}>
                      <FileText /> Certificado
                    </button>
                  )}
                  {puedeCambiarSoporte(i) && (
                    <label className="btn small">
                      <Upload /> {i.estado === 'RECHAZADA' ? 'Corregir soporte' : 'Cambiar soporte'}
                      <input
                        type="file"
                        hidden
                        accept={ACEPTA}
                        onChange={(e) => {
                          const f = e.target.files?.[0];
                          e.target.value = '';
                          if (f) void cambiarSoporte(i, f);
                        }}
                      />
                    </label>
                  )}
                  {revisa && i.estado === 'PENDIENTE' && (
                    <>
                      <ActionButton
                        className="btn small primary"
                        action={async () => {
                          const r = await api.put<Incapacidad>(`/Disability/${i.id}/Review`, { aprobar: true });
                          if (r.aviso) toast.error(r.aviso);
                        }}
                        confirm={`¿Aprobar la incapacidad de ${i.empleado} (${i.dias} días)? Se reflejará en su nómina.`}
                        success="Incapacidad aprobada"
                        onDone={lista.reload}
                      >
                        Aprobar
                      </ActionButton>
                      <button className="btn small danger" onClick={() => { setMotivo(''); setRechazar(i); }}>Rechazar</button>
                    </>
                  )}
                  {revisa && i.estado === 'APROBADA' && (
                    <button className="btn small" onClick={() => { setMotivo(''); setAnular(i); }}>Anular</button>
                  )}
                  {veTodas && !propia(i) && i.telefono && i.estado === 'RECHAZADA' && (
                    <WhatsApp telefono={i.telefono} mensaje={`Hola ${i.empleado.split(' ')[0]}, su incapacidad fue rechazada: ${i.motivoRechazo}. Por favor suba de nuevo el certificado en AUTOMINE.`} soloIcono />
                  )}
                </div>
              ),
            },
          ]}
        />
      )}

      {nueva && (
        <FormModal
          title="Registrar incapacidad"
          submitLabel="Enviar a revisión"
          wide
          onClose={() => setNueva(false)}
          onSubmit={async () => {
            if (!archivo) throw new Error('Adjunte el certificado de incapacidad (PDF o foto)');
            const v = form.values;
            const fd = new FormData();
            if (registraParaOtros && v.empleadoId) fd.append('empleadoId', v.empleadoId);
            fd.append('tipo', v.tipo);
            fd.append('fechaInicio', v.fechaInicio);
            fd.append('fechaFin', v.fechaFin);
            fd.append('prorroga', String(Boolean(v.prorroga)));
            if (v.entidad) fd.append('entidad', v.entidad);
            if (v.numeroCertificado) fd.append('numeroCertificado', v.numeroCertificado);
            if (v.observaciones) fd.append('observaciones', v.observaciones);
            fd.append('soporte', archivo);
            await api.form<Incapacidad>('POST', '/Disability', fd);
            toast.ok('Incapacidad enviada a revisión');
            setNueva(false);
            setFiltro(revisa ? 'PENDIENTE' : 'TODAS');
            lista.reload();
          }}
        >
          {() => (
            <div className="form-grid">
              {registraParaOtros && (
                <Field label="Trabajador incapacitado *" full>
                  <select required {...form.bind('empleadoId')}>
                    <option value="">Seleccione...</option>
                    {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cedula}</option>)}
                  </select>
                </Field>
              )}
              <Field label="Tipo *">
                <select {...form.bind('tipo')}>{TIPOS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}</select>
              </Field>
              <Field label={`${tipoActual?.quien ?? 'EPS / ARL'} que la expide`}>
                <input placeholder={tipoActual?.quien === 'ARL' ? 'Ej.: ARL Sura' : 'Ej.: EPS Sanitas'} maxLength={120} {...form.bind('entidad')} />
              </Field>
              <Field label="Desde *"><input type="date" required {...form.bind('fechaInicio')} /></Field>
              <Field label={`Hasta * ${dias > 0 ? `(${dias} ${dias === 1 ? 'día' : 'días'})` : ''}`}><input type="date" required min={form.values.fechaInicio} {...form.bind('fechaFin')} /></Field>
              <Field label="N.º de certificado"><input maxLength={60} {...form.bind('numeroCertificado')} /></Field>
              <Field label="¿Es prórroga de una anterior?">
                <label className="check"><input type="checkbox" {...form.check('prorroga')} /> Sí, continúa una incapacidad anterior</label>
              </Field>
              <Field label="Certificado (PDF o foto) *" full>
                <input type="file" accept={ACEPTA} required onChange={(e) => setArchivo(e.target.files?.[0] ?? null)} />
                {archivo && <span className="muted">{archivo.name} · {(archivo.size / 1024 / 1024).toFixed(2)} MB</span>}
              </Field>
              <Field label="Observaciones" full>
                <textarea rows={2} maxLength={1000} placeholder="Información adicional para SG-SST (opcional)" {...form.bind('observaciones')} />
              </Field>
            </div>
          )}
        </FormModal>
      )}

      {rechazar && (
        <FormModal
          title={`Rechazar incapacidad de ${rechazar.empleado}`}
          submitLabel="Rechazar"
          onClose={() => setRechazar(null)}
          onSubmit={async () => {
            await api.put(`/Disability/${rechazar.id}/Review`, { aprobar: false, motivo });
            toast.ok('Incapacidad rechazada: se avisó al trabajador');
            setRechazar(null);
            lista.reload();
          }}
        >
          {() => (
            <Field label="Motivo (lo verá el trabajador para corregirlo) *" full>
              <textarea required rows={3} maxLength={500} value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej.: el certificado no es legible, las fechas no coinciden..." />
            </Field>
          )}
        </FormModal>
      )}

      {anular && (
        <FormModal
          title={`Anular incapacidad de ${anular.empleado}`}
          submitLabel="Anular"
          onClose={() => setAnular(null)}
          onSubmit={async () => {
            const r = await api.put<Incapacidad>(`/Disability/${anular.id}/Cancel`, { motivo });
            toast.ok('Incapacidad anulada');
            if (r.aviso) toast.error(r.aviso);
            setAnular(null);
            lista.reload();
          }}
        >
          {() => (
            <>
              <p className="muted">Si la nómina del mes sigue abierta se recalcula automáticamente.</p>
              <Field label="Motivo *" full>
                <textarea required minLength={3} rows={3} maxLength={500} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
              </Field>
            </>
          )}
        </FormModal>
      )}
    </>
  );
}
