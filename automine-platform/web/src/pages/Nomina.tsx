import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, Field, FormModal, PageHeader, Stat, useForm, useLoad, useToast } from '../components/ui';
import { fmt } from '../format';
import type { EmpleadoOpcion, Nomina as NominaT, Periodo } from '../types';

const ahora = new Date();

function NovedadesForm({ nomina, onClose, onSaved }: { nomina: NominaT; onClose: () => void; onSaved: () => void }) {
  const f = useForm({
    diasTrabajados: String(nomina.diasTrabajados),
    horasExtraDiurnas: String(nomina.horasExtraDiurnas),
    horasExtraNocturnas: String(nomina.horasExtraNocturnas),
    horasExtraDominicales: String(nomina.horasExtraDominicales),
    bonificaciones: String(nomina.bonificaciones),
    otrosDescuentos: String(nomina.otrosDescuentos),
    detalleDescuentos: nomina.detalleDescuentos ?? '',
  });
  return (
    <FormModal
      title={`Novedades de ${nomina.empleado}`}
      onClose={onClose}
      onSubmit={async () => {
        const v = f.values;
        await api.put(`/Payroll/Detail/${nomina.id}`, {
          diasTrabajados: Number(v.diasTrabajados),
          horasExtraDiurnas: Number(v.horasExtraDiurnas),
          horasExtraNocturnas: Number(v.horasExtraNocturnas),
          horasExtraDominicales: Number(v.horasExtraDominicales),
          bonificaciones: Number(v.bonificaciones),
          otrosDescuentos: Number(v.otrosDescuentos),
          detalleDescuentos: v.detalleDescuentos,
        });
        onSaved();
      }}
    >
      {(err) => (
        <div className="form-grid">
          <Field label="Días trabajados (0-30)" error={err.diasTrabajados}><input type="number" min={0} max={30} {...f.bind('diasTrabajados')} /></Field>
          <Field label="Bonificaciones ($)"><input type="number" min={0} {...f.bind('bonificaciones')} /></Field>
          <Field label="Horas extra diurnas"><input type="number" min={0} step="0.5" {...f.bind('horasExtraDiurnas')} /></Field>
          <Field label="Horas extra nocturnas"><input type="number" min={0} step="0.5" {...f.bind('horasExtraNocturnas')} /></Field>
          <Field label="Horas extra dominicales/festivas"><input type="number" min={0} step="0.5" {...f.bind('horasExtraDominicales')} /></Field>
          <Field label="Otros descuentos ($)" error={err.otrosDescuentos}><input type="number" min={0} {...f.bind('otrosDescuentos')} /></Field>
          <Field label="Detalle de descuentos" full><input placeholder="Ej: prestamo, libranza, embargo" {...f.bind('detalleDescuentos')} /></Field>
          <p className="muted full" style={{ margin: 0 }}>Salud, pensión, fondo de solidaridad y auxilio de transporte se calculan automáticamente.</p>
        </div>
      )}
    </FormModal>
  );
}

