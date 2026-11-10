import { Camera, ScanFace, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, ErrorBox, Field, FormModal, Modal, PageHeader, Spinner, Stat, Tabs, useLoad, useToast } from '../components/ui';
import { fmt, today } from '../format';

interface Marcacion {
  id: number;
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  tipo: string;
  fechaHora: string;
  metodo: string;
  similitud: number | null;
  tieneFoto: boolean;
  observacion: string | null;
  anulada: boolean;
  registradoPor: string;
}
interface Dia {
  fecha: string;
  entrada: string | null;
  salida: string | null;
  horas: number;
  extraDiurna: number;
  extraNocturna: number;
  extraDominical: number;
  dominical: boolean;
  inconsistencia: string | null;
}
interface Resumen {
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  diasTrabajados: number;
  horas: number;
  extraDiurna: number;
  extraNocturna: number;
  extraDominical: number;
  inconsistencias: number;
  dias: Dia[];
}
interface EstadoRostro {
  empleadoId: number;
  cedula: string;
  nombre: string;
  cargo: string;
  registrado: boolean;
  registradoEl: string | null;
  registradoPor: string | null;
}
interface ResultadoNomina {
  aplicadas: number;
  sinNomina: number;
  cerradas: number;
  detalle: string[];
}

type Vista = 'DIA' | 'MES' | 'ROSTROS';
const TONO_METODO: Record<string, string> = { FACIAL: 'ok', VERIFICACION_MANUAL: 'warn', MANUAL: 'danger' };
const h = (n: number) => fmt.num(n);
const hora = (iso: string | null) => (iso ? iso.substring(11, 16) : '—');

