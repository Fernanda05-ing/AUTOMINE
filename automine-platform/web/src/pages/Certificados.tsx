import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ActionButton, Badge, DataTable, Field, FormModal, PageHeader, useForm, useLoad, useToast } from '../components/ui';
import { fmt } from '../format';
import type { Certificado, EmpleadoOpcion } from '../types';

/** Certificados laborales con codigo de validacion y firma institucional (RF022-RF025, RN006). */
export default function Certificados() {
  const { can } = useAuth();
  const toast = useToast();
  const lista = useLoad(() => api.get<Certificado[]>('/Certificates'));
  const empleados = useLoad(() => api.get<EmpleadoOpcion[]>('/Employees/Options', { activos: false }));
  const [modal, setModal] = useState(false);
  const f = useForm({ empleadoId: '', dirigidoA: '', incluyeSalario: true });
  const emite = can('RRHH');

  return (
    <>
      <PageHeader title="Certificados laborales" subtitle="Se emiten solo a empleados activos o con historial laboral válido">
        <a className="btn" href="/validar" target="_blank" rel="noreferrer">Página pública de validación</a>
        {emite && <button className="btn primary" onClick={() => setModal(true)}>Emitir certificado</button>}
      </PageHeader>
      <DataTable<Certificado>
        rows={lista.data}
        search={(c) => `${c.empleado} ${c.cedula} ${c.codigoValidacion} ${c.dirigidoA}`}
        searchPlaceholder="Buscar por empleado, cédula o código"
        columns={[
          { key: 'f', label: 'Emitido', render: (c) => fmt.dateTime(c.fechaEmision) },
          { key: 'e', label: 'Empleado', render: (c) => <><strong>{c.empleado}</strong><div className="muted">{c.cedula}</div></> },
          { key: 'c', label: 'Código', render: (c) => <a href={`/validar/${c.codigoValidacion}`} target="_blank" rel="noreferrer"><code>{c.codigoValidacion}</code></a> },
          { key: 'd', label: 'Dirigido a', render: (c) => c.dirigidoA },
          { key: 's', label: 'Salario', render: (c) => (c.incluyeSalario ? 'Sí' : 'No') },
          { key: 'por', label: 'Emitido por', render: (c) => c.emitidoPor },
          { key: 'st', label: 'Estado', render: (c) => <Badge value={c.estado} /> },
          {
            key: 'a',
            label: '',
            render: (c) =>
              c.estado === 'VIGENTE' && (
                <div className="actions">
                  <button className="btn small" onClick={() => api.download(`/Certificate/Detail/${c.id}/Pdf`).catch((e) => toast.error(e.message))}>PDF</button>
                  {emite && <ActionButton className="btn small danger" confirm="El certificado dejará de ser válido. ¿Anular?" action={() => api.put(`/Certificate/${c.id}/Void`)} success="Certificado anulado" onDone={lista.reload}>Anular</ActionButton>}
                </div>
              ),
          },
        ]}
      />
      {modal && (
        <FormModal
          title="Emitir certificado laboral"
          submitLabel="Emitir y descargar"
          onClose={() => setModal(false)}
          onSubmit={async () => {
            const c = await api.post<Certificado>('/Certificate', { empleadoId: Number(f.values.empleadoId), dirigidoA: f.values.dirigidoA, incluyeSalario: f.values.incluyeSalario });
            await api.download(`/Certificate/Detail/${c.id}/Pdf`);
            toast.ok(`Certificado ${c.codigoValidacion} emitido`);
            setModal(false);
            lista.reload();
          }}
        >
          {(err) => (
            <div className="form-grid">
              <Field label="Empleado *" error={err.empleadoId} full>
                <select required {...f.bind('empleadoId')}>
                  <option value="">Seleccione...</option>
                  {empleados.data?.map((e) => <option key={e.id} value={e.id}>{e.nombre} — {e.cedula} {e.estado === 'INACTIVO' ? '(retirado)' : ''}</option>)}
                </select>
              </Field>
              <Field label="Dirigido a" full><input placeholder="A QUIEN INTERESE" {...f.bind('dirigidoA')} /></Field>
              <label className="check full"><input type="checkbox" {...f.check('incluyeSalario')} /> Incluir salario</label>
            </div>
          )}
        </FormModal>
      )}
    </>
  );
}
