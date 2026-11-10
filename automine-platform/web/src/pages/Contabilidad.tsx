import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Finanzas } from '../components/Charts';
import { ActionButton, Badge, DataTable, Field, FormModal, PageHeader, Spinner, Stat, Tabs, useForm, useLoad, useToast } from '../components/ui';
import { fmt, today } from '../format';
import type { TendenciaMes } from '../types';

interface Movimiento {
  id: number;
  tipo: string;
  fecha: string;
  concepto: string;
  categoria: string;
  valor: number;
  referencia: string | null;
  origen: string;
  registradoPor: string;
}
interface Linea {
  concepto: string;
  valor: number;
}
interface Balance {
  fechaCorte: string;
  activos: Linea[];
  totalActivos: number;
  pasivos: Linea[];
  totalPasivos: number;
  patrimonio: Linea[];
  totalPatrimonio: number;
}
interface Resultados {
  ingresos: Linea[];
  totalIngresos: number;
  gastos: Linea[];
  totalGastos: number;
  utilidad: number;
  margen: number;
}
interface Caja {
  saldo: number;
  tope: number;
  movimientos: { id: number; fecha: string; tipo: string; concepto: string; valor: number; saldo: number; soporte: string | null; responsable: string }[];
}

const CAT_INGRESO = ['Venta de mineral', 'Servicios', 'Rendimientos financieros', 'Otros ingresos'];
const CAT_GASTO = ['Combustibles y lubricantes', 'Explosivos y voladura', 'Servicios públicos', 'Arrendamiento de equipos', 'Regalías y contraprestaciones', 'Mantenimiento de maquinaria', 'Gastos generales', 'Impuestos'];

function Estado({ titulo, lineas, total }: { titulo: string; lineas: Linea[]; total: number }) {
  return (
    <table>
      <thead><tr><th>{titulo}</th><th className="num">Valor</th></tr></thead>
      <tbody>
        {lineas.map((l) => <tr key={l.concepto}><td>{l.concepto}</td><td className="num">{fmt.money(l.valor)}</td></tr>)}
        <tr><td><strong>Total {titulo.toLowerCase()}</strong></td><td className="num"><strong>{fmt.money(total)}</strong></td></tr>
      </tbody>
    </table>
  );
}

