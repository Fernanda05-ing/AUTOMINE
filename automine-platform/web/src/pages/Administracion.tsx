import { useState } from 'react';
import { api, type Usuario } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, Field, FormModal, PageHeader, Tabs, useForm, useLoad, useToast } from '../components/ui';
import { fmt } from '../format';
import type { EmpleadoOpcion } from '../types';

interface Rol {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
}
interface Pagina<T> {
  content: T[];
  page: { number: number; totalPages: number; totalElements: number };
}
interface Acceso {
  id: number;
  email: string;
  evento: string;
  ip: string | null;
  agente: string | null;
  fecha: string;
}
interface Auditoria {
  id: number;
  usuario: string;
  accion: string;
  entidad: string;
  entidadId: string | null;
  detalle: string | null;
  fecha: string;
}
interface Parametro {
  clave: string;
  valor: string;
  descripcion: string;
}

function Paginado<T>({ path, columns }: { path: string; columns: Parameters<typeof DataTable<T>>[0]['columns'] }) {
  const [page, setPage] = useState(0);
  const data = useLoad(() => api.get<Pagina<T>>(path, { page, size: 50 }), [page]);
  const total = data.data?.page.totalPages ?? 1;
  return (
    <>
      <DataTable<T> rows={data.data?.content} columns={columns} />
      <div className="actions mt">
        <button className="btn small" disabled={page === 0} onClick={() => setPage(page - 1)}>← Anterior</button>
        <span className="muted">Pagina {page + 1} de {Math.max(total, 1)}</span>
        <button className="btn small" disabled={page + 1 >= total} onClick={() => setPage(page + 1)}>Siguiente →</button>
      </div>
    </>
  );
}

