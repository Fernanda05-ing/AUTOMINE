import { FileDown } from 'lucide-react';
import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, ErrorBox, PageHeader, Spinner, Stat, Tabs, useLoad, useToast } from '../components/ui';
import { fmt } from '../format';

interface Calculo {
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  desde: string;
  hasta: string;
  base: number;
  dias: number;
  valor: number;
  intereses: number;
  pagado: boolean;
}
interface Periodo {
  tipo: string;
  anio: number;
  periodo: number;
  desde: string;
  hasta: string;
  fechaLimite: string;
  total: number;
  totalIntereses: number;
  pendientes: number;
  detalle: Calculo[];
}
export interface Liquidacion {
  id: number;
  empleadoId: number;
  cedula: string;
  empleado: string;
  cargo: string;
  tipoContrato: string;
  fechaIngreso: string;
  fechaRetiro: string;
  motivo: string;
  observaciones: string | null;
  salarioBase: number;
  basePrestaciones: number;
  diasSalario: number;
  salarioPendiente: number;
  diasPrima: number;
  prima: number;
  diasCesantias: number;
  cesantias: number;
  interesesCesantias: number;
  diasVacaciones: number;
  vacaciones: number;
  indemnizacion: number;
  total: number;
  liquidadoPor: string;
}

type Vista = 'PRIMA' | 'CESANTIAS' | 'RETIROS';
const hoy = new Date();

/** Prestaciones sociales: prima de servicios, cesantías con intereses y liquidaciones de retiro. */
export default function Prestaciones() {
  const { can } = useAuth();
  const toast = useToast();
  const liquida = can('RRHH', 'CONTADOR');
  const [vista, setVista] = useState<Vista>('PRIMA');
  const [anio, setAnio] = useState(hoy.getFullYear());
  const [semestre, setSemestre] = useState(hoy.getMonth() < 6 ? 1 : 2);

  const periodo = useLoad(
    () => (vista === 'RETIROS' ? Promise.resolve(null) : api.get<Periodo>('/Benefits', { tipo: vista, anio, periodo: vista === 'PRIMA' ? semestre : undefined })),
    [vista, anio, semestre],
  );
  const retiros = useLoad(() => (vista === 'RETIROS' ? api.get<Liquidacion[]>('/Settlements') : Promise.resolve([])), [vista]);
  const p = periodo.data;

  return (
    <>
      <PageHeader title="Prestaciones sociales" subtitle="Prima de servicios, cesantías e intereses, y liquidaciones de retiro" />
      <Tabs<Vista>
        tabs={[
          { key: 'PRIMA', label: 'Prima de servicios' },
          { key: 'CESANTIAS', label: 'Cesantías e intereses' },
          { key: 'RETIROS', label: 'Liquidaciones de retiro' },
        ]}
        active={vista}
        onChange={setVista}
      />

      {vista !== 'RETIROS' && (
        <>
          <div className="toolbar mb">
            <select value={anio} onChange={(e) => setAnio(Number(e.target.value))} aria-label="Año">
              {[hoy.getFullYear() - 1, hoy.getFullYear(), hoy.getFullYear() + 1].map((a) => <option key={a} value={a}>{a}</option>)}
            </select>
            {vista === 'PRIMA' && (
              <select value={semestre} onChange={(e) => setSemestre(Number(e.target.value))} aria-label="Semestre">
                <option value={1}>Primer semestre (pago en junio)</option>
                <option value={2}>Segundo semestre (pago en diciembre)</option>
              </select>
            )}
            <div style={{ flex: 1 }} />
            {liquida && p && p.pendientes > 0 && (
              <ActionButton
                className="btn primary"
                action={() => api.post('/Benefits/Settle', { tipo: vista, anio, periodo: vista === 'PRIMA' ? semestre : 0 })}
                confirm={`¿Liquidar ${vista === 'PRIMA' ? 'la prima' : 'las cesantías e intereses'} de ${p.pendientes} trabajadores? Quedará registrado en su historial.`}
                success="Prestaciones liquidadas"
                onDone={periodo.reload}
              >
                Liquidar {p.pendientes} pendientes
              </ActionButton>
            )}
          </div>
          <ErrorBox error={periodo.error} />
          {periodo.loading && !p ? (
            <Spinner />
          ) : (
            p && (
              <>
                <div className="grid grid-4 mb">
                  <Stat label={vista === 'PRIMA' ? 'Total prima' : 'Total cesantías'} value={fmt.money(p.total)} />
                  {vista === 'CESANTIAS' && <Stat label="Total intereses (12 %)" value={fmt.money(p.totalIntereses)} />}
                  <Stat label="Trabajadores" value={p.detalle.length} />
                  <Stat label="Pendientes por liquidar" value={p.pendientes} />
                </div>
                <div className="alert info mb">
                  Período {fmt.date(p.desde)} – {fmt.date(p.hasta)}. Fecha límite: {p.fechaLimite}. Base = salario + auxilio de transporte (si gana hasta 2 SMMLV), en días comerciales de 360.
                </div>
                <DataTable<Calculo>
                  rows={p.detalle}
                  search={(c) => `${c.empleado} ${c.cedula} ${c.cargo}`}
                  searchPlaceholder="Buscar trabajador"
                  columns={[
                    { key: 'e', label: 'Trabajador', render: (c) => <><strong>{c.empleado}</strong><div className="muted">{c.cedula} · {c.cargo}</div></> },
                    { key: 'b', label: 'Base', num: true, render: (c) => fmt.money(c.base) },
                    { key: 'd', label: 'Días', num: true, render: (c) => c.dias },
                    { key: 'v', label: vista === 'PRIMA' ? 'Prima' : 'Cesantías', num: true, render: (c) => <strong>{fmt.money(c.valor)}</strong> },
                    ...(vista === 'CESANTIAS' ? [{ key: 'i', label: 'Intereses', num: true, render: (c: Calculo) => fmt.money(c.intereses) }] : []),
                    { key: 's', label: 'Estado', render: (c) => <Badge value={c.pagado ? 'LIQUIDADA' : 'PENDIENTE'} tone={c.pagado ? 'ok' : 'warn'} /> },
                  ]}
                />
              </>
            )
          )}
        </>
      )}

      {vista === 'RETIROS' && (
        <>
          <div className="alert info mb">Para liquidar a un trabajador, ábralo en <strong>Empleados</strong> y pulse <strong>Liquidar retiro</strong>.</div>
          <ErrorBox error={retiros.error} />
          <DataTable<Liquidacion>
            rows={retiros.data}
            empty="No hay liquidaciones de retiro"
            search={(l) => `${l.empleado} ${l.cedula}`}
            columns={[
              { key: 'e', label: 'Trabajador', render: (l) => <><strong>{l.empleado}</strong><div className="muted">{l.cedula} · {l.cargo}</div></> },
              { key: 'f', label: 'Retiro', render: (l) => fmt.date(l.fechaRetiro) },
              { key: 'm', label: 'Motivo', render: (l) => fmt.label(l.motivo) },
              { key: 't', label: 'Total', num: true, render: (l) => <strong>{fmt.money(l.total)}</strong> },
              {
                key: 'a',
                label: '',
                render: (l) => (
                  <button className="btn small" onClick={() => api.download(`/Settlement/${l.id}/Pdf`, undefined, `liquidacion-${l.cedula}.pdf`).catch((e) => toast.error(e.message))}>
                    <FileDown /> PDF
                  </button>
                ),
              },
            ]}
          />
        </>
      )}
    </>
  );
}