export default function Contabilidad() {
  const { can } = useAuth();
  const toast = useToast();
  const escribe = can('CONTADOR');
  const [tab, setTab] = useState<'resumen' | 'ingresos' | 'gastos' | 'caja' | 'balance'>('resumen');
  const [desde, setDesde] = useState(today().substring(0, 5) + '01-01');
  const [hasta, setHasta] = useState(today());
  const ingresos = useLoad(() => api.get<Movimiento[]>('/Income', { desde, hasta }), [desde, hasta]);
  const gastos = useLoad(() => api.get<Movimiento[]>('/Expense', { desde, hasta }), [desde, hasta]);
  const resultados = useLoad(() => api.get<Resultados>('/Accounting/IncomeStatement', { desde, hasta }), [desde, hasta]);
  const tendencia = useLoad(() => api.get<TendenciaMes[]>('/Accounting/Trend', { desde, hasta }), [desde, hasta]);
  const balance = useLoad(() => api.get<Balance>('/Accounting/Balance', { corte: hasta }), [hasta]);
  const caja = useLoad(() => api.get<Caja>('/PettyCash', { desde, hasta }), [desde, hasta]);
  const [modal, setModal] = useState<'INGRESO' | 'GASTO' | 'CAJA' | null>(null);
  const mf = useForm({ valor: '', concepto: '', fecha: today(), categoria: '', referencia: '' });
  const cf = useForm({ tipo: 'GASTO', fecha: today(), concepto: '', valor: '', soporte: '' });

  const recargar = () => { ingresos.reload(); gastos.reload(); resultados.reload(); tendencia.reload(); balance.reload(); caja.reload(); };

  const tablaMovs = (rows: Movimiento[] | null) => (
    <DataTable<Movimiento>
      rows={rows}
      search={(m) => `${m.concepto} ${m.categoria} ${m.referencia ?? ''} ${m.origen}`}
      columns={[
        { key: 'f', label: 'Fecha', render: (m) => fmt.date(m.fecha) },
        { key: 'c', label: 'Concepto', clamp: true, render: (m) => m.concepto },
        { key: 'ca', label: 'Categoría', render: (m) => m.categoria },
        { key: 'r', label: 'Referencia', render: (m) => m.referencia },
        { key: 'o', label: 'Origen', render: (m) => <Badge value={m.origen} tone={m.origen === 'MANUAL' ? '' : 'info'} /> },
        { key: 'v', label: 'Valor', num: true, render: (m) => fmt.money(m.valor) },
        { key: 'a', label: '', render: (m) => escribe && m.origen === 'MANUAL' && <ActionButton className="btn small danger" confirm="¿Anular el movimiento?" action={() => api.del(`/Accounting/Entry/${m.id}`)} onDone={recargar}>Anular</ActionButton> },
      ]}
    />
  );

  const r = resultados.data;
  return (
    <>
      <PageHeader title="Contabilidad" subtitle="Ingresos, gastos, caja menor, balance general y estado de resultados">
        <button className="btn" onClick={() => api.download('/Reports/contabilidad/excel', { desde, hasta }).catch((e) => toast.error(e.message))}>Excel</button>
        <button className="btn" onClick={() => api.download('/Reports/contabilidad/pdf', { desde, hasta }).catch((e) => toast.error(e.message))}>PDF</button>
        {escribe && <button className="btn" onClick={() => { mf.reset(); setModal('GASTO'); }}>Registrar gasto</button>}
        {escribe && <button className="btn primary" onClick={() => { mf.reset(); setModal('INGRESO'); }}>Registrar ingreso</button>}
      </PageHeader>
      <div className="toolbar">
        <Field label="Desde"><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></Field>
        <Field label="Hasta (corte)"><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></Field>
      </div>
      <div className="grid grid-4 mb">
        <Stat label="Ingresos" value={r ? fmt.money(r.totalIngresos) : '—'} />
        <Stat label="Gastos" value={r ? fmt.money(r.totalGastos) : '—'} />
        <Stat label="Utilidad" value={r ? fmt.money(r.utilidad) : '—'} hint={r ? `Margen ${fmt.num(r.margen)} %` : undefined} />
        <Stat label="Saldo caja menor" value={caja.data ? fmt.money(caja.data.saldo) : '—'} hint={caja.data ? `Tope ${fmt.money(caja.data.tope)}` : undefined} />
      </div>
      <Tabs
        tabs={[{ key: 'resumen', label: 'Estado de resultados' }, { key: 'ingresos', label: 'Ingresos' }, { key: 'gastos', label: 'Gastos' }, { key: 'caja', label: 'Caja menor' }, { key: 'balance', label: 'Balance general' }]}
        active={tab}
        onChange={setTab}
      />
      {tab === 'resumen' && (r ? (
        <div className="grid grid-2">
          <div className="card">
            <h3>Estado de resultados</h3>
            <div className="table-wrap">
              <Estado titulo="Ingresos" lineas={r.ingresos} total={r.totalIngresos} />
              <Estado titulo="Gastos" lineas={r.gastos} total={r.totalGastos} />
              <table><tbody><tr><td><strong>Utilidad del periodo</strong></td><td className={`num ${r.utilidad >= 0 ? 'up' : 'down'}`}><strong>{fmt.money(r.utilidad)}</strong></td></tr></tbody></table>
            </div>
          </div>
          <div className="card"><h3>Tendencia mensual</h3>{tendencia.data && <Finanzas data={tendencia.data} />}</div>
        </div>
      ) : <Spinner />)}
      {tab === 'ingresos' && tablaMovs(ingresos.data)}
      {tab === 'gastos' && (
        <>
          <p className="muted">Los pagos de nómina, la caja menor y los mantenimientos se registran aquí automáticamente.</p>
          {tablaMovs(gastos.data)}
        </>
      )}
      {tab === 'caja' && (
        <>
          {escribe && <div className="actions mb"><button className="btn" onClick={() => { cf.reset(); setModal('CAJA'); }}>Movimiento de caja menor</button></div>}
          <DataTable
            rows={caja.data?.movimientos}
            columns={[
              { key: 'f', label: 'Fecha', render: (c) => fmt.date(c.fecha) },
              { key: 't', label: 'Tipo', render: (c) => <Badge value={c.tipo} tone={c.tipo === 'GASTO' ? 'danger' : 'ok'} /> },
              { key: 'concepto', label: 'Concepto' },
              { key: 'soporte', label: 'Soporte' },
              { key: 'v', label: 'Valor', num: true, render: (c) => fmt.money(c.valor) },
              { key: 's', label: 'Saldo', num: true, render: (c) => <strong>{fmt.money(c.saldo)}</strong> },
              { key: 'responsable', label: 'Responsable' },
            ]}
          />
        </>
      )}
      {tab === 'balance' && (balance.data ? (
        <div className="card" style={{ maxWidth: 720 }}>
          <h3>Balance general al {fmt.date(balance.data.fechaCorte)}</h3>
          <div className="table-wrap">
            <Estado titulo="Activos" lineas={balance.data.activos} total={balance.data.totalActivos} />
            <Estado titulo="Pasivos" lineas={balance.data.pasivos} total={balance.data.totalPasivos} />
            <Estado titulo="Patrimonio" lineas={balance.data.patrimonio} total={balance.data.totalPatrimonio} />
          </div>
          <p className="muted mt" style={{ fontSize: 12 }}>
            Balance simplificado: efectivo según ingresos y gastos registrados, caja menor, inventario valorizado al costo promedio y nómina aprobada pendiente de pago.
          </p>
        </div>
      ) : <Spinner />)}

      {(modal === 'INGRESO' || modal === 'GASTO') && (
        <FormModal
          title={modal === 'INGRESO' ? 'Registrar ingreso' : 'Registrar gasto'}
          onClose={() => setModal(null)}
          onSubmit={async () => {
            await api.post(modal === 'INGRESO' ? '/Income' : '/Expense', { ...mf.values, valor: Number(mf.values.valor) });
            toast.ok('Movimiento registrado');
            setModal(null);
            recargar();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Valor *" error={err.valor}><input type="number" min={1} required {...mf.bind('valor')} /></Field>
              <Field label="Fecha *" error={err.fecha}><input type="date" max={today()} required {...mf.bind('fecha')} /></Field>
              <Field label="Concepto *" error={err.concepto} full><input required {...mf.bind('concepto')} /></Field>
              <Field label="Categoría">
                <input list="categorias" {...mf.bind('categoria')} />
                <datalist id="categorias">{(modal === 'INGRESO' ? CAT_INGRESO : CAT_GASTO).map((c) => <option key={c} value={c} />)}</datalist>
              </Field>
              <Field label="Referencia / factura"><input {...mf.bind('referencia')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {modal === 'CAJA' && (
        <FormModal
          title="Movimiento de caja menor"
          onClose={() => setModal(null)}
          onSubmit={async () => {
            await api.post('/PettyCash', { ...cf.values, valor: Number(cf.values.valor) });
            toast.ok('Movimiento de caja registrado');
            setModal(null);
            recargar();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Tipo"><select {...cf.bind('tipo')}><option value="GASTO">Gasto (sale de caja)</option><option value="REPOSICION">Reposición / apertura (entra a caja)</option></select></Field>
              <Field label="Fecha *"><input type="date" max={today()} required {...cf.bind('fecha')} /></Field>
              <Field label="Concepto *" error={err.concepto} full><input required {...cf.bind('concepto')} /></Field>
              <Field label="Valor *" error={err.valor}><input type="number" min={1} required {...cf.bind('valor')} /></Field>
              <Field label="Soporte (recibo)"><input {...cf.bind('soporte')} /></Field>
            </div>
          )}
        </FormModal>
      )}
    </>
  );
}
