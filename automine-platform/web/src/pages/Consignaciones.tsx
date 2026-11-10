import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, Field, FormModal, PageHeader, Stat, useForm, useLoad, useToast } from '../components/ui';
import { fmt, today } from '../format';
import type { Consignacion, Nomina, Periodo } from '../types';

/** Consignaciones bancarias de nomina (RF017-RF021, RN002, RN005). */
export default function Consignaciones() {
  const { user, can } = useAuth();
  const toast = useToast();
  const periodos = useLoad(() => api.get<Periodo[]>('/Payroll/Periods'));
  const [periodoId, setPeriodoId] = useState('');
  const [estado, setEstado] = useState('');
  const lista = useLoad(() => api.get<Consignacion[]>('/Transfers', { periodoId, estado }), [periodoId, estado]);
  const [modal, setModal] = useState<'registrar' | 'lote' | null>(null);
  const [anular, setAnular] = useState<Consignacion | null>(null);
  const [nominasPeriodo, setNominasPeriodo] = useState<Nomina[]>([]);
  const reg = useForm({ periodo: '', nominaId: '', banco: '', cuenta: '', valor: '', fechaPago: today(), referencia: '', observaciones: '' });
  const lote = useForm({ periodo: '', fechaPago: today(), prefijoReferencia: 'NOM' });
  const motivo = useForm({ motivo: '' });
  const contador = can('CONTADOR');
  const aprobados = periodos.data?.filter((p) => p.estado === 'APROBADO') ?? [];
  const total = (lista.data ?? []).filter((c) => c.estado !== 'ANULADA').reduce((s, c) => s + c.valor, 0);

  const cargarNominas = async (pid: string) => {
    reg.setValues((v) => ({ ...v, periodo: pid, nominaId: '' }));
    setNominasPeriodo(pid ? (await api.get<Nomina[]>(`/Payroll/Period/${pid}/Detail`)).filter((n) => n.estado === 'APROBADA') : []);
  };

  return (
    <>
      <PageHeader title="Consignaciones" subtitle="Pagos bancarios asociados a nóminas aprobadas. Solo Contabilidad aprueba los pagos.">
        {contador && <button className="btn" onClick={() => setModal('lote')}>Consignar periodo completo</button>}
        {contador && <button className="btn primary" onClick={() => setModal('registrar')}>Registrar consignación</button>}
      </PageHeader>
      <div className="grid grid-3 mb">
        <Stat label="Consignaciones" value={lista.data?.length ?? '—'} />
        <Stat label="Pendientes de aprobación" value={lista.data?.filter((c) => c.estado === 'REGISTRADA').length ?? '—'} />
        <Stat label="Valor total (sin anuladas)" value={fmt.money(total)} />
      </div>
      <div className="toolbar">
        <Field label="Periodo">
          <select value={periodoId} onChange={(e) => setPeriodoId(e.target.value)}>
            <option value="">Todos</option>
            {periodos.data?.map((p) => <option key={p.id} value={p.id}>{fmt.mes(p.mes)} {p.anio}</option>)}
          </select>
        </Field>
        <Field label="Estado">
          <select value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="">Todos</option>
            <option value="REGISTRADA">Registradas</option>
            <option value="APROBADA">Aprobadas</option>
            <option value="ANULADA">Anuladas</option>
          </select>
        </Field>
      </div>
      <DataTable<Consignacion>
        rows={lista.data}
        search={(c) => `${c.empleado} ${c.cedula} ${c.referencia} ${c.banco}`}
        searchPlaceholder="Buscar por empleado, cédula, referencia o banco"
        columns={[
          { key: 'f', label: 'Fecha', render: (c) => fmt.date(c.fechaPago) },
          { key: 'e', label: 'Empleado', render: (c) => <><strong>{c.empleado}</strong><div className="muted">{c.cedula}</div></> },
          { key: 'p', label: 'Nómina', render: (c) => `${fmt.mes(c.mes)} ${c.anio}` },
          { key: 'b', label: 'Banco / cuenta', render: (c) => `${c.banco} ${c.cuenta}` },
          { key: 'r', label: 'Referencia', render: (c) => <code>{c.referencia}</code> },
          { key: 'v', label: 'Valor', num: true, render: (c) => fmt.money(c.valor) },
          { key: 's', label: 'Estado', render: (c) => <Badge value={c.estado} /> },
          {
            key: 'a',
            label: '',
            render: (c) => (
              <div className="actions">
                {c.estado === 'REGISTRADA' && user?.rol === 'CONTADOR' && (
                  <ActionButton className="btn small primary" action={() => api.put(`/Transfer/${c.id}/Approve`)} success="Pago aprobado" onDone={lista.reload}>Aprobar</ActionButton>
                )}
                {c.estado === 'REGISTRADA' && contador && <button className="btn small danger" onClick={() => setAnular(c)}>Anular</button>}
                {contador && c.estado !== 'ANULADA' && (
                  <label className="btn small">
                    {c.tieneComprobante ? 'Cambiar soporte' : 'Subir soporte'}
                    <input
                      type="file"
                      hidden
                      accept=".pdf,.jpg,.jpeg,.png"
                      onChange={async (e) => {
                        const f = e.target.files?.[0];
                        if (!f) return;
                        try {
                          await api.upload(`/Transfer/${c.id}/Receipt`, f);
                          toast.ok('Comprobante cargado');
                          lista.reload();
                        } catch (err) {
                          toast.error((err as Error).message);
                        }
                      }}
                    />
                  </label>
                )}
                {c.tieneComprobante && <button className="btn small" onClick={() => api.download(`/Transfer/${c.id}/Receipt`).catch((e) => toast.error(e.message))}>Ver soporte</button>}
              </div>
            ),
          },
        ]}
      />

      {modal === 'registrar' && (
        <FormModal
          title="Registrar consignación"
          onClose={() => setModal(null)}
          onSubmit={async () => {
            const v = reg.values;
            await api.post('/Transfer', {
              nominaId: Number(v.nominaId), banco: v.banco || null, cuenta: v.cuenta || null,
              valor: v.valor ? Number(v.valor) : null, fechaPago: v.fechaPago, referencia: v.referencia, observaciones: v.observaciones,
            });
            toast.ok('Consignación registrada. Queda pendiente de aprobación');
            setModal(null);
            lista.reload();
          }}
          wide
        >
          {(err) => (
            <>
              {aprobados.length === 0 && <div className="alert warn">No hay periodos aprobados. Primero apruebe la nómina (RN002).</div>}
              <div className="form-grid">
                <Field label="Periodo aprobado *">
                  <select required value={reg.values.periodo} onChange={(e) => void cargarNominas(e.target.value)}>
                    <option value="">Seleccione...</option>
                    {aprobados.map((p) => <option key={p.id} value={p.id}>{fmt.mes(p.mes)} {p.anio}</option>)}
                  </select>
                </Field>
                <Field label="Nómina del empleado *" error={err.nominaId}>
                  <select required {...reg.bind('nominaId')}>
                    <option value="">Seleccione...</option>
                    {nominasPeriodo.map((n) => <option key={n.id} value={n.id}>{n.empleado} — {fmt.money(n.netoPagar)}</option>)}
                  </select>
                </Field>
                <Field label="Banco (vacío = cuenta del empleado)"><input {...reg.bind('banco')} /></Field>
                <Field label="Cuenta (vacío = cuenta del empleado)"><input {...reg.bind('cuenta')} /></Field>
                <Field label="Valor (vacío = saldo pendiente)" error={err.valor}><input type="number" min={1} {...reg.bind('valor')} /></Field>
                <Field label="Fecha de pago *" error={err.fechaPago}><input type="date" required {...reg.bind('fechaPago')} /></Field>
                <Field label="Referencia bancaria *" error={err.referencia}><input required {...reg.bind('referencia')} /></Field>
                <Field label="Observaciones"><input {...reg.bind('observaciones')} /></Field>
              </div>
            </>
          )}
        </FormModal>
      )}
      {modal === 'lote' && (
        <FormModal
          title="Consignar periodo completo"
          submitLabel="Registrar consignaciones"
          onClose={() => setModal(null)}
          onSubmit={async () => {
            const r = await api.post<{ registradas: number; sinCuenta: number; yaConsignadas: number }>(`/Transfer/Batch/${lote.values.periodo}`, {
              fechaPago: lote.values.fechaPago,
              prefijoReferencia: lote.values.prefijoReferencia,
            });
            toast.ok(`${r.registradas} consignaciones registradas · ${r.sinCuenta} sin cuenta bancaria · ${r.yaConsignadas} ya consignadas`);
            setModal(null);
            lista.reload();
          }}
        >
          {() => (
            <div className="form-grid">
              <Field label="Periodo aprobado *">
                <select required {...lote.bind('periodo')}>
                  <option value="">Seleccione...</option>
                  {aprobados.map((p) => <option key={p.id} value={p.id}>{fmt.mes(p.mes)} {p.anio}</option>)}
                </select>
              </Field>
              <Field label="Fecha de pago"><input type="date" required {...lote.bind('fechaPago')} /></Field>
              <Field label="Prefijo de referencia"><input {...lote.bind('prefijoReferencia')} /></Field>
            </div>
          )}
        </FormModal>
      )}
      {anular && (
        <FormModal title={`Anular consignación ${anular.referencia}`} submitLabel="Anular" onClose={() => setAnular(null)} onSubmit={async () => { await api.put(`/Transfer/${anular.id}/Void`, motivo.values); toast.ok('Consignación anulada'); setAnular(null); lista.reload(); }}>
          {(err) => <Field label="Motivo" error={err.motivo}><textarea required {...motivo.bind('motivo')} /></Field>}
        </FormModal>
      )}
    </>
  );
}