/** Administracion: usuarios y roles (RF003), bitacora (RF005), auditoria (RNF005) y parametros. */
export default function Administracion() {
  const { user } = useAuth();
  const toast = useToast();
  const [tab, setTab] = useState<'usuarios' | 'bitacora' | 'auditoria' | 'parametros' | 'correo'>('usuarios');
  const usuarios = useLoad(() => api.get<Usuario[]>('/Users'));
  const roles = useLoad(() => api.get<Rol[]>('/Roles'));
  const empleados = useLoad(() => api.get<EmpleadoOpcion[]>('/Employees/Options'));
  const parametros = useLoad(() => api.get<Parametro[]>('/Settings'));
  const [form, setForm] = useState<{ u?: Usuario } | null>(null);
  const [param, setParam] = useState<Parametro | null>(null);
  const uf = useForm({ nombre: '', email: '', password: '', rol: 'EMPLEADO', empleadoId: '', estado: 'ACTIVO' });
  const pf = useForm({ valor: '' });

  return (
    <>
      <PageHeader title="Administración" subtitle="Usuarios, roles, bitácora de accesos, auditoría y parámetros del sistema">
        {tab === 'usuarios' && <button className="btn primary" onClick={() => { uf.reset(); setForm({}); }}>Crear usuario</button>}
      </PageHeader>
      <Tabs
        tabs={[{ key: 'usuarios', label: 'Usuarios' }, { key: 'bitacora', label: 'Bitácora de accesos' }, { key: 'auditoria', label: 'Auditoría' }, { key: 'parametros', label: 'Parámetros' }, { key: 'correo', label: 'Correo' }]}
        active={tab}
        onChange={setTab}
      />
      {tab === 'usuarios' && (
        <DataTable<Usuario>
          rows={usuarios.data}
          columns={[
            { key: 'nombre', label: 'Nombre' },
            { key: 'email', label: 'Correo' },
            { key: 'r', label: 'Rol', render: (u) => u.rolNombre },
            { key: 'emp', label: 'Empleado', render: (u) => (u.empleadoId ? empleados.data?.find((e) => e.id === u.empleadoId)?.nombre ?? `#${u.empleadoId}` : '—') },
            { key: 'ua', label: 'Último acceso', render: (u) => fmt.dateTime(u.ultimoAcceso) || '—' },
            { key: 'e', label: 'Estado', render: (u) => <Badge value={u.estado} /> },
            {
              key: 'a',
              label: '',
              render: (u) => (
                <div className="actions">
                  <button className="btn small" onClick={() => { uf.setValues({ nombre: u.nombre, email: u.email, password: '', rol: u.rol, empleadoId: u.empleadoId ? String(u.empleadoId) : '', estado: u.estado }); setForm({ u }); }}>Editar</button>
                  {u.id !== user?.id && (
                    <ActionButton action={() => api.put(`/Users/${u.id}/Status`, { estado: u.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO' })} success="Estado actualizado" onDone={usuarios.reload}>
                      {u.estado === 'ACTIVO' ? 'Inactivar' : 'Activar'}
                    </ActionButton>
                  )}
                </div>
              ),
            },
          ]}
        />
      )}
      {tab === 'bitacora' && (
        <Paginado<Acceso>
          path="/AccessLog"
          columns={[
            { key: 'f', label: 'Fecha', render: (a) => fmt.dateTime(a.fecha) },
            { key: 'email', label: 'Usuario' },
            { key: 'e', label: 'Evento', render: (a) => <Badge value={a.evento} /> },
            { key: 'ip', label: 'IP' },
            { key: 'ag', label: 'Navegador / app', render: (a) => <span className="muted" style={{ fontSize: 11 }}>{a.agente?.substring(0, 70)}</span> },
          ]}
        />
      )}
      {tab === 'auditoria' && (
        <Paginado<Auditoria>
          path="/Audit"
          columns={[
            { key: 'f', label: 'Fecha', render: (a) => fmt.dateTime(a.fecha) },
            { key: 'usuario', label: 'Usuario' },
            { key: 'accion', label: 'Acción' },
            { key: 'en', label: 'Registro', render: (a) => `${a.entidad}${a.entidadId ? ' #' + a.entidadId : ''}` },
            { key: 'detalle', label: 'Detalle' },
          ]}
        />
      )}
      {tab === 'correo' && <Correo />}
      {tab === 'parametros' && (
        <>
          <p className="muted">Valores legales usados en la liquidacion de nomina y limites de caja menor. Los porcentajes van como fraccion (0.04 = 4 %).</p>
          <DataTable<Parametro>
            rows={parametros.data}
            rowKey={(p) => p.clave}
            columns={[
              { key: 'clave', label: 'Parámetro' },
              { key: 'descripcion', label: 'Descripción' },
              { key: 'valor', label: 'Valor', num: true },
              { key: 'a', label: '', render: (p) => <button className="btn small" onClick={() => { pf.setValues({ valor: p.valor }); setParam(p); }}>Cambiar</button> },
            ]}
          />
        </>
      )}
      {form && (
        <FormModal
          title={form.u ? `Editar ${form.u.nombre}` : 'Crear usuario'}
          onClose={() => setForm(null)}
          onSubmit={async () => {
            const v = uf.values;
            if (form.u) {
              await api.put('/EditUser', { usuarioId: form.u.id, nombre: v.nombre, email: v.email, password: v.password || null, rol: v.rol, empleadoId: v.empleadoId ? Number(v.empleadoId) : null, estado: v.estado });
            } else {
              await api.post('/CreateUser', { nombre: v.nombre, email: v.email, password: v.password, rol: v.rol, empleadoId: v.empleadoId ? Number(v.empleadoId) : null });
            }
            toast.ok('Usuario guardado');
            setForm(null);
            usuarios.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Nombre *" error={err.nombre}><input required {...uf.bind('nombre')} /></Field>
              <Field label="Correo *" error={err.email}><input type="email" required {...uf.bind('email')} /></Field>
              <Field label={form.u ? 'Nueva contraseña (opcional)' : 'Contraseña *'} error={err.password}>
                <input type="password" required={!form.u} {...uf.bind('password')} placeholder="8+ caracteres, letras y números" autoComplete="new-password" />
              </Field>
              <Field label="Rol *">
                <select {...uf.bind('rol')}>{roles.data?.map((r) => <option key={r.codigo} value={r.codigo}>{r.nombre}</option>)}</select>
              </Field>
              <Field label={uf.values.rol === 'EMPLEADO' ? 'Empleado asociado *' : 'Empleado asociado'} full>
                <select required={uf.values.rol === 'EMPLEADO'} {...uf.bind('empleadoId')}>
                  <option value="">—</option>
                  {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cedula}</option>)}
                </select>
              </Field>
              <p className="muted full" style={{ margin: 0 }}>{roles.data?.find((r) => r.codigo === uf.values.rol)?.descripcion}</p>
            </div>
          )}
        </FormModal>
      )}
      {param && (
        <FormModal
          title={param.descripcion}
          onClose={() => setParam(null)}
          onSubmit={async () => {
            await api.put(`/Settings/${param.clave}`, pf.values);
            toast.ok('Parámetro actualizado');
            setParam(null);
            parametros.reload();
          }}
        >
          {() => <Field label={param.clave}><input required {...pf.bind('valor')} inputMode="decimal" /></Field>}
        </FormModal>
      )}
    </>
  );
}

/** Estado del correo SMTP y envio de un correo de prueba. */
function Correo() {
  const toast = useToast();
  const estado = useLoad(() => api.get<{ configurado: boolean; urlPublica: string }>('/Mail/Status'));
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  return (
    <div className="card" style={{ maxWidth: 680 }}>
      <h3>Correo electrónico</h3>
      {estado.data && (
        <div className={`alert ${estado.data.configurado ? 'success' : 'warn'}`}>
          {estado.data.configurado
            ? 'El servidor de correo está configurado. Envíe un correo de prueba para confirmarlo.'
            : 'El correo no está configurado: los mensajes se escriben en el registro del servidor. Defina MAIL_USERNAME y MAIL_PASSWORD (contraseña de aplicación de Gmail).'}
        </div>
      )}
      <p className="muted">
        Se envían correos de recuperación de contraseña, bienvenida de usuarios, nómina por aprobar, desprendibles disponibles,
        pagos consignados, accidentes reportados y un resumen diario de alertas de inventario. Las notificaciones se pueden apagar
        en Parámetros (NOTIFICACIONES_CORREO = 0).
      </p>
      {estado.data && <p className="muted">Los enlaces de los correos apuntan a: <code>{estado.data.urlPublica}</code></p>}
      <form
        className="actions mt"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          try {
            const r = await api.post<{ message: string }>('/Mail/Test', { email });
            toast.ok(r.message);
          } catch (err) {
            toast.error((err as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <input type="email" required placeholder="correo@empresa.co" value={email} onChange={(e) => setEmail(e.target.value)} style={{ flex: 1, minWidth: 220 }} aria-label="Correo de destino" />
        <button className="btn primary" disabled={busy || !estado.data?.configurado}>{busy ? 'Enviando...' : 'Enviar correo de prueba'}</button>
      </form>
    </div>
  );
}
