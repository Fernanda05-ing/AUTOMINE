import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, ErrorBox, PageHeader, Stat, Tabs, useLoad, useToast } from '../components/ui';
import { fmt } from '../format';
import type { Certificado, Consignacion, Nomina, TurnoEmpleado } from '../types';

type Tab = 'pagos' | 'consignaciones' | 'certificados' | 'turnos';

/** Consultas personales del trabajador (HU006, HU007): pagos, consignaciones, certificados y turnos. */
export default function MiPortal() {
  const { user } = useAuth();
  const toast = useToast();
  const [tab, setTab] = useState<Tab>('pagos');
  const id = user?.empleadoId;
  const nominas = useLoad(() => api.get<Nomina[]>(`/Payroll/${id}`), [id]);
  const consignaciones = useLoad(() => api.get<Consignacion[]>(`/Transfers/${id}`), [id]);
  const certificados = useLoad(() => api.get<Certificado[]>('/Certificates'), [id]);
  const turnos = useLoad(() => api.get<TurnoEmpleado[]>(`/Shifts/${id}`), [id]);
  if (!id) return <ErrorBox error="Su usuario no está asociado a un empleado." />;
  const ultimo = nominas.data?.[0];

  return (
    <>
      <PageHeader title="Mi portal" subtitle="Consulte sus pagos, consignaciones, certificados y turnos">
        <ActionButton
          className="btn primary"
          action={async () => {
            await api.download(`/Certificate/${id}`, undefined, 'certificado-laboral.pdf');
            certificados.reload();
          }}
          success="Certificado generado"
        >
          Descargar certificado laboral
        </ActionButton>
      </PageHeader>
      <div className="grid grid-3 mb">
        <Stat label="Último neto pagado" value={ultimo ? fmt.money(ultimo.netoPagar) : '—'} hint={ultimo ? `${fmt.mes(ultimo.mes)} ${ultimo.anio}` : undefined} />
        <Stat label="Consignaciones recibidas" value={consignaciones.data?.filter((c) => c.estado === 'APROBADA').length ?? '—'} />
        <Stat label="Turnos asignados (60 días)" value={turnos.data?.length ?? '—'} />
      </div>
      <Tabs<Tab>
        tabs={[
          { key: 'pagos', label: 'Mis pagos' },
          { key: 'consignaciones', label: 'Consignaciones' },
          { key: 'certificados', label: 'Certificados' },
          { key: 'turnos', label: 'Mis turnos' },
        ]}
        active={tab}
        onChange={setTab}
      />
      {tab === 'pagos' && (
        <DataTable<Nomina>
          rows={nominas.data}
          columns={[
            { key: 'p', label: 'Periodo', render: (n) => `${fmt.mes(n.mes)} ${n.anio}` },
            { key: 'd', label: 'Devengado', num: true, render: (n) => fmt.money(n.totalDevengado) },
            { key: 'x', label: 'Descuentos', num: true, render: (n) => fmt.money(n.totalDescuentos) },
            { key: 'n', label: 'Neto', num: true, render: (n) => <strong>{fmt.money(n.netoPagar)}</strong> },
            { key: 'e', label: 'Estado', render: (n) => <Badge value={n.estado} /> },
            {
              key: 'a',
              label: '',
              render: (n) => (
                <button className="btn small" onClick={() => api.download(`/Payroll/Detail/${n.id}/Payslip`).catch((e) => toast.error(e.message))}>
                  Desprendible PDF
                </button>
              ),
            },
          ]}
        />
      )}
      {tab === 'consignaciones' && (
        <DataTable<Consignacion>
          rows={consignaciones.data}
          columns={[
            { key: 'f', label: 'Fecha', render: (c) => fmt.date(c.fechaPago) },
            { key: 'p', label: 'Nómina', render: (c) => `${fmt.mes(c.mes)} ${c.anio}` },
            { key: 'b', label: 'Banco', render: (c) => `${c.banco} ${c.cuenta}` },
            { key: 'r', label: 'Referencia', render: (c) => c.referencia },
            { key: 'v', label: 'Valor', num: true, render: (c) => fmt.money(c.valor) },
            { key: 'e', label: 'Estado', render: (c) => <Badge value={c.estado} /> },
            {
              key: 'c',
              label: '',
              render: (c) =>
                c.tieneComprobante ? (
                  <button className="btn small" onClick={() => api.download(`/Transfer/${c.id}/Receipt`).catch((e) => toast.error(e.message))}>
                    Comprobante
                  </button>
                ) : null,
            },
          ]}
        />
      )}
      {tab === 'certificados' && (
        <DataTable<Certificado>
          rows={certificados.data}
          columns={[
            { key: 'f', label: 'Emitido', render: (c) => fmt.dateTime(c.fechaEmision) },
            { key: 'c', label: 'Código', render: (c) => <code>{c.codigoValidacion}</code> },
            { key: 'd', label: 'Dirigido a', render: (c) => c.dirigidoA },
            { key: 'e', label: 'Estado', render: (c) => <Badge value={c.estado} /> },
            {
              key: 'a',
              label: '',
              render: (c) =>
                c.estado === 'VIGENTE' ? (
                  <button className="btn small" onClick={() => api.download(`/Certificate/Detail/${c.id}/Pdf`).catch((e) => toast.error(e.message))}>
                    PDF
                  </button>
                ) : null,
            },
          ]}
        />
      )}
      {tab === 'turnos' && (
        <DataTable<TurnoEmpleado>
          rows={turnos.data}
          rowKey={(t) => t.produccionId}
          columns={[
            { key: 'f', label: 'Fecha', render: (t) => fmt.date(t.fecha) },
            { key: 't', label: 'Turno', render: (t) => fmt.label(t.turno) },
            { key: 'fr', label: 'Frente', render: (t) => t.frente },
            { key: 'l', label: 'Labor', render: (t) => t.labor },
            { key: 'to', label: 'Toneladas del turno', num: true, render: (t) => fmt.num(t.toneladasTurno) },
          ]}
        />
      )}
    </>
  );
}
