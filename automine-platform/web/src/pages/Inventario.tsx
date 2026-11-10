import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, Field, FormModal, Modal, PageHeader, Stat, Tabs, useForm, useLoad, useToast } from '../components/ui';
import { daysAgo, fmt, today } from '../format';

interface Producto {
  id: number;
  codigo: string;
  nombre: string;
  tipo: string;
  unidad: string;
  cantidad: number;
  stockMinimo: number;
  costoUnitario: number;
  valorTotal: number;
  ubicacion: string | null;
  activo: boolean;
  bajoStock: boolean;
}
interface Movimiento {
  id: number;
  productoCodigo: string;
  producto: string;
  tipo: string;
  cantidad: number;
  saldo: number;
  costoUnitario: number;
  motivo: string;
  responsable: string;
  fecha: string;
}
interface Maquina {
  id: number;
  codigo: string;
  nombre: string;
  tipo: string;
  marca: string | null;
  modelo: string | null;
  serial: string | null;
  estado: string;
  horometro: number;
  ultimoMantenimiento: string | null;
  proximoMantenimiento: string | null;
  frenteId: number | null;
  observaciones: string | null;
}

const TIPOS = ['HERRAMIENTA', 'EQUIPO', 'INSUMO', 'EPP', 'REPUESTO'];

function Kardex({ producto, onClose }: { producto: Producto; onClose: () => void }) {
  const toast = useToast();
  const [desde, setDesde] = useState(daysAgo(90));
  const [hasta, setHasta] = useState(today());
  const k = useLoad(() => api.get<Movimiento[]>(`/Inventory/${producto.id}/Kardex`, { desde, hasta }), [desde, hasta]);
  return (
    <Modal title={`Kardex ${producto.codigo} · ${producto.nombre}`} onClose={onClose} wide>
      <div className="toolbar">
        <Field label="Desde"><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></Field>
        <Field label="Hasta"><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></Field>
        <button className="btn" onClick={() => api.download('/Reports/inventario/excel', { desde, hasta }).catch((e) => toast.error(e.message))}>Excel</button>
      </div>
      <DataTable<Movimiento>
        rows={k.data}
        columns={[
          { key: 'f', label: 'Fecha', render: (m) => fmt.dateTime(m.fecha) },
          { key: 't', label: 'Tipo', render: (m) => <Badge value={m.tipo} tone={m.tipo === 'ENTRADA' ? 'ok' : m.tipo === 'SALIDA' ? 'danger' : 'info'} /> },
          { key: 'c', label: 'Cantidad', num: true, render: (m) => fmt.num(m.cantidad) },
          { key: 's', label: 'Saldo', num: true, render: (m) => <strong>{fmt.num(m.saldo)}</strong> },
          { key: 'cu', label: 'Costo unit.', num: true, render: (m) => fmt.money(m.costoUnitario) },
          { key: 'm', label: 'Motivo', clamp: true, render: (m) => m.motivo },
          { key: 'r', label: 'Responsable', render: (m) => m.responsable },
        ]}
      />
    </Modal>
  );
}