function DetallePeriodo({ periodo, onBack, onChanged }: { periodo: Periodo; onBack: () => void; onChanged: () => void }) {
  const { user, can } = useAuth();
  const toast = useToast();
  const detalle = useLoad(() => api.get<NominaT[]>(`/Payroll/Period/${periodo.id}/Detail`), [periodo.id]);
  const [editar, setEditar] = useState<NominaT | null>(null);
  const abierto = periodo.estado === 'ABIERTO';
  const edita = can('CONTADOR') && abierto;

  return (
    <>
      <PageHeader title={`Nómina ${fmt.mes(periodo.mes)} ${periodo.anio}`} subtitle={periodo.aprobadoPor ? `Aprobada por ${periodo.aprobadoPor} el ${fmt.dateTime(periodo.fechaAprobacion)}` : 'Periodo abierto: puede ajustar novedades antes de aprobar'}>
        <button className="btn" onClick={onBack}>← Periodos</button>
        <button className="btn" onClick={() => api.download('/Reports/nomina/excel', { periodoId: periodo.id }).catch((e) => toast.error(e.message))}>Excel</button>
        <button className="btn" onClick={() => api.download('/Reports/nomina/pdf', { periodoId: periodo.id }).catch((e) => toast.error(e.message))}>PDF</button>
        {abierto && user?.rol === 'CONTADOR' && (
          <ActionButton className="btn primary" confirm="Al aprobar, el periodo queda bloqueado y listo para consignar. ¿Continuar?" action={() => api.put(`/Payroll/Period/${periodo.id}/Approve`)} success="Nómina aprobada" onDone={onChanged}>
            Aprobar nómina
          </ActionButton>
        )}
      </PageHeader>
      {abierto && user?.rol !== 'CONTADOR' && <div className="alert info">Solo el rol Contador puede aprobar la nómina (RN005).</div>}
      <div className="grid grid-4 mb">
        <Stat label="Liquidaciones" value={periodo.liquidaciones} />
        <Stat label="Total devengado" value={fmt.money(periodo.totalDevengado)} />
        <Stat label="Total descuentos" value={fmt.money(periodo.totalDescuentos)} />
        <Stat label="Neto a pagar" value={fmt.money(periodo.totalNeto)} />
      </div>
      <DataTable<NominaT>
        rows={detalle.data}
        columns={[
          { key: 'e', label: 'Empleado', render: (n) => <><strong>{n.empleado}</strong><div className="muted">{n.cedula} · {n.cargo}</div></> },
          { key: 'd', label: 'Días', num: true, render: (n) => n.diasTrabajados },
          { key: 's', label: 'Salario', num: true, render: (n) => fmt.money(n.salarioDevengado) },
          { key: 'x', label: 'Extras', num: true, render: (n) => fmt.money(n.valorHorasExtra) },
          { key: 'b', label: 'Bonif.', num: true, render: (n) => fmt.money(n.bonificaciones) },
          { key: 'a', label: 'Aux. transp.', num: true, render: (n) => fmt.money(n.auxilioTransporte) },
          { key: 'dv', label: 'Devengado', num: true, render: (n) => fmt.money(n.totalDevengado) },
          { key: 'ds', label: 'Descuentos', num: true, render: (n) => fmt.money(n.totalDescuentos) },
          { key: 'n', label: 'Neto', num: true, render: (n) => <strong>{fmt.money(n.netoPagar)}</strong> },
          { key: 'st', label: 'Estado', render: (n) => <Badge value={n.estado} /> },
          {
            key: 'ac',
            label: '',
            render: (n) => (
              <div className="actions">
                {edita && <button className="btn small" onClick={() => setEditar(n)}>Novedades</button>}
                {edita && <ActionButton className="btn small danger" confirm={`¿Eliminar la liquidación de ${n.empleado}?`} action={() => api.del(`/Payroll/Detail/${n.id}`)} onDone={() => { detalle.reload(); onChanged(); }}>Quitar</ActionButton>}
                <button className="btn small" onClick={() => api.download(`/Payroll/Detail/${n.id}/Payslip`).catch((e) => toast.error(e.message))}>PDF</button>
              </div>
            ),
          },
        ]}
      />
      {editar && <NovedadesForm nomina={editar} onClose={() => setEditar(null)} onSaved={() => { setEditar(null); toast.ok('Liquidación recalculada'); detalle.reload(); onChanged(); }} />}
    </>
  );
}

