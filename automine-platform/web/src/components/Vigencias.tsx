import { useState } from 'react';
import { api } from '../api';
import { fmt } from '../format';
import { ActionButton, DataTable, Field, FormModal, useForm, useLoad, useToast } from './ui';

interface Vigencia {
  id: number;
  clave: string;
  desde: string;
  valor: string;
  norma: string | null;
}

const CLAVES = ['SALARIO_MINIMO', 'AUXILIO_TRANSPORTE', 'HORAS_MES', 'RECARGO_EXTRA_DIURNA', 'RECARGO_EXTRA_NOCTURNA', 'RECARGO_EXTRA_DOMINICAL', 'PORC_SALUD', 'PORC_PENSION', 'PORC_SOLIDARIDAD'];

/** Valores que rigen desde una fecha: la nómina de cada mes usa los vigentes en ese mes. */
export default function Vigencias() {
  const toast = useToast();
  const lista = useLoad(() => api.get<Vigencia[]>('/Settings/Validity'));
  const [nueva, setNueva] = useState(false);
  const f = useForm({ clave: 'SALARIO_MINIMO', desde: `${new Date().getFullYear() + 1}-01-01`, valor: '', norma: '' });
  const hoy = new Date().toISOString().substring(0, 10);
  const filas = lista.data ?? [];
  const vigente = (v: Vigencia) => !filas.some((o) => o.clave === v.clave && o.desde > v.desde && o.desde <= hoy) && v.desde <= hoy;

  return (
    <div className="mt">
      <div className="toolbar mb">
        <h3 style={{ margin: 0 }}>Valores por fecha de vigencia</h3>
        <div style={{ flex: 1 }} />
        <button className="btn primary" onClick={() => { f.reset(); setNueva(true); }}>Agregar valor</button>
      </div>
      <p className="muted">
        Cada nómina usa el valor vigente en su mes (por ejemplo, el salario mínimo de cada año o la jornada de 42 horas desde julio de 2026). Si un parámetro no tiene vigencias se usa el valor general de arriba.
      </p>
      <DataTable<Vigencia>
        rows={filas}
        pageSize={25}
        rowKey={(v) => v.id}
        columns={[
          { key: 'c', label: 'Parámetro', render: (v) => <strong>{v.clave}</strong> },
          { key: 'd', label: 'Desde', render: (v) => fmt.date(v.desde) },
          { key: 'v', label: 'Valor', num: true, render: (v) => (Number(v.valor) >= 1000 ? fmt.money(Number(v.valor)) : v.valor) },
          { key: 'n', label: 'Norma', render: (v) => v.norma ?? '' },
          { key: 's', label: '', render: (v) => (vigente(v) ? <span className="badge ok">Vigente</span> : v.desde > hoy ? <span className="badge info">Futuro</span> : <span className="muted">Anterior</span>) },
          {
            key: 'a',
            label: '',
            render: (v) => (
              <ActionButton className="btn small danger" action={() => api.del(`/Settings/Validity/${v.id}`)} confirm={`¿Eliminar ${v.clave} desde ${fmt.date(v.desde)}?`} onDone={lista.reload}>
                Quitar
              </ActionButton>
            ),
          },
        ]}
      />
      {nueva && (
        <FormModal
          title="Agregar valor con vigencia"
          onClose={() => setNueva(false)}
          onSubmit={async () => {
            await api.post('/Settings/Validity', f.values);
            toast.ok('Valor agregado');
            setNueva(false);
            lista.reload();
          }}
        >
          {() => (
            <div className="form-grid">
              <Field label="Parámetro *"><select {...f.bind('clave')}>{CLAVES.map((c) => <option key={c} value={c}>{c}</option>)}</select></Field>
              <Field label="Rige desde *"><input type="date" required {...f.bind('desde')} /></Field>
              <Field label="Valor *"><input required inputMode="decimal" placeholder="Ej.: 1750905 o 0.04" {...f.bind('valor')} /></Field>
              <Field label="Norma"><input maxLength={200} placeholder="Ej.: Decreto 1469 de 2025" {...f.bind('norma')} /></Field>
            </div>
          )}
        </FormModal>
      )}
    </div>
  );
}