export default function Inventario() {
  const { can } = useAuth();
  const toast = useToast();
  const escribe = can('ALMACENISTA');
  const [tab, setTab] = useState<'stock' | 'movimientos' | 'maquinaria'>('stock');
  const [q, setQ] = useState('');
  const [tipo, setTipo] = useState('');
  const productos = useLoad(() => api.get<Producto[]>('/Inventory', { q, tipo }), [tipo]);
  const alertas = useLoad(() => api.get<Producto[]>('/Inventory/Alerts'));
  const movimientos = useLoad(() => api.get<Movimiento[]>('/Inventory/Movements', { limite: 100 }));
  const maquinas = useLoad(() => api.get<Maquina[]>('/Machinery'));
  const [prodForm, setProdForm] = useState<{ p?: Producto } | null>(null);
  const [movForm, setMovForm] = useState<Producto | null>(null);
  const [kardex, setKardex] = useState<Producto | null>(null);
  const [maqForm, setMaqForm] = useState<{ m?: Maquina } | null>(null);
  const [mant, setMant] = useState<Maquina | null>(null);
  const pf = useForm({ codigo: '', nombre: '', tipo: 'HERRAMIENTA', unidad: 'und', cantidad: '0', stockMinimo: '1', costoUnitario: '0', ubicacion: '' });
  const mf = useForm({ tipo: 'ENTRADA', cantidad: '', costoUnitario: '', motivo: '', responsable: '' });
  const qf = useForm({ codigo: '', nombre: '', tipo: '', marca: '', modelo: '', serial: '', estado: 'OPERATIVA', horometro: '0', proximoMantenimiento: '', observaciones: '' });
  const tf = useForm({ fecha: today(), descripcion: '', costo: '', horometro: '', proximoMantenimiento: '' });
  const valor = (productos.data ?? []).reduce((s, p) => s + (p.activo ? p.valorTotal : 0), 0);
  const recargar = () => { productos.reload(); alertas.reload(); movimientos.reload(); };

  return (
    <>
      <PageHeader title="Inventario" subtitle="Herramientas, equipos, insumos, EPP y maquinaria con kardex y alertas de stock mínimo">
        <button className="btn" onClick={() => api.download('/Reports/inventario/excel', { desde: daysAgo(30), hasta: today() }).catch((e) => toast.error(e.message))}>Excel</button>
        <button className="btn" onClick={() => api.download('/Reports/inventario/pdf', { desde: daysAgo(30), hasta: today() }).catch((e) => toast.error(e.message))}>PDF</button>
        {escribe && tab !== 'maquinaria' && <button className="btn primary" onClick={() => { pf.reset(); setProdForm({}); }}>Registrar producto</button>}
        {escribe && tab === 'maquinaria' && <button className="btn primary" onClick={() => { qf.reset(); setMaqForm({}); }}>Registrar máquina</button>}
      </PageHeader>
      <div className="grid grid-3 mb">
        <Stat label="Productos" value={productos.data?.length ?? '—'} />
        <Stat label="Valor del inventario" value={fmt.money(valor)} />
        <Stat label="Alertas de stock mínimo" value={alertas.data?.length ?? '—'} />
      </div>
      {alertas.data && alertas.data.length > 0 && (
        <div className="alert warn">
          <strong>Stock bajo:</strong> {alertas.data.map((p) => `${p.nombre} (${fmt.num(p.cantidad)} ${p.unidad})`).join(' · ')}
        </div>
      )}
      <Tabs tabs={[{ key: 'stock', label: 'Existencias' }, { key: 'movimientos', label: 'Últimos movimientos' }, { key: 'maquinaria', label: 'Maquinaria' }]} active={tab} onChange={setTab} />
      {tab === 'stock' && (
        <>
          <form className="toolbar" onSubmit={(e) => { e.preventDefault(); productos.reload(); }}>
            <Field label="Buscar"><input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Código o nombre" /></Field>
            <Field label="Tipo"><select value={tipo} onChange={(e) => setTipo(e.target.value)}><option value="">Todos</option>{TIPOS.map((t) => <option key={t} value={t}>{fmt.label(t)}</option>)}</select></Field>
            <button className="btn">Buscar</button>
          </form>
          <DataTable<Producto>
            rows={productos.data}
            columns={[
              { key: 'codigo', label: 'Código' },
              { key: 'n', label: 'Producto', render: (p) => <><strong>{p.nombre}</strong>{!p.activo && <> <Badge value="INACTIVO" /></>}</> },
              { key: 't', label: 'Tipo', render: (p) => fmt.label(p.tipo) },
              { key: 'c', label: 'Existencia', num: true, render: (p) => <>{fmt.num(p.cantidad)} {p.unidad} {p.bajoStock && p.activo && <Badge value="BAJO STOCK" tone="danger" />}</> },
              { key: 'm', label: 'Mínimo', num: true, render: (p) => fmt.num(p.stockMinimo) },
              { key: 'cu', label: 'Costo unit.', num: true, render: (p) => fmt.money(p.costoUnitario) },
              { key: 'v', label: 'Valor', num: true, render: (p) => fmt.money(p.valorTotal) },
              { key: 'ubicacion', label: 'Ubicación' },
              {
                key: 'a',
                label: '',
                render: (p) => (
                  <div className="actions">
                    <button className="btn small" onClick={() => setKardex(p)}>Kardex</button>
                    {escribe && p.activo && <button className="btn small" onClick={() => { mf.reset(); setMovForm(p); }}>Movimiento</button>}
                    {escribe && (
                      <button className="btn small" onClick={() => { pf.setValues({ codigo: p.codigo, nombre: p.nombre, tipo: p.tipo, unidad: p.unidad, cantidad: '0', stockMinimo: String(p.stockMinimo), costoUnitario: String(p.costoUnitario), ubicacion: p.ubicacion ?? '' }); setProdForm({ p }); }}>
                        Editar
                      </button>
                    )}
                    {escribe && <ActionButton action={() => api.put(`/Inventory/${p.id}/Active/${!p.activo}`)} onDone={recargar}>{p.activo ? 'Desactivar' : 'Activar'}</ActionButton>}
                  </div>
                ),
              },
            ]}
          />
        </>
      )}
      {tab === 'movimientos' && (
        <DataTable<Movimiento>
          rows={movimientos.data}
          search={(m) => `${m.productoCodigo} ${m.producto} ${m.tipo} ${m.motivo} ${m.responsable}`}
          columns={[
            { key: 'f', label: 'Fecha', render: (m) => fmt.dateTime(m.fecha) },
            { key: 'p', label: 'Producto', render: (m) => `${m.productoCodigo} · ${m.producto}` },
            { key: 't', label: 'Tipo', render: (m) => <Badge value={m.tipo} tone={m.tipo === 'ENTRADA' ? 'ok' : m.tipo === 'SALIDA' ? 'danger' : 'info'} /> },
            { key: 'c', label: 'Cantidad', num: true, render: (m) => fmt.num(m.cantidad) },
            { key: 's', label: 'Saldo', num: true, render: (m) => fmt.num(m.saldo) },
            { key: 'm', label: 'Motivo', clamp: true, render: (m) => m.motivo },
            { key: 'r', label: 'Responsable', render: (m) => m.responsable },
          ]}
        />
      )}
      {tab === 'maquinaria' && (
        <DataTable<Maquina>
          rows={maquinas.data}
          search={(m) => `${m.codigo} ${m.nombre} ${m.tipo} ${m.estado}`}
          columns={[
            { key: 'codigo', label: 'Código' },
            { key: 'n', label: 'Máquina', render: (m) => <><strong>{m.nombre}</strong><div className="muted">{[m.marca, m.modelo, m.serial].filter(Boolean).join(' · ')}</div></> },
            { key: 'tipo', label: 'Tipo' },
            { key: 'h', label: 'Horómetro', num: true, render: (m) => fmt.num(m.horometro) },
            { key: 'u', label: 'Último mant.', render: (m) => fmt.date(m.ultimoMantenimiento) || '—' },
            {
              key: 'p',
              label: 'Próximo mant.',
              render: (m) => {
                const vencido = m.proximoMantenimiento && m.proximoMantenimiento < today();
                return m.proximoMantenimiento ? <span className={vencido ? 'down' : ''}>{fmt.date(m.proximoMantenimiento)}{vencido ? ' (vencido)' : ''}</span> : '—';
              },
            },
            { key: 'e', label: 'Estado', render: (m) => <Badge value={m.estado} /> },
            {
              key: 'a',
              label: '',
              render: (m) =>
                escribe && (
                  <div className="actions">
                    <button className="btn small" onClick={() => { tf.setValues({ fecha: today(), descripcion: '', costo: '', horometro: String(m.horometro), proximoMantenimiento: '' }); setMant(m); }}>Mantenimiento</button>
                    <button className="btn small" onClick={() => { qf.setValues({ codigo: m.codigo, nombre: m.nombre, tipo: m.tipo, marca: m.marca ?? '', modelo: m.modelo ?? '', serial: m.serial ?? '', estado: m.estado, horometro: String(m.horometro), proximoMantenimiento: m.proximoMantenimiento ?? '', observaciones: m.observaciones ?? '' }); setMaqForm({ m }); }}>Editar</button>
                  </div>
                ),
            },
          ]}
        />
      )}

      {kardex && <Kardex producto={kardex} onClose={() => setKardex(null)} />}
      {prodForm && (
        <FormModal
          title={prodForm.p ? `Editar ${prodForm.p.codigo}` : 'Registrar producto'}
          onClose={() => setProdForm(null)}
          onSubmit={async () => {
            const v = pf.values;
            const body = { ...v, cantidad: Number(v.cantidad), stockMinimo: Number(v.stockMinimo), costoUnitario: Number(v.costoUnitario) };
            if (prodForm.p) await api.put(`/Inventory/${prodForm.p.id}`, body);
            else await api.post('/Inventory', body);
            toast.ok('Producto guardado');
            setProdForm(null);
            recargar();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Código *" error={err.codigo}><input required {...pf.bind('codigo')} /></Field>
              <Field label="Tipo *"><select {...pf.bind('tipo')}>{TIPOS.map((t) => <option key={t} value={t}>{fmt.label(t)}</option>)}</select></Field>
              <Field label="Nombre *" error={err.nombre} full><input required {...pf.bind('nombre')} /></Field>
              <Field label="Unidad *"><input required {...pf.bind('unidad')} placeholder="und, gal, kg, m" /></Field>
              {!prodForm.p && <Field label="Cantidad inicial"><input type="number" min={0} step="0.01" {...pf.bind('cantidad')} /></Field>}
              <Field label="Stock mínimo *" error={err.stockMinimo}><input type="number" min={0} step="0.01" required {...pf.bind('stockMinimo')} /></Field>
              <Field label="Costo unitario *" error={err.costoUnitario}><input type="number" min={0} required {...pf.bind('costoUnitario')} /></Field>
              <Field label="Ubicación"><input {...pf.bind('ubicacion')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {movForm && (
        <FormModal
          title={`Movimiento · ${movForm.nombre} (existencia ${fmt.num(movForm.cantidad)} ${movForm.unidad})`}
          onClose={() => setMovForm(null)}
          onSubmit={async () => {
            const v = mf.values;
            await api.post('/Inventory/Movements', { productoId: movForm.id, tipo: v.tipo, cantidad: Number(v.cantidad), costoUnitario: v.costoUnitario ? Number(v.costoUnitario) : null, motivo: v.motivo, responsable: v.responsable });
            toast.ok('Movimiento registrado');
            setMovForm(null);
            recargar();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Tipo">
                <select {...mf.bind('tipo')}>
                  <option value="ENTRADA">Entrada (compra, devolución)</option>
                  <option value="SALIDA">Salida (consumo, prestamo)</option>
                  <option value="AJUSTE">Ajuste por conteo físico</option>
                </select>
              </Field>
              <Field label={mf.values.tipo === 'AJUSTE' ? 'Cantidad contada *' : 'Cantidad *'} error={err.cantidad}><input type="number" min={0} step="0.01" required {...mf.bind('cantidad')} /></Field>
              {mf.values.tipo === 'ENTRADA' && <Field label="Costo unitario de compra"><input type="number" min={0} {...mf.bind('costoUnitario')} /></Field>}
              <Field label="Responsable / recibe"><input {...mf.bind('responsable')} /></Field>
              <Field label="Motivo *" error={err.motivo} full><input required {...mf.bind('motivo')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {maqForm && (
        <FormModal
          title={maqForm.m ? `Editar ${maqForm.m.codigo}` : 'Registrar máquina'}
          onClose={() => setMaqForm(null)}
          onSubmit={async () => {
            const v = qf.values;
            const body = { ...v, horometro: Number(v.horometro), proximoMantenimiento: v.proximoMantenimiento || null };
            if (maqForm.m) await api.put(`/Machinery/${maqForm.m.id}`, body);
            else await api.post('/Machinery', body);
            toast.ok('Máquina guardada');
            setMaqForm(null);
            maquinas.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Código *" error={err.codigo}><input required {...qf.bind('codigo')} /></Field>
              <Field label="Tipo *"><input required {...qf.bind('tipo')} placeholder="Cargador, perforadora..." /></Field>
              <Field label="Nombre *" full><input required {...qf.bind('nombre')} /></Field>
              <Field label="Marca"><input {...qf.bind('marca')} /></Field>
              <Field label="Modelo"><input {...qf.bind('modelo')} /></Field>
              <Field label="Serial"><input {...qf.bind('serial')} /></Field>
              <Field label="Estado"><select {...qf.bind('estado')}>{['OPERATIVA', 'MANTENIMIENTO', 'FUERA_SERVICIO'].map((s) => <option key={s} value={s}>{fmt.label(s)}</option>)}</select></Field>
              <Field label="Horómetro"><input type="number" min={0} step="0.1" {...qf.bind('horometro')} /></Field>
              <Field label="Próximo mantenimiento"><input type="date" {...qf.bind('proximoMantenimiento')} /></Field>
              <Field label="Observaciones" full><input {...qf.bind('observaciones')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {mant && (
        <FormModal
          title={`Mantenimiento · ${mant.codigo}`}
          onClose={() => setMant(null)}
          onSubmit={async () => {
            const v = tf.values;
            await api.post(`/Machinery/${mant.id}/Maintenance`, { fecha: v.fecha, descripcion: v.descripcion, costo: v.costo ? Number(v.costo) : null, horometro: v.horometro ? Number(v.horometro) : null, proximoMantenimiento: v.proximoMantenimiento || null });
            toast.ok('Mantenimiento registrado');
            setMant(null);
            maquinas.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Fecha *"><input type="date" max={today()} required {...tf.bind('fecha')} /></Field>
              <Field label="Costo (se registra como gasto)"><input type="number" min={0} {...tf.bind('costo')} /></Field>
              <Field label="Descripción *" error={err.descripcion} full><input required {...tf.bind('descripcion')} /></Field>
              <Field label="Horómetro actual"><input type="number" min={0} step="0.1" {...tf.bind('horometro')} /></Field>
              <Field label="Próximo mantenimiento"><input type="date" {...tf.bind('proximoMantenimiento')} /></Field>
            </div>
          )}
        </FormModal>
      )}
    </>
  );
}
