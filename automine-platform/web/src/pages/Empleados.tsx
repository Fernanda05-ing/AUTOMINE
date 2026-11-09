import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import {
  ActionButton,
  Badge,
  DataTable,
  ErrorBox,
  Field,
  FormModal,
  Modal,
  PageHeader,
  Stat,
  Tabs,
  useForm,
  useLoad,
  useToast,
} from '../components/ui';
import { fmt, today } from '../format';
import type { Empleado } from '../types';

const CONTRATOS = ['INDEFINIDO', 'FIJO', 'OBRA_LABOR', 'APRENDIZAJE', 'PRESTACION_SERVICIOS'];

interface Historial {
  id: number;
  fecha: string;
  tipo: string;
  descripcion: string;
  registradoPor: string;
}
interface Contrato {
  id: number;
  tipo: string;
  cargo: string;
  salario: number;
  fechaInicio: string;
  fechaFin: string | null;
  estado: string;
}
interface Documento {
  id: number;
  nombre: string;
  tipo: string;
  tamano: number;
  subidoPor: string;
  createdAt: string;
}

const vacio = {
  cedula: '', nombres: '', apellidos: '', email: '', telefono: '', direccion: '', fechaNacimiento: '',
  cargo: '', area: '', salario: '', fechaIngreso: today(), tipoContrato: 'INDEFINIDO', banco: '',
  tipoCuenta: '', numeroCuenta: '', eps: '', fondoPension: '', arl: '', crearUsuario: false,
};

function EmpleadoForm({ empleado, onClose, onSaved }: { empleado?: Empleado; onClose: () => void; onSaved: (e: Empleado) => void }) {
  const f = useForm<typeof vacio>(
    empleado
      ? Object.fromEntries(Object.keys(vacio).map((k) => [k, (empleado as unknown as Record<string, unknown>)[k] ?? (k === 'crearUsuario' ? false : '')])) as typeof vacio
      : vacio,
  );
  const submit = async () => {
    const body = { ...f.values, salario: Number(f.values.salario), fechaNacimiento: f.values.fechaNacimiento || null };
    const saved = empleado
      ? await api.put<Empleado>(`/Employee/${empleado.id}`, body)
      : await api.post<Empleado>('/Employee', body);
    onSaved(saved);
  };
  return (
    <FormModal title={empleado ? `Editar ${empleado.nombreCompleto}` : 'Registrar empleado'} onClose={onClose} onSubmit={submit} wide>
      {(err) => (
        <div className="form-grid">
          <Field label="Cédula *" error={err.cedula}><input required {...f.bind('cedula')} inputMode="numeric" /></Field>
          <Field label="Correo" error={err.email}><input type="email" {...f.bind('email')} /></Field>
          <Field label="Nombres *" error={err.nombres}><input required {...f.bind('nombres')} /></Field>
          <Field label="Apellidos *" error={err.apellidos}><input required {...f.bind('apellidos')} /></Field>
          <Field label="Cargo *" error={err.cargo}><input required {...f.bind('cargo')} /></Field>
          <Field label="Área"><input {...f.bind('area')} /></Field>
          <Field label="Salario mensual *" error={err.salario}><input type="number" min={1} required {...f.bind('salario')} /></Field>
          <Field label="Tipo de contrato *">
            <select {...f.bind('tipoContrato')}>{CONTRATOS.map((c) => <option key={c} value={c}>{fmt.label(c)}</option>)}</select>
          </Field>
          <Field label="Fecha de ingreso *" error={err.fechaIngreso}><input type="date" required {...f.bind('fechaIngreso')} /></Field>
          <Field label="Fecha de nacimiento" error={err.fechaNacimiento}><input type="date" {...f.bind('fechaNacimiento')} /></Field>
          <Field label="Teléfono"><input {...f.bind('telefono')} /></Field>
          <Field label="Dirección"><input {...f.bind('direccion')} /></Field>
          <Field label="Banco"><input {...f.bind('banco')} /></Field>
          <Field label="Tipo de cuenta">
            <select {...f.bind('tipoCuenta')}><option value="">—</option><option value="AHORROS">Ahorros</option><option value="CORRIENTE">Corriente</option></select>
          </Field>
          <Field label="Número de cuenta"><input {...f.bind('numeroCuenta')} /></Field>
          <Field label="EPS"><input {...f.bind('eps')} /></Field>
          <Field label="Fondo de pensión"><input {...f.bind('fondoPension')} /></Field>
          <Field label="ARL"><input {...f.bind('arl')} /></Field>
          {!empleado && (
            <label className="check full">
              <input type="checkbox" {...f.check('crearUsuario')} /> Crear usuario de la app movil para el empleado (requiere correo)
            </label>
          )}
        </div>
      )}
    </FormModal>
  );
}

