import { ClipboardCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Badge, DataTable, ErrorBox, Field, FormModal, Modal, PageHeader, Spinner, Stat, useLoad, useToast } from '../components/ui';
import { fmt } from '../format';

interface Punto {
  item: string;
  critico: boolean;
}
interface Respuesta extends Punto {
  estado: string;
  observacion: string | null;
}
interface Inspeccion {
  id: number;
  maquinariaId: number | null;
  equipo: string;
  tipo: string;
  fechaHora: string;
  horometro: number | null;
  operador: string | null;
  items: Respuesta[];
  fallas: number;
  fallasCriticas: number;
  resultado: string;
  observaciones: string | null;
  inspector: string;
  fotos: number[];
}
interface Maquina {
  id: number;
  codigo: string;
  nombre: string;
  tipo: string;
  estado: string;
}

const TONO: Record<string, string> = { APTO: 'ok', APTO_CON_OBSERVACIONES: 'warn', NO_APTO: 'danger' };
const TIPOS: Record<string, string> = {
  MAQUINARIA_PESADA: 'Maquinaria pesada',
  VEHICULO: 'Vehículo',
  HERRAMIENTA: 'Herramienta / equipo neumático',
  EQUIPO_ALTURAS: 'Equipo de trabajo en alturas',
};

/** Inspecciones preoperacionales con lista de chequeo y fotos. */
export default function Inspecciones() {
  const { can } = useAuth();
  const toast = useToast();
  const inspecciona = can('SGSST', 'SUPERVISOR', 'ALMACENISTA');
  const lista = useLoad(() => api.get<Inspeccion[]>('/Inspections'));
  const listas = useLoad(() => api.get<Record<string, Punto[]>>('/Inspections/Checklists'));
  const maquinas = useLoad(() => api.get<Maquina[]>('/Machinery').catch(() => [] as Maquina[]));
  const [ver, setVer] = useState<Inspeccion | null>(null);
  const [fotosUrl, setFotosUrl] = useState<string[]>([]);
  const [nueva, setNueva] = useState(false);
  const [tipo, setTipo] = useState('MAQUINARIA_PESADA');
  const [maquinaId, setMaquinaId] = useState('');
  const [equipo, setEquipo] = useState('');
  const [horometro, setHorometro] = useState('');
  const [obs, setObs] = useState('');
  const [resp, setResp] = useState<Record<string, { estado: string; observacion: string }>>({});
  const [archivos, setArchivos] = useState<File[]>([]);

  useEffect(() => {
    if (!ver) return;
    let vivas: string[] = [];
    Promise.all(ver.fotos.map((f) => api.blob(`/Inspection/${ver.id}/Photo/${f}`)))
      .then((u) => {
        vivas = u;
        setFotosUrl(u);
      })
      .catch(() => setFotosUrl([]));
    return () => {
      vivas.forEach((u) => URL.revokeObjectURL(u));
      setFotosUrl([]);
    };
  }, [ver]);

  const filas = lista.data ?? [];
  const hoy = new Date().toISOString().substring(0, 10);
  const puntos = listas.data?.[tipo] ?? [];

  const abrir = () => {
    setTipo('MAQUINARIA_PESADA');
    setMaquinaId('');
    setEquipo('');
    setHorometro('');
    setObs('');
    setResp({});
    setArchivos([]);
    setNueva(true);
  };

  return (
    <>
      <PageHeader title="Inspecciones preoperacionales" subtitle="Lista de chequeo diaria de maquinaria, vehículos y equipos antes de operar">
        {inspecciona && (
          <button className="btn primary" onClick={abrir}>
            <ClipboardCheck /> Nueva inspección
          </button>
        )}
      </PageHeader>
      <div className="grid grid-4 mb">
        <Stat label="Inspecciones hoy" value={filas.filter((i) => i.fechaHora.startsWith(hoy)).length} />
        <Stat label="No aptos (30 días)" value={filas.filter((i) => i.resultado === 'NO_APTO').length} />
        <Stat label="Con observaciones" value={filas.filter((i) => i.resultado === 'APTO_CON_OBSERVACIONES').length} />
        <Stat label="Total (30 días)" value={filas.length} />
      </div>
      <ErrorBox error={lista.error} />
      {lista.loading && !lista.data ? (
        <Spinner />
      ) : (
        <DataTable<Inspeccion>
          rows={filas}
          empty="No hay inspecciones en los últimos 30 días"
          search={(i) => `${i.equipo} ${i.inspector} ${i.operador ?? ''}`}
          searchPlaceholder="Buscar equipo o inspector"
          columns={[
            { key: 'f', label: 'Fecha', render: (i) => fmt.dateTime(i.fechaHora) },
            { key: 'e', label: 'Equipo', render: (i) => <><strong>{i.equipo}</strong><div className="muted">{TIPOS[i.tipo] ?? i.tipo}</div></> },
            { key: 'r', label: 'Resultado', render: (i) => <Badge value={i.resultado} tone={TONO[i.resultado]} /> },
            { key: 'fa', label: 'Fallas', num: true, render: (i) => (i.fallas ? `${i.fallas}${i.fallasCriticas ? ` (${i.fallasCriticas} crít.)` : ''}` : '—') },
            { key: 'i', label: 'Inspector', render: (i) => i.inspector },
            { key: 'a', label: '', render: (i) => <button className="btn small" onClick={() => setVer(i)}>Ver</button> },
          ]}
        />
      )}

      {ver && (
        <Modal title={`${ver.equipo} — ${fmt.dateTime(ver.fechaHora)}`} onClose={() => setVer(null)} wide>
          <div className="actions mb">
            <Badge value={ver.resultado} tone={TONO[ver.resultado]} />
            <span className="muted">Inspector: {ver.inspector}{ver.operador ? ` · Operador: ${ver.operador}` : ''}{ver.horometro ? ` · Horómetro: ${fmt.num(ver.horometro)}` : ''}</span>
          </div>
          <table className="table">
            <thead><tr><th>Punto</th><th>Estado</th><th>Observación</th></tr></thead>
            <tbody>
              {ver.items.map((it) => (
                <tr key={it.item}>
                  <td>{it.item}{it.critico && <span className="muted"> *</span>}</td>
                  <td><Badge value={it.estado === 'NA' ? 'N/A' : it.estado} tone={it.estado === 'OK' ? 'ok' : it.estado === 'FALLA' ? 'danger' : ''} /></td>
                  <td>{it.observacion ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {ver.observaciones && <p className="mt">Observaciones: {ver.observaciones}</p>}
          {fotosUrl.length > 0 && (
            <div className="grid grid-2 mt">
              {fotosUrl.map((u) => <img key={u} src={u} alt="Foto de la inspección" style={{ width: '100%', borderRadius: 10 }} />)}
            </div>
          )}
          <p className="muted mt">* Punto crítico: si falla, el equipo no puede operar y pasa a mantenimiento.</p>
        </Modal>
      )}

      {nueva && (
        <FormModal
          title="Nueva inspección preoperacional"
          submitLabel="Registrar inspección"
          wide
          onClose={() => setNueva(false)}
          onSubmit={async () => {
            const items = puntos.map((p) => ({ item: p.item, estado: resp[p.item]?.estado ?? '', observacion: resp[p.item]?.observacion ?? '' }));
            const fd = new FormData();
            if (maquinaId) fd.append('maquinariaId', maquinaId);
            else fd.append('equipo', equipo);
            fd.append('tipo', tipo);
            if (horometro) fd.append('horometro', horometro);
            if (obs) fd.append('observaciones', obs);
            fd.append('items', JSON.stringify(items));
            archivos.forEach((a) => fd.append('fotos', a));
            const r = await api.form<Inspeccion>('POST', '/Inspection', fd);
            if (r.resultado === 'NO_APTO') toast.error('Equipo NO APTO: quedó en mantenimiento y se avisó a almacén y supervisión');
            else toast.ok('Inspección registrada');
            setNueva(false);
            lista.reload();
          }}
        >
          {() => (
            <div className="form-grid">
              <Field label="Tipo de equipo *">
                <select value={tipo} onChange={(e) => { setTipo(e.target.value); setResp({}); }}>
                  {Object.entries(TIPOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </Field>
              <Field label="Equipo registrado">
                <select value={maquinaId} onChange={(e) => setMaquinaId(e.target.value)}>
                  <option value="">Otro (escribir)</option>
                  {maquinas.data?.map((m) => <option key={m.id} value={m.id}>{m.codigo} {m.nombre}</option>)}
                </select>
              </Field>
              {!maquinaId && <Field label="Equipo *"><input required value={equipo} onChange={(e) => setEquipo(e.target.value)} placeholder="Ej.: Camioneta ABC-123" /></Field>}
              <Field label="Horómetro / kilometraje"><input type="number" step="0.1" value={horometro} onChange={(e) => setHorometro(e.target.value)} /></Field>
              <div className="full">
                <table className="table">
                  <thead><tr><th>Punto de revisión</th><th>Estado</th></tr></thead>
                  <tbody>
                    {puntos.map((p) => {
                      const r = resp[p.item];
                      return (
                        <tr key={p.item}>
                          <td>
                            {p.item}{p.critico && <strong title="Crítico"> *</strong>}
                            {r?.estado === 'FALLA' && (
                              <input
                                className="mt"
                                required
                                placeholder="Describa la falla"
                                value={r.observacion}
                                onChange={(e) => setResp({ ...resp, [p.item]: { estado: 'FALLA', observacion: e.target.value } })}
                              />
                            )}
                          </td>
                          <td style={{ whiteSpace: 'nowrap' }}>
                            {['OK', 'FALLA', 'NA'].map((e) => (
                              <button
                                type="button"
                                key={e}
                                className={`btn small ${r?.estado === e ? (e === 'FALLA' ? 'danger' : 'primary') : ''}`}
                                onClick={() => setResp({ ...resp, [p.item]: { estado: e, observacion: r?.observacion ?? '' } })}
                              >
                                {e === 'NA' ? 'N/A' : e === 'OK' ? 'Bien' : 'Falla'}
                              </button>
                            ))}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <Field label={`Fotos${Object.values(resp).some((r) => r.estado === 'FALLA') ? ' * (obligatorias si hay fallas)' : ''}`} full>
                <input type="file" multiple accept=".jpg,.jpeg,.png" onChange={(e) => setArchivos(Array.from(e.target.files ?? []).slice(0, 10))} />
              </Field>
              <Field label="Observaciones" full><textarea rows={2} maxLength={1000} value={obs} onChange={(e) => setObs(e.target.value)} /></Field>
            </div>
          )}
        </FormModal>
      )}
    </>
  );
}