export default function Nomina() {
  const { can } = useAuth();
  const toast = useToast();
  const periodos = useLoad(() => api.get<Periodo[]>('/Payroll/Periods'));
  const [abierto, setAbierto] = useState<Periodo | null>(null);
  const [modal, setModal] = useState<'generar' | 'liquidar' | null>(null);
  const gen = useForm({ anio: String(ahora.getFullYear()), mes: String(ahora.getMonth() + 1) });
  const liq = useForm({ empleadoId: '', anio: String(ahora.getFullYear()), mes: String(ahora.getMonth() + 1), diasTrabajados: '30', horasExtraDiurnas: '0', horasExtraNocturnas: '0', horasExtraDominicales: '0', bonificaciones: '0', otrosDescuentos: '0', detalleDescuentos: '' });
  const empleados = useLoad(() => (can('CONTADOR') ? api.get<EmpleadoOpcion[]>('/Employees/Options') : Promise.resolve([])));
  const liquida = can('CONTADOR');

  if (abierto) {
    return (
      <DetallePeriodo
        periodo={abierto}
        onBack={() => { setAbierto(null); periodos.reload(); }}
        onChanged={async () => { setAbierto(await api.get<Periodo>(`/Payroll/Period/${abierto.id}`)); }}
      />
    );
  }

  return (
    <>
      <PageHeader title="Nómina" subtitle="Liquidación mensual con salario, horas extra, auxilio, deducciones de ley y neto a pagar">
        {liquida && <button className="btn" onClick={() => setModal('liquidar')}>Liquidar un empleado</button>}
        {liquida && <button className="btn primary" onClick={() => setModal('generar')}>Generar nómina del mes</button>}
      </PageHeader>
      <DataTable<Periodo>
        rows={periodos.data}
        columns={[
          { key: 'p', label: 'Periodo', render: (p) => <button className="btn link" onClick={() => setAbierto(p)}>{fmt.mes(p.mes)} {p.anio}</button> },
          { key: 'l', label: 'Liquidaciones', num: true, render: (p) => p.liquidaciones },
          { key: 'd', label: 'Devengado', num: true, render: (p) => fmt.money(p.totalDevengado) },
          { key: 'x', label: 'Descuentos', num: true, render: (p) => fmt.money(p.totalDescuentos) },
          { key: 'n', label: 'Neto', num: true, render: (p) => <strong>{fmt.money(p.totalNeto)}</strong> },
          { key: 'e', label: 'Estado', render: (p) => <Badge value={p.estado} /> },
          { key: 'a', label: 'Aprobada por', render: (p) => p.aprobadoPor ?? '—' },
          { key: 'v', label: '', render: (p) => <button className="btn small" onClick={() => setAbierto(p)}>Ver detalle</button> },
        ]}
      />
      {modal === 'generar' && (
        <FormModal
          title="Generar nómina del mes"
          submitLabel="Generar"
          onClose={() => setModal(null)}
          onSubmit={async () => {
            const r = await api.post<{ creadas: number; omitidas: number }>('/Payroll/Generate', { anio: Number(gen.values.anio), mes: Number(gen.values.mes) });
            toast.ok(`Se liquidaron ${r.creadas} empleados (${r.omitidas} ya tenian nómina en el periodo)`);
            setModal(null);
            periodos.reload();
          }}
        >
          {() => (
            <>
              <p className="muted">Se liquida a todos los empleados activos con 30 días y sin novedades. Luego puede ajustar horas extra y descuentos en el detalle.</p>
              <div className="form-grid">
                <Field label="Año"><input type="number" min={2020} max={2100} {...gen.bind('anio')} /></Field>
                <Field label="Mes"><select {...gen.bind('mes')}>{Array.from({ length: 12 }, (_, i) => <option key={i} value={i + 1}>{fmt.mes(i + 1)}</option>)}</select></Field>
              </div>
            </>
          )}
        </FormModal>
      )}
      {modal === 'liquidar' && (
        <FormModal
          title="Liquidar nómina de un empleado"
          submitLabel="Liquidar"
          onClose={() => setModal(null)}
          onSubmit={async () => {
            const v = liq.values;
            const n = await api.post<NominaT>('/Payroll', {
              empleadoId: Number(v.empleadoId), anio: Number(v.anio), mes: Number(v.mes), diasTrabajados: Number(v.diasTrabajados),
              horasExtraDiurnas: Number(v.horasExtraDiurnas), horasExtraNocturnas: Number(v.horasExtraNocturnas), horasExtraDominicales: Number(v.horasExtraDominicales),
              bonificaciones: Number(v.bonificaciones), otrosDescuentos: Number(v.otrosDescuentos), detalleDescuentos: v.detalleDescuentos,
            });
            toast.ok(`Liquidado ${n.empleado}: neto ${fmt.money(n.netoPagar)}`);
            setModal(null);
            periodos.reload();
          }}
          wide
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Empleado *" error={err.empleadoId} full>
                <select required {...liq.bind('empleadoId')}>
                  <option value="">Seleccione...</option>
                  {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cedula}</option>)}
                </select>
              </Field>
              <Field label="Año"><input type="number" {...liq.bind('anio')} /></Field>
              <Field label="Mes"><select {...liq.bind('mes')}>{Array.from({ length: 12 }, (_, i) => <option key={i} value={i + 1}>{fmt.mes(i + 1)}</option>)}</select></Field>
              <Field label="Días trabajados"><input type="number" min={0} max={30} {...liq.bind('diasTrabajados')} /></Field>
              <Field label="Bonificaciones ($)"><input type="number" min={0} {...liq.bind('bonificaciones')} /></Field>
              <Field label="Horas extra diurnas"><input type="number" min={0} step="0.5" {...liq.bind('horasExtraDiurnas')} /></Field>
              <Field label="Horas extra nocturnas"><input type="number" min={0} step="0.5" {...liq.bind('horasExtraNocturnas')} /></Field>
              <Field label="Horas extra dominicales"><input type="number" min={0} step="0.5" {...liq.bind('horasExtraDominicales')} /></Field>
              <Field label="Otros descuentos ($)"><input type="number" min={0} {...liq.bind('otrosDescuentos')} /></Field>
              <Field label="Detalle de descuentos" full><input {...liq.bind('detalleDescuentos')} /></Field>
            </div>
          )}
        </FormModal>
      )}
    </>
  );
}