/** Control de asistencia: SG-SST marca entradas y salidas con reconocimiento facial desde el celular. */
export default function Asistencia() {
  const { can } = useAuth();
  const toast = useToast();
  const gestionaRostros = can('SGSST', 'RRHH');
  const aplicaNomina = can('CONTADOR', 'RRHH');
  const [vista, setVista] = useState<Vista>('DIA');
  const [fecha, setFecha] = useState(today());
  const ahora = new Date();
  const [anio, setAnio] = useState(ahora.getFullYear());
  const [mes, setMes] = useState(ahora.getMonth() + 1);
  const [foto, setFoto] = useState<{ url: string; titulo: string } | null>(null);
  const [detalle, setDetalle] = useState<Resumen | null>(null);
  const [anular, setAnular] = useState<Marcacion | null>(null);
  const [motivo, setMotivo] = useState('');

  const marcas = useLoad(() => (vista === 'DIA' ? api.get<Marcacion[]>('/Attendance', { fecha }) : Promise.resolve([])), [vista, fecha]);
  const resumen = useLoad(() => (vista === 'MES' ? api.get<Resumen[]>('/Attendance/Summary', { anio, mes }) : Promise.resolve([])), [vista, anio, mes]);
  const rostros = useLoad(() => (vista === 'ROSTROS' && gestionaRostros ? api.get<EstadoRostro[]>('/Faces/Status') : Promise.resolve([])), [vista]);

  const verFoto = async (path: string, titulo: string) => {
    try {
      setFoto({ url: await api.blob(path), titulo });
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const lista = marcas.data ?? [];
  const validas = lista.filter((m) => !m.anulada);
  const dentro = new Set<number>();
  [...validas]
    .sort((a, b) => a.fechaHora.localeCompare(b.fechaHora))
    .forEach((m) => (m.tipo === 'ENTRADA' ? dentro.add(m.empleadoId) : dentro.delete(m.empleadoId)));
  const res = resumen.data ?? [];
  const reg = rostros.data ?? [];

  return (
    <>
      <PageHeader title="Asistencia" subtitle="Entradas y salidas registradas por SG-SST con reconocimiento facial desde el celular" />
      <Tabs<Vista>
        tabs={[
          { key: 'DIA', label: 'Marcaciones del día' },
          { key: 'MES', label: 'Horas del mes' },
          ...(gestionaRostros ? [{ key: 'ROSTROS' as Vista, label: 'Rostros registrados' }] : []),
        ]}
        active={vista}
        onChange={setVista}
      />

      {vista === 'DIA' && (
        <>
          <div className="toolbar mb">
            <input type="date" value={fecha} max={today()} onChange={(e) => setFecha(e.target.value)} aria-label="Fecha" />
          </div>
          <div className="grid grid-4 mb">
            <Stat label="Marcaciones" value={validas.length} />
            <Stat label="Trabajadores en turno" value={fecha === today() ? dentro.size : '—'} />
            <Stat label="Verificación manual" value={validas.filter((m) => m.metodo !== 'FACIAL').length} />
            <Stat label="Anuladas" value={lista.length - validas.length} />
          </div>
          <ErrorBox error={marcas.error} />
          {marcas.loading && !marcas.data ? (
            <Spinner />
          ) : (
            <DataTable<Marcacion>
              rows={lista}
              empty="No hay marcaciones en esta fecha. La SISO las registra desde la app móvil (pestaña Asistencia)."
              search={(m) => `${m.empleado} ${m.cedula}`}
              searchPlaceholder="Buscar trabajador"
              columns={[
                { key: 'h', label: 'Hora', render: (m) => <strong>{hora(m.fechaHora)}</strong> },
                { key: 'e', label: 'Trabajador', render: (m) => <><strong>{m.empleado}</strong><div className="muted">{m.cedula} · {m.cargo}</div></> },
                { key: 't', label: 'Tipo', render: (m) => <Badge value={m.tipo} tone={m.tipo === 'ENTRADA' ? 'ok' : 'info'} /> },
                {
                  key: 'm',
                  label: 'Verificación',
                  render: (m) => (
                    <>
                      <Badge value={m.metodo} tone={TONO_METODO[m.metodo]} />
                      {m.similitud !== null && <div className="muted">Similitud {Math.round(m.similitud * 100)} %</div>}
                    </>
                  ),
                },
                { key: 'o', label: 'Observación', clamp: true, render: (m) => m.observacion ?? '' },
                { key: 'r', label: 'Registró', render: (m) => <span className="muted">{m.registradoPor}</span> },
                {
                  key: 'a',
                  label: '',
                  render: (m) => (
                    <div className="actions">
                      {m.tieneFoto && (
                        <button className="btn small" onClick={() => void verFoto(`/Attendance/${m.id}/Photo`, `${m.empleado} — ${m.tipo.toLowerCase()} ${hora(m.fechaHora)}`)}>
                          <Camera /> Foto
                        </button>
                      )}
                      {gestionaRostros && !m.anulada && <button className="btn small danger" onClick={() => { setMotivo(''); setAnular(m); }}>Anular</button>}
                      {m.anulada && <Badge value="ANULADA" />}
                    </div>
                  ),
                },
              ]}
            />
          )}
        </>
      )}

      {vista === 'MES' && (
        <>
          <div className="toolbar mb">
            <select value={mes} onChange={(e) => setMes(Number(e.target.value))} aria-label="Mes">
              {Array.from({ length: 12 }, (_, i) => <option key={i} value={i + 1}>{fmt.mes(i + 1)}</option>)}
            </select>
            <select value={anio} onChange={(e) => setAnio(Number(e.target.value))} aria-label="Año">
              {[ahora.getFullYear() - 1, ahora.getFullYear()].map((a) => <option key={a} value={a}>{a}</option>)}
            </select>
            <div style={{ flex: 1 }} />
            {aplicaNomina && res.length > 0 && (
              <ActionButton
                className="btn primary"
                action={async () => {
                  const r = await api.post<ResultadoNomina>(`/Attendance/ApplyToPayroll?anio=${anio}&mes=${mes}`);
                  if (r.detalle.length) toast.error(r.detalle.slice(0, 4).join(' · ') + (r.detalle.length > 4 ? ` y ${r.detalle.length - 4} más` : ''));
                  toast.ok(`Horas extra aplicadas en ${r.aplicadas} nóminas`);
                }}
                confirm={`¿Pasar las horas extra de ${fmt.mes(mes)} ${anio} a las nóminas abiertas? Reemplaza las horas extra digitadas a mano.`}
              >
                Pasar horas extra a la nómina
              </ActionButton>
            )}
          </div>
          <div className="grid grid-4 mb">
            <Stat label="Horas trabajadas" value={h(res.reduce((s, r) => s + r.horas, 0))} />
            <Stat label="Extra diurnas" value={h(res.reduce((s, r) => s + r.extraDiurna, 0))} />
            <Stat label="Extra nocturnas" value={h(res.reduce((s, r) => s + r.extraNocturna, 0))} />
            <Stat label="Extra dominicales/festivas" value={h(res.reduce((s, r) => s + r.extraDominical, 0))} />
          </div>
          <ErrorBox error={resumen.error} />
          {resumen.loading && !resumen.data ? (
            <Spinner />
          ) : (
            <DataTable<Resumen>
              rows={res}
              empty="No hay marcaciones en este mes"
              search={(r) => `${r.empleado} ${r.cedula}`}
              searchPlaceholder="Buscar trabajador"
              columns={[
                { key: 'e', label: 'Trabajador', render: (r) => <><strong>{r.empleado}</strong><div className="muted">{r.cedula} · {r.cargo}</div></> },
                { key: 'd', label: 'Días', num: true, render: (r) => r.diasTrabajados },
                { key: 'h', label: 'Horas', num: true, render: (r) => h(r.horas) },
                { key: 'ed', label: 'Ext. diurnas', num: true, render: (r) => h(r.extraDiurna) },
                { key: 'en', label: 'Ext. nocturnas', num: true, render: (r) => h(r.extraNocturna) },
                { key: 'edd', label: 'Ext. dom/fest', num: true, render: (r) => h(r.extraDominical) },
                { key: 'i', label: 'Novedades', render: (r) => (r.inconsistencias > 0 ? <Badge value={`${r.inconsistencias} por revisar`} tone="warn" /> : <span className="muted">—</span>) },
                { key: 'a', label: '', render: (r) => <button className="btn small" onClick={() => setDetalle(r)}>Ver días</button> },
              ]}
            />
          )}
          <p className="muted mt">
            Jornada ordinaria y horario nocturno según los parámetros de Administración (por defecto 8 h diarias, nocturno de 7 p. m. a 6 a. m., Ley 2466 de 2025). Las horas en domingo o festivo se cuentan como dominicales.
          </p>
        </>
      )}

      {vista === 'ROSTROS' && (
        <>
          <div className="alert info mb">
            Los rostros se registran desde la app móvil de la SISO (Asistencia → Registrar rostro), con la autorización del trabajador (Ley 1581 de 2012). Se eliminan automáticamente cuando el trabajador se retira.
          </div>
          <div className="grid grid-4 mb">
            <Stat label="Registrados" value={reg.filter((r) => r.registrado).length} />
            <Stat label="Sin registrar" value={reg.filter((r) => !r.registrado).length} />
          </div>
          <DataTable<EstadoRostro>
            rows={reg}
            search={(r) => `${r.nombre} ${r.cedula}`}
            searchPlaceholder="Buscar trabajador"
            columns={[
              { key: 'e', label: 'Trabajador', render: (r) => <><strong>{r.nombre}</strong><div className="muted">{r.cedula} · {r.cargo}</div></> },
              { key: 's', label: 'Rostro', render: (r) => (r.registrado ? <Badge value="REGISTRADO" tone="ok" /> : <Badge value="SIN_REGISTRAR" tone="warn" />) },
              { key: 'f', label: 'Registrado', render: (r) => (r.registradoEl ? <>{fmt.dateTime(r.registradoEl)}<div className="muted">por {r.registradoPor}</div></> : '') },
              {
                key: 'a',
                label: '',
                render: (r) =>
                  r.registrado && (
                    <div className="actions">
                      <button className="btn small" onClick={() => void verFoto(`/Employee/${r.empleadoId}/Face/Photo`, r.nombre)}>
                        <ScanFace /> Ver
                      </button>
                      <ActionButton
                        className="btn small danger"
                        action={() => api.del(`/Employee/${r.empleadoId}/Face`)}
                        confirm={`¿Eliminar el rostro de ${r.nombre}? Tendrá que registrarse de nuevo para marcar con reconocimiento facial.`}
                        success="Rostro eliminado"
                        onDone={rostros.reload}
                      >
                        <Trash2 />
                      </ActionButton>
                    </div>
                  ),
              },
            ]}
          />
        </>
      )}

      {foto && (
        <Modal title={foto.titulo} onClose={() => { URL.revokeObjectURL(foto.url); setFoto(null); }}>
          <img src={foto.url} alt={foto.titulo} style={{ width: '100%', borderRadius: 12 }} />
        </Modal>
      )}

      {detalle && (
        <Modal title={`${detalle.empleado} — ${fmt.mes(mes)} ${anio}`} onClose={() => setDetalle(null)} wide>
          <table className="table">
            <thead>
              <tr><th>Fecha</th><th>Entrada</th><th>Salida</th><th className="num">Horas</th><th className="num">Ext. diur.</th><th className="num">Ext. noct.</th><th className="num">Ext. dom.</th><th>Novedad</th></tr>
            </thead>
            <tbody>
              {detalle.dias.map((d, i) => (
                <tr key={i}>
                  <td>{fmt.date(d.fecha)}{d.dominical && <span className="muted"> (dom/fest)</span>}</td>
                  <td>{hora(d.entrada)}</td>
                  <td>{hora(d.salida)}</td>
                  <td className="num">{h(d.horas)}</td>
                  <td className="num">{h(d.extraDiurna)}</td>
                  <td className="num">{h(d.extraNocturna)}</td>
                  <td className="num">{h(d.extraDominical)}</td>
                  <td>{d.inconsistencia && <span className="badge warn">{d.inconsistencia}</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Modal>
      )}

      {anular && (
        <FormModal
          title="Anular marcación"
          submitLabel="Anular"
          onClose={() => setAnular(null)}
          onSubmit={async () => {
            await api.put(`/Attendance/${anular.id}/Cancel`, { motivo });
            toast.ok('Marcación anulada');
            setAnular(null);
            marcas.reload();
          }}
        >
          {() => (
            <>
              <p>{anular.empleado} — {anular.tipo.toLowerCase()} de las {hora(anular.fechaHora)}</p>
              <Field label="Motivo *" full>
                <textarea required rows={2} maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej.: marcación duplicada, persona equivocada" />
              </Field>
            </>
          )}
        </FormModal>
      )}
    </>
  );
}
