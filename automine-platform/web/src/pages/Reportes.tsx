import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { DataTable, Field, PageHeader, useLoad, useToast } from '../components/ui';
import { firstOfMonth, fmt, today } from '../format';
import type { Kpi, Periodo } from '../types';

const REPORTES = [
  { tipo: 'mensual', titulo: 'Informe gerencial mensual', desc: 'KPI, producción por frente, resultados financieros y SG-SST del mes de la fecha inicial.', roles: ['GERENTE', 'CONTADOR'] },
  { tipo: 'produccion', titulo: 'Producción minera', desc: 'Indicadores, cumplimiento por frente y registros por turno.', roles: ['GERENTE', 'SUPERVISOR'] },
  { tipo: 'contabilidad', titulo: 'Contable', desc: 'Estado de resultados, balance general y movimientos.', roles: ['GERENTE', 'CONTADOR'] },
  { tipo: 'nomina', titulo: 'Nómina del periodo', desc: 'Liquidaciones y totales del periodo seleccionado.', roles: ['GERENTE', 'CONTADOR'] },
  { tipo: 'inventario', titulo: 'Inventario', desc: 'Existencias, movimientos del rango y maquinaria.', roles: ['GERENTE', 'ALMACENISTA', 'CONTADOR'] },
  { tipo: 'sgsst', titulo: 'SG-SST', desc: 'Accidentes, incidentes, EPP, capacitaciones y matriz de riesgos.', roles: ['GERENTE', 'SGSST'] },
  { tipo: 'empleados', titulo: 'Personal', desc: 'Listado de empleados con cargo, contrato, salario y estado.', roles: ['GERENTE', 'RRHH'] },
];

/** RF046-RF050: exportacion a PDF/Excel, reportes mensuales e indicadores KPI. */
export default function Reportes() {
  const { can } = useAuth();
  const toast = useToast();
  const [desde, setDesde] = useState(firstOfMonth());
  const [hasta, setHasta] = useState(today());
  const [periodoId, setPeriodoId] = useState('');
  const verKpi = can('GERENTE', 'CONTADOR');
  const periodos = useLoad(() => (can('GERENTE', 'CONTADOR') ? api.get<Periodo[]>('/Payroll/Periods') : Promise.resolve([])));
  const [anio, mes] = desde.split('-').map(Number);
  const kpis = useLoad(() => (verKpi ? api.get<Kpi[]>('/Kpis', { anio, mes }) : Promise.resolve([])), [anio, mes]);
  const [busy, setBusy] = useState<string | null>(null);

  const descargar = async (tipo: string, formato: string) => {
    setBusy(tipo + formato);
    try {
      await api.download(`/Reports/${tipo}/${formato}`, { desde, hasta, periodoId: tipo === 'nomina' ? periodoId : undefined });
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <PageHeader title="Reportes" subtitle="Exporte reportes en PDF o Excel por rango de fechas" />
      <div className="toolbar">
        <Field label="Desde"><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></Field>
        <Field label="Hasta"><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></Field>
        {can('GERENTE', 'CONTADOR') && (
          <Field label="Periodo de nómina">
            <select value={periodoId} onChange={(e) => setPeriodoId(e.target.value)}>
              <option value="">Seleccione...</option>
              {periodos.data?.map((p) => <option key={p.id} value={p.id}>{fmt.mes(p.mes)} {p.anio}</option>)}
            </select>
          </Field>
        )}
      </div>
      <div className="grid grid-3">
        {REPORTES.filter((r) => can(...r.roles)).map((r) => (
          <div className="card" key={r.tipo} style={{ marginTop: 0 }}>
            <h3 style={{ marginBottom: 4 }}>{r.titulo}</h3>
            <p className="muted" style={{ marginTop: 0, minHeight: 42 }}>{r.desc}</p>
            <div className="actions">
              <button className="btn primary small" disabled={!!busy || (r.tipo === 'nomina' && !periodoId)} onClick={() => descargar(r.tipo, 'pdf')}>{busy === r.tipo + 'pdf' ? 'Generando...' : 'PDF'}</button>
              <button className="btn small" disabled={!!busy || (r.tipo === 'nomina' && !periodoId)} onClick={() => descargar(r.tipo, 'excel')}>{busy === r.tipo + 'excel' ? 'Generando...' : 'Excel'}</button>
            </div>
          </div>
        ))}
      </div>
      {verKpi && (
        <div className="card mt">
          <h3>Indicadores clave de {fmt.mes(mes)} {anio}</h3>
          <DataTable<Kpi>
            rows={kpis.data}
            rowKey={(k) => k.clave}
            columns={[
              { key: 'nombre', label: 'Indicador' },
              { key: 'v', label: 'Valor', num: true, render: (k) => (k.unidad.startsWith('COP') ? fmt.money(k.valor) : `${fmt.num(k.valor)} ${k.unidad}`) },
              { key: 'a', label: 'Mes anterior', num: true, render: (k) => (k.anterior === null ? '—' : k.unidad.startsWith('COP') ? fmt.money(k.anterior) : fmt.num(k.anterior)) },
              { key: 'var', label: 'Variación', num: true, render: (k) => (k.variacion === null ? '—' : <span className={k.variacion >= 0 ? 'up' : 'down'}>{k.variacion >= 0 ? '+' : ''}{fmt.num(k.variacion)} %</span>) },
            ]}
          />
        </div>
      )}
    </>
  );
}
