import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { fmt } from '../format';
import type { Cumplimiento, TendenciaMes, Valor } from '../types';

const AXIS = { fontSize: 11, fill: 'var(--muted)' };
const millones = (v: number) => `${fmt.num(v / 1_000_000)} M`;

export function ProduccionDiaria({ data }: { data: Valor[] }) {
  const rows = data.map((d) => ({ dia: fmt.date(d.etiqueta).substring(0, 5), toneladas: d.valor }));
  return (
    <div className="chart-box">
      <ResponsiveContainer>
        <BarChart data={rows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
          <XAxis dataKey="dia" tick={AXIS} />
          <YAxis tick={AXIS} width={44} />
          <Tooltip formatter={(v: number) => [`${fmt.num(v)} t`, 'Producción']} />
          <Bar dataKey="toneladas" fill="var(--primary)" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function Finanzas({ data }: { data: TendenciaMes[] }) {
  const rows = data.map((d) => ({ mes: fmt.mes(d.mes).substring(0, 3), Ingresos: d.ingresos, Gastos: d.gastos, Utilidad: d.utilidad }));
  return (
    <div className="chart-box">
      <ResponsiveContainer>
        <LineChart data={rows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
          <XAxis dataKey="mes" tick={AXIS} />
          <YAxis tick={AXIS} tickFormatter={millones} width={56} />
          <Tooltip formatter={(v: number) => fmt.money(v)} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Line type="monotone" dataKey="Ingresos" stroke="#15803d" strokeWidth={2} dot={false} />
          <Line type="monotone" dataKey="Gastos" stroke="#c2410c" strokeWidth={2} dot={false} />
          <Line type="monotone" dataKey="Utilidad" stroke="#f2a900" strokeWidth={2.5} strokeDasharray="5 4" dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function CumplimientoFrentes({ data }: { data: Cumplimiento[] }) {
  return (
    <div className="chart-box">
      <ResponsiveContainer>
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
          <XAxis type="number" tick={AXIS} />
          <YAxis type="category" dataKey="frente" tick={AXIS} width={120} />
          <Tooltip formatter={(v: number) => `${fmt.num(v)} t`} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Bar dataKey="toneladas" name="Producido" fill="var(--primary)" radius={[0, 4, 4, 0]} />
          <Bar dataKey="meta" name="Meta" fill="#d6d3d1" radius={[0, 4, 4, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function PorTurno({ data }: { data: Valor[] }) {
  return (
    <div className="chart-box" style={{ height: 220 }}>
      <ResponsiveContainer>
        <BarChart data={data.map((d) => ({ turno: fmt.label(d.etiqueta), toneladas: d.valor }))}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
          <XAxis dataKey="turno" tick={AXIS} />
          <YAxis tick={AXIS} width={48} />
          <Tooltip formatter={(v: number) => [`${fmt.num(v)} t`, 'Toneladas']} />
          <Bar dataKey="toneladas" fill="#f2a900" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
