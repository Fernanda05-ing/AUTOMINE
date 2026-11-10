import { useState } from 'react';
import { api } from '../api';
import { fmt, today } from '../format';
import type { Liquidacion } from '../pages/Prestaciones';
import type { Empleado } from '../types';
import { ErrorBox, Field, Modal, useForm, useToast } from './ui';

const MOTIVOS = [
  ['RENUNCIA', 'Renuncia voluntaria'],
  ['SIN_JUSTA_CAUSA', 'Despido sin justa causa (con indemnización)'],
  ['JUSTA_CAUSA', 'Despido con justa causa'],
  ['MUTUO_ACUERDO', 'Mutuo acuerdo'],
  ['FIN_CONTRATO', 'Terminación del contrato / obra'],
  ['PENSION', 'Pensión'],
];

/** Simula y confirma la liquidación final de un trabajador (prestaciones, vacaciones e indemnización). */
export default function LiquidacionRetiro({ empleado, onClose, onDone }: { empleado: Empleado; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const f = useForm({ fechaRetiro: today(), motivo: 'RENUNCIA', fechaFinContrato: '', observaciones: '' });
  const [resultado, setResultado] = useState<Liquidacion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [guardada, setGuardada] = useState<Liquidacion | null>(null);
  const necesitaFin = f.values.motivo === 'SIN_JUSTA_CAUSA' && empleado.tipoContrato !== 'INDEFINIDO';

  const body = () => ({ ...f.values, fechaFinContrato: f.values.fechaFinContrato || null });

  const simular = async () => {
    setBusy(true);
    setError(null);
    try {
      setResultado(await api.post<Liquidacion>(`/Employee/${empleado.id}/Settlement/Preview`, body()));
    } catch (e) {
      setResultado(null);
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const confirmar = async () => {
    if (!window.confirm(`Se guardará la liquidación y ${empleado.nombreCompleto} quedará retirado. ¿Continuar?`)) return;
    setBusy(true);
    setError(null);
    try {
      const l = await api.post<Liquidacion>(`/Employee/${empleado.id}/Settlement`, body());
      setGuardada(l);
      toast.ok('Liquidación registrada y trabajador retirado');
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const r = guardada ?? resultado;
  return (
    <Modal
      title={`Liquidación final — ${empleado.nombreCompleto}`}
      onClose={onClose}
      wide
      footer={
        guardada ? (
          <>
            <button className="btn" onClick={onClose}>Cerrar</button>
            <button className="btn primary" onClick={() => api.download(`/Settlement/${guardada.id}/Pdf`, undefined, `liquidacion-${empleado.cedula}.pdf`).catch((e) => toast.error(e.message))}>
              Descargar PDF para firma
            </button>
          </>
        ) : (
          <>
            <button className="btn" onClick={onClose} disabled={busy}>Cancelar</button>
            <button className="btn" onClick={() => void simular()} disabled={busy}>Calcular</button>
            <button className="btn danger" onClick={() => void confirmar()} disabled={busy || !resultado}>Confirmar y retirar</button>
          </>
        )
      }
    >
      <ErrorBox error={error} />
      {!guardada && (
        <div className="form-grid mb">
          <Field label="Fecha de retiro *"><input type="date" {...f.bind('fechaRetiro')} /></Field>
          <Field label="Motivo *">
            <select {...f.bind('motivo')}>{MOTIVOS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
          </Field>
          {necesitaFin && (
            <Field label="Fecha en que terminaba el contrato *"><input type="date" {...f.bind('fechaFinContrato')} /></Field>
          )}
          <Field label="Observaciones" full><textarea rows={2} maxLength={500} {...f.bind('observaciones')} /></Field>
          <p className="muted full">
            Ingreso: {fmt.date(empleado.fechaIngreso)} · Contrato: {fmt.label(empleado.tipoContrato)} · Salario: {fmt.money(empleado.salario)}. Pulse <strong>Calcular</strong> para ver la liquidación antes de confirmarla.
          </p>
        </div>
      )}
      {r && (
        <table className="table">
          <thead>
            <tr><th>Concepto</th><th>Detalle</th><th className="num">Valor</th></tr>
          </thead>
          <tbody>
            <tr><td>Salario pendiente</td><td>{r.diasSalario} días</td><td className="num">{fmt.money(r.salarioPendiente)}</td></tr>
            <tr><td>Prima de servicios</td><td>{r.diasPrima} días</td><td className="num">{fmt.money(r.prima)}</td></tr>
            <tr><td>Cesantías</td><td>{r.diasCesantias} días</td><td className="num">{fmt.money(r.cesantias)}</td></tr>
            <tr><td>Intereses de cesantías (12 %)</td><td /><td className="num">{fmt.money(r.interesesCesantias)}</td></tr>
            <tr><td>Vacaciones no disfrutadas</td><td>{fmt.num(r.diasVacaciones)} días hábiles</td><td className="num">{fmt.money(r.vacaciones)}</td></tr>
            {r.indemnizacion > 0 && <tr><td>Indemnización (art. 64 CST)</td><td /><td className="num">{fmt.money(r.indemnizacion)}</td></tr>}
            <tr><td><strong>Total a pagar</strong></td><td /><td className="num"><strong>{fmt.money(r.total)}</strong></td></tr>
          </tbody>
        </table>
      )}
      {r && <p className="muted mt">Base de prestaciones: {fmt.money(r.basePrestaciones)}. El salario pendiente no incluye deducciones de seguridad social.</p>}
    </Modal>
  );
}
