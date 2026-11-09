import {
  AlertTriangle,
  ArrowRight,
  Boxes,
  CircleDollarSign,
  Gauge,
  HardHat,
  Mountain,
  Receipt,
  ShieldCheck,
  TrendingUp,
  Users,
  Wallet,
  Wrench,
  type LucideIcon,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { CumplimientoFrentes, Finanzas, ProduccionDiaria } from '../components/Charts';
import { ErrorBox, Spinner, Stat, useLoad } from '../components/ui';
import { fmt } from '../format';
import { allowed, MENU } from '../menu';
import type { Dashboard, Kpi } from '../types';

function valorKpi(k: Kpi) {
  if (k.unidad === 'COP') return fmt.money(k.valor);
  if (k.unidad === 'COP/t') return `${fmt.money(k.valor)}/t`;
  if (k.unidad === '%') return `${fmt.num(k.valor)} %`;
  return `${fmt.num(k.valor)} ${k.unidad}`;
}

const KPI_UI: Record<string, { icon: LucideIcon; tone?: '' | 'ok' | 'info' | 'danger'; invert?: boolean }> = {
  toneladas: { icon: Mountain },
  cumplimiento: { icon: Gauge, tone: 'info' },
  ingresos: { icon: TrendingUp, tone: 'ok' },
  utilidad: { icon: CircleDollarSign, tone: 'ok' },
  gastos: { icon: Receipt, tone: 'danger', invert: true },
  costoTonelada: { icon: Gauge, tone: 'danger', invert: true },
  nomina: { icon: Wallet, tone: 'info', invert: true },
  empleados: { icon: Users },
  productividad: { icon: Gauge, tone: 'info' },
  inventario: { icon: Boxes },
};

const ALERTA_ICON: Record<string, LucideIcon> = {
  INVENTARIO: Boxes,
  MAQUINARIA: Wrench,
  SGSST: HardHat,
  NOMINA: Wallet,
};

/** Servicio Dashboard (RF048): tablero gerencial en tiempo real. */
function TableroGerencial({ nombre }: { nombre: string }) {
  const { data, loading, error } = useLoad(() => api.get<Dashboard>('/Dashboard'));
  if (loading) return <Spinner />;
  if (error || !data) return <ErrorBox error={error} />;
  const k = (clave: string) => data.kpis.find((x) => x.clave === clave);
  const destacados = ['ingresos', 'utilidad', 'gastos', 'costoTonelada', 'nomina', 'productividad', 'empleados', 'inventario'];
  const ton = k('toneladas');
  const cumpl = k('cumplimiento');
  return (
    <>
      <section className="hero">
        <div className="hero-row">
          <div>
            <h1>Hola, {nombre}</h1>
            <p>Así va la operación este mes.</p>
          </div>
          <div className="hero-chips">
            <div className="hero-chip">
              <small>Producción del mes</small>
              <strong>{ton ? `${fmt.num(ton.valor)} t` : '—'}</strong>
            </div>
            <div className="hero-chip">
              <small>Cumplimiento de meta</small>
              <strong>{cumpl ? `${fmt.num(cumpl.valor)} %` : '—'}</strong>
            </div>
            <div className="hero-chip">
              <small>Días sin accidentes</small>
              <strong>{data.sst.diasSinAccidentes < 0 ? '—' : data.sst.diasSinAccidentes}</strong>
            </div>
          </div>
        </div>
      </section>

      <div className="grid grid-4">
        {destacados.map((c) => {
          const kpi = k(c);
          if (!kpi) return null;
          const ui = KPI_UI[c] ?? { icon: Gauge };
          return <Stat key={c} label={kpi.nombre} value={valorKpi(kpi)} delta={kpi.variacion} icon={ui.icon} tone={ui.tone} invert={ui.invert} />;
        })}
      </div>

      <div className="grid grid-wide mt">
        <div className="card">
          <div className="card-head">
            <h3>Producción diaria</h3>
            <span>Toneladas · mes en curso</span>
          </div>
          <ProduccionDiaria data={data.produccionMes.serieDiaria} />
        </div>
        <div className="card">
          <div className="card-head">
            <h3>Alertas</h3>
            <span>{data.alertas.length} pendientes</span>
          </div>
          {data.alertas.length === 0 ? (
            <p className="muted">Todo en orden: no hay alertas.</p>
          ) : (
            <ul className="list-plain" style={{ maxHeight: 290, overflowY: 'auto' }}>
              {data.alertas.map((a, i) => {
                const Icon = ALERTA_ICON[a.tipo] ?? AlertTriangle;
                return (
                  <li key={i}>
                    <span className={`alert-dot ${a.nivel === 'ALTA' ? 'alta' : ''}`}>
                      <Icon aria-hidden />
                    </span>
                    <span>
                      <strong style={{ display: 'block', fontSize: 12 }}>{fmt.label(a.tipo)}</strong>
                      <span className="muted">{a.mensaje}</span>
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>

      <div className="grid grid-2 mt">
        <div className="card">
          <div className="card-head">
            <h3>Ingresos, gastos y utilidad</h3>
            <span>Últimos 6 meses</span>
          </div>
          <Finanzas data={data.finanzas} />
        </div>
        <div className="card">
          <div className="card-head">
            <h3>Cumplimiento por frente</h3>
            <span>Producido vs. meta</span>
          </div>
          <CumplimientoFrentes data={data.produccionMes.porFrente} />
        </div>
      </div>

      <div className="section-title">Seguridad y salud en el trabajo</div>
      <div className="grid grid-4">
        <Stat label="Accidentes del mes" value={data.sst.accidentesMes} icon={HardHat} tone="danger" />
        <Stat label="Incidentes del mes" value={data.sst.incidentesMes} icon={AlertTriangle} />
        <Stat label="Casos abiertos" value={data.sst.eventosAbiertos} icon={ShieldCheck} tone="info" />
        <Stat label="Riesgos altos o críticos" value={data.sst.riesgosAltos} icon={AlertTriangle} tone="danger" />
      </div>
    </>
  );
}

export default function Inicio() {
  const { user, can } = useAuth();
  if (!user) return null;
  const nombre = user.nombre.split(' ')[0];
  if (can('GERENTE', 'CONTADOR')) return <TableroGerencial nombre={nombre} />;
  const accesos = MENU.filter((m) => m.path !== '/' && allowed(m, user.rol) && !(m.path === '/mi-portal' && !user.empleadoId));
  return (
    <>
      <section className="hero">
        <div className="hero-row">
          <div>
            <h1>Hola, {nombre}</h1>
            <p>{user.rolNombre} · Elija un modulo para comenzar.</p>
          </div>
        </div>
      </section>
      <div className="grid grid-3">
        {accesos.map((m) => (
          <Link key={m.path} to={m.path} className="card link-card">
            <span className="stat-icon">
              <m.icon aria-hidden />
            </span>
            <span style={{ flex: 1 }}>
              <h3 style={{ margin: 0 }}>{m.label}</h3>
              <span className="muted" style={{ fontSize: 12.5 }}>{m.section}</span>
            </span>
            <ArrowRight size={18} className="muted" aria-hidden />
          </Link>
        ))}
      </div>
    </>
  );
}