function Detalle({ empleado, onClose, onChanged }: { empleado: Empleado; onClose: () => void; onChanged: () => void }) {
  const { can } = useAuth();
  const toast = useToast();
  const editable = can('RRHH');
  const [tab, setTab] = useState<'datos' | 'historial' | 'contratos' | 'documentos'>('datos');
  const historial = useLoad(() => api.get<Historial[]>(`/Employee/${empleado.id}/History`), [empleado.id]);
  const contratos = useLoad(() => api.get<Contrato[]>(`/Employee/${empleado.id}/Contracts`), [empleado.id]);
  const documentos = useLoad(() => (editable ? api.get<Documento[]>(`/Employee/${empleado.id}/Documents`) : Promise.resolve([])), [empleado.id]);
  const [modal, setModal] = useState<'novedad' | 'contrato' | 'retiro' | null>(null);
  const nov = useForm({ fecha: today(), tipo: 'OBSERVACION', descripcion: '' });
  const con = useForm({ tipo: empleado.tipoContrato, cargo: empleado.cargo, salario: String(empleado.salario), fechaInicio: today(), fechaFin: '' });
  const ret = useForm({ fechaRetiro: today(), motivo: '' });
  const [tipoDoc, setTipoDoc] = useState('CEDULA');

  const e = empleado;
  return (
    <Modal title={e.nombreCompleto} onClose={onClose} wide>
      <div className="actions mb">
        <Badge value={e.estado} />
        <span className="muted">CC {e.cedula} · {e.cargo}</span>
        <div style={{ flex: 1 }} />
        {editable && e.estado === 'ACTIVO' && <button className="btn small danger" onClick={() => setModal('retiro')}>Inactivar</button>}
        {editable && e.estado === 'INACTIVO' && (
          <ActionButton action={() => api.put(`/Employee/${e.id}/Reactivate`)} success="Empleado reactivado" onDone={onChanged}>Reactivar</ActionButton>
        )}
      </div>
      <Tabs
        tabs={[
          { key: 'datos', label: 'Datos' },
          { key: 'historial', label: 'Historial laboral' },
          { key: 'contratos', label: 'Contratos' },
          ...(editable ? [{ key: 'documentos' as const, label: 'Documentos' }] : []),
        ]}
        active={tab}
        onChange={setTab}
      />
      {tab === 'datos' && (
        <dl className="kv">
          <dt>Correo</dt><dd>{e.email}</dd>
          <dt>Teléfono</dt><dd>{e.telefono}</dd>
          <dt>Dirección</dt><dd>{e.direccion}</dd>
          <dt>Nacimiento</dt><dd>{fmt.date(e.fechaNacimiento)}</dd>
          <dt>Área</dt><dd>{e.area}</dd>
          <dt>Salario</dt><dd>{fmt.money(e.salario)}</dd>
          <dt>Contrato</dt><dd>{fmt.label(e.tipoContrato)}</dd>
          <dt>Ingreso</dt><dd>{fmt.date(e.fechaIngreso)}</dd>
          <dt>Retiro</dt><dd>{fmt.date(e.fechaRetiro) || '—'}</dd>
          <dt>Cuenta</dt><dd>{[e.banco, fmt.label(e.tipoCuenta), e.numeroCuenta].filter(Boolean).join(' · ')}</dd>
          <dt>Seguridad social</dt><dd>{[e.eps, e.fondoPension, e.arl].filter(Boolean).join(' · ')}</dd>
          <dt>Usuario app</dt><dd>{e.usuarioId ? 'Sí' : 'No'}</dd>
        </dl>
      )}
      {tab === 'historial' && (
        <>
          {editable && <div className="actions mb"><button className="btn small" onClick={() => setModal('novedad')}>Registrar novedad</button></div>}
          <DataTable<Historial>
            rows={historial.data}
            columns={[
              { key: 'f', label: 'Fecha', render: (h) => fmt.date(h.fecha) },
              { key: 't', label: 'Tipo', render: (h) => <Badge value={h.tipo} tone="info" /> },
              { key: 'd', label: 'Descripción', render: (h) => h.descripcion },
              { key: 'r', label: 'Registrado por', render: (h) => h.registradoPor },
            ]}
          />
        </>
      )}
      {tab === 'contratos' && (
        <>
          {editable && e.estado === 'ACTIVO' && <div className="actions mb"><button className="btn small" onClick={() => setModal('contrato')}>Nuevo contrato / otrosi</button></div>}
          <DataTable<Contrato>
            rows={contratos.data}
            columns={[
              { key: 't', label: 'Tipo', render: (c) => fmt.label(c.tipo) },
              { key: 'c', label: 'Cargo', render: (c) => c.cargo },
              { key: 's', label: 'Salario', num: true, render: (c) => fmt.money(c.salario) },
              { key: 'i', label: 'Inicio', render: (c) => fmt.date(c.fechaInicio) },
              { key: 'f', label: 'Fin', render: (c) => fmt.date(c.fechaFin) || '—' },
              { key: 'e', label: 'Estado', render: (c) => <Badge value={c.estado} /> },
            ]}
          />
        </>
      )}
      {tab === 'documentos' && (
        <>
          <div className="toolbar">
            <Field label="Tipo de documento">
              <select value={tipoDoc} onChange={(ev) => setTipoDoc(ev.target.value)}>
                {['CEDULA', 'HOJA_DE_VIDA', 'CONTRATO', 'CERTIFICADO_ESTUDIO', 'EXAMEN_MEDICO', 'AFILIACION', 'OTRO'].map((t) => <option key={t} value={t}>{fmt.label(t)}</option>)}
              </select>
            </Field>
            <Field label="Archivo (PDF, JPG, PNG o Word, max 10 MB)">
              <input
                type="file"
                accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
                onChange={async (ev) => {
                  const file = ev.target.files?.[0];
                  if (!file) return;
                  try {
                    await api.upload(`/Employee/${e.id}/Documents`, file, { tipo: tipoDoc });
                    toast.ok('Documento adjuntado');
                    documentos.reload();
                  } catch (err) {
                    toast.error((err as Error).message);
                  }
                  ev.target.value = '';
                }}
              />
            </Field>
          </div>
          <DataTable<Documento>
            rows={documentos.data}
            columns={[
              { key: 'n', label: 'Archivo', render: (d) => d.nombre },
              { key: 't', label: 'Tipo', render: (d) => fmt.label(d.tipo) },
              { key: 's', label: 'Tamaño', num: true, render: (d) => `${fmt.num(d.tamano / 1024)} KB` },
              { key: 'f', label: 'Fecha', render: (d) => fmt.dateTime(d.createdAt) },
              {
                key: 'a',
                label: '',
                render: (d) => (
                  <div className="actions">
                    <button className="btn small" onClick={() => api.download(`/Employee/${e.id}/Documents/${d.id}`).catch((x) => toast.error(x.message))}>Descargar</button>
                    <ActionButton className="btn small danger" confirm="¿Eliminar el documento?" action={() => api.del(`/Employee/${e.id}/Documents/${d.id}`)} onDone={documentos.reload}>Eliminar</ActionButton>
                  </div>
                ),
              },
            ]}
          />
        </>
      )}

      {modal === 'novedad' && (
        <FormModal title="Registrar novedad" onClose={() => setModal(null)} onSubmit={async () => { await api.post(`/Employee/${e.id}/History`, nov.values); toast.ok('Novedad registrada'); setModal(null); historial.reload(); }}>
          {(err) => (
            <div className="form-grid">
              <Field label="Fecha"><input type="date" required {...nov.bind('fecha')} /></Field>
              <Field label="Tipo">
                <select {...nov.bind('tipo')}>{['OBSERVACION', 'LLAMADO_ATENCION', 'RECONOCIMIENTO', 'INCAPACIDAD', 'VACACIONES', 'LICENCIA', 'CAPACITACION'].map((t) => <option key={t} value={t}>{fmt.label(t)}</option>)}</select>
              </Field>
              <Field label="Descripción" error={err.descripcion} full><textarea required {...nov.bind('descripcion')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {modal === 'contrato' && (
        <FormModal title="Nuevo contrato" onClose={() => setModal(null)} onSubmit={async () => { await api.post(`/Employee/${e.id}/Contracts`, { ...con.values, salario: Number(con.values.salario), fechaFin: con.values.fechaFin || null }); toast.ok('Contrato registrado'); setModal(null); onChanged(); }}>
          {(err) => (
            <div className="form-grid">
              <Field label="Tipo"><select {...con.bind('tipo')}>{CONTRATOS.map((c) => <option key={c} value={c}>{fmt.label(c)}</option>)}</select></Field>
              <Field label="Cargo" error={err.cargo}><input required {...con.bind('cargo')} /></Field>
              <Field label="Salario" error={err.salario}><input type="number" required {...con.bind('salario')} /></Field>
              <Field label="Fecha de inicio"><input type="date" required {...con.bind('fechaInicio')} /></Field>
              <Field label="Fecha de fin (opcional)"><input type="date" {...con.bind('fechaFin')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {modal === 'retiro' && (
        <FormModal title="Inactivar empleado" submitLabel="Inactivar" onClose={() => setModal(null)} onSubmit={async () => { await api.put(`/Employee/${e.id}/Deactivate`, ret.values); toast.ok('Empleado inactivado'); setModal(null); onChanged(); }}>
          {(err) => (
            <>
              <div className="alert warn">El empleado quedará inactivo, su contrato vigente terminará y su usuario de la app será bloqueado.</div>
              <div className="form-grid">
                <Field label="Fecha de retiro" error={err.fechaRetiro}><input type="date" required {...ret.bind('fechaRetiro')} /></Field>
                <Field label="Motivo" error={err.motivo} full><textarea required {...ret.bind('motivo')} /></Field>
              </div>
            </>
          )}
        </FormModal>
      )}
    </Modal>
  );
}

export default function Empleados() {
  const { can } = useAuth();
  const toast = useToast();
  const [q, setQ] = useState('');
  const [estado, setEstado] = useState('ACTIVO');
  const lista = useLoad(() => api.get<Empleado[]>('/Employees', { q, estado }), [estado]);
  const resumen = useLoad(() => api.get<{ total: number; activos: number; inactivos: number }>('/Employees/Summary'));
  const [form, setForm] = useState<{ empleado?: Empleado } | null>(null);
  const [detalle, setDetalle] = useState<Empleado | null>(null);
  const editable = can('RRHH');

  const recargar = () => {
    lista.reload();
    resumen.reload();
  };

  return (
    <>
      <PageHeader title="Empleados" subtitle="Registro, historial laboral, contratos y documentos del personal">
        {editable && <button className="btn primary" onClick={() => setForm({})}>Registrar empleado</button>}
      </PageHeader>
      <div className="grid grid-3 mb">
        <Stat label="Total" value={resumen.data?.total ?? '—'} />
        <Stat label="Activos" value={resumen.data?.activos ?? '—'} />
        <Stat label="Inactivos" value={resumen.data?.inactivos ?? '—'} />
      </div>
      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); lista.reload(); }}>
        <Field label="Buscar"><input placeholder="Nombre, cédula o cargo" value={q} onChange={(e) => setQ(e.target.value)} /></Field>
        <Field label="Estado">
          <select value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="ACTIVO">Activos</option>
            <option value="INACTIVO">Inactivos</option>
            <option value="">Todos</option>
          </select>
        </Field>
        <button className="btn">Buscar</button>
      </form>
      <ErrorBox error={lista.error} />
      <DataTable<Empleado>
        rows={lista.data}
        columns={[
          { key: 'cedula', label: 'Cédula' },
          { key: 'n', label: 'Nombre', render: (e) => <button className="btn link" onClick={() => setDetalle(e)}>{e.nombreCompleto}</button> },
          { key: 'cargo', label: 'Cargo' },
          { key: 'area', label: 'Área' },
          { key: 'i', label: 'Ingreso', render: (e) => fmt.date(e.fechaIngreso) },
          { key: 's', label: 'Salario', num: true, render: (e) => fmt.money(e.salario) },
          { key: 'e', label: 'Estado', render: (e) => <Badge value={e.estado} /> },
          { key: 'a', label: '', render: (e) => editable && <button className="btn small" onClick={() => setForm({ empleado: e })}>Editar</button> },
        ]}
      />
      {form && (
        <EmpleadoForm
          empleado={form.empleado}
          onClose={() => setForm(null)}
          onSaved={(e) => {
            setForm(null);
            recargar();
            toast.ok(e.passwordTemporal ? `Empleado registrado. Contraseña temporal de la app: ${e.passwordTemporal}` : 'Empleado guardado');
          }}
        />
      )}
      {detalle && (
        <Detalle
          key={detalle.id}
          empleado={detalle}
          onClose={() => setDetalle(null)}
          onChanged={async () => {
            recargar();
            setDetalle(await api.get<Empleado>(`/Employee/${detalle.id}`));
          }}
        />
      )}
    </>
  );
}
