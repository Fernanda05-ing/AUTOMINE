import { CalendarDays, ChevronRight, LogOut, Menu, Moon, Sun } from 'lucide-react';
import { aplicarTema, temaGuardado, type Tema } from '../theme';
import { useEffect, useState } from 'react';
import { api } from '../api';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth';
import { allowed, MENU } from '../menu';
import Brand from './Brand';

const fechaLarga = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
const hoyLargo = fechaLarga.charAt(0).toUpperCase() + fechaLarga.slice(1);

export function iniciales(nombre: string) {
  return nombre
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
}

export default function Layout() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [tema, setTema] = useState<Tema>(temaGuardado);
  const location = useLocation();
  const [noLeidos, setNoLeidos] = useState(0);
  useEffect(() => {
    if (!user || user.debeCambiarPassword) return;
    const consultar = () =>
      api.get<{ noLeidos: number }>('/Chat/Unread').then((r) => setNoLeidos(r.noLeidos)).catch(() => {});
    consultar();
    const t = setInterval(consultar, 20000);
    return () => clearInterval(t);
  }, [user, location.pathname]);
  if (!user) return null;
  const items = MENU.filter((m) => allowed(m, user.rol) && !(m.path === '/mi-portal' && !user.empleadoId));
  const sections = [...new Set(items.map((i) => i.section))];
  const actual =
    location.pathname === '/perfil' ? { label: 'Mi perfil', section: 'Cuenta' } : MENU.find((m) => m.path === location.pathname) ?? MENU[0];

  return (
    <div className="shell">
      <div className={`scrim ${open ? 'open' : ''}`} onClick={() => setOpen(false)} />
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="sidebar-head">
          <Brand />
        </div>
        <nav className="nav" aria-label="Menú principal">
          {sections.map((s) => (
            <div key={s}>
              <div className="nav-section">{s}</div>
              {items
                .filter((i) => i.section === s)
                .map((i) => (
                  <NavLink key={i.path} to={i.path} end={i.path === '/'} onClick={() => setOpen(false)}>
                    <i.icon aria-hidden />
                    {i.label}
                    {i.path === '/mensajes' && noLeidos > 0 && <span className="nav-badge">{noLeidos > 99 ? '99+' : noLeidos}</span>}
                  </NavLink>
                ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-user">
          <NavLink to="/perfil" className="avatar" title="Mi perfil" onClick={() => setOpen(false)} style={{ textDecoration: 'none' }}>
            {iniciales(user.nombre)}
          </NavLink>
          <div className="info">
            <strong>{user.nombre}</strong>
            <span>{user.rolNombre}</span>
          </div>
          <button className="icon-btn" onClick={() => void logout()} title="Cerrar sesión" aria-label="Cerrar sesión">
            <LogOut />
          </button>
        </div>
      </aside>
      <div className="main">
        <header className="topbar">
          <button className="icon-btn menú-btn" onClick={() => setOpen(!open)} aria-label="Abrir menú">
            <Menu />
          </button>
          <div className="crumb">
            {actual.section}
            <ChevronRight size={14} />
            <strong>{actual.label}</strong>
          </div>
          <div className="spacer" />
          <div className="date">
            <CalendarDays size={15} />
            <span>{hoyLargo}</span>
          </div>
          <button
            className="icon-btn theme-btn"
            onClick={() => {
              const nuevo = tema === 'dark' ? 'light' : 'dark';
              aplicarTema(nuevo);
              setTema(nuevo);
            }}
            title={tema === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
            aria-label={tema === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
          >
            {tema === 'dark' ? <Sun /> : <Moon />}
          </button>
        </header>
        <main className="content" key={location.pathname}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
