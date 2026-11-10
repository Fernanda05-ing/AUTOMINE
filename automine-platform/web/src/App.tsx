import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth';
import Layout from './components/Layout';
import { Spinner } from './components/ui';
import { allowed, MENU } from './menu';
import Login, { ForgotPassword, ResetPassword } from './pages/Login';
import ValidarCertificado from './pages/ValidarCertificado';
import CambiarPassword from './pages/CambiarPassword';

const Inicio = lazy(() => import('./pages/Inicio'));
const Perfil = lazy(() => import('./pages/Perfil'));
const Chat = lazy(() => import('./pages/Chat'));
const MiPortal = lazy(() => import('./pages/MiPortal'));
const Empleados = lazy(() => import('./pages/Empleados'));
const Certificados = lazy(() => import('./pages/Certificados'));
const Nomina = lazy(() => import('./pages/Nomina'));
const Incapacidades = lazy(() => import('./pages/Incapacidades'));
const Vacaciones = lazy(() => import('./pages/Vacaciones'));
const Prestaciones = lazy(() => import('./pages/Prestaciones'));
const Asistencia = lazy(() => import('./pages/Asistencia'));
const SaludOcupacional = lazy(() => import('./pages/SaludOcupacional'));
const Inspecciones = lazy(() => import('./pages/Inspecciones'));
const Consignaciones = lazy(() => import('./pages/Consignaciones'));
const Produccion = lazy(() => import('./pages/Produccion'));
const Inventario = lazy(() => import('./pages/Inventario'));
const Sgsst = lazy(() => import('./pages/Sgsst'));
const Contabilidad = lazy(() => import('./pages/Contabilidad'));
const Reportes = lazy(() => import('./pages/Reportes'));
const Administracion = lazy(() => import('./pages/Administracion'));

function Protected({ path, children }: { path: string; children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  if (user.debeCambiarPassword) return <Navigate to="/cambiar-password" replace />;
  const item = MENU.find((m) => m.path === path);
  if (item && !allowed(item, user.rol)) return <Navigate to="/" replace />;
  return <Suspense fallback={<Spinner />}>{children}</Suspense>;
}

export default function App() {
  const { user } = useAuth();
  const p = (path: string, el: ReactNode) => <Route path={path === '/' ? undefined : path.substring(1)} index={path === '/'} element={<Protected path={path}>{el}</Protected>} />;
  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to="/" replace /> : <Login />} />
      <Route path="/cambiar-password" element={user ? <CambiarPassword /> : <Navigate to="/login" replace />} />
      <Route path="/recuperar" element={<ForgotPassword />} />
      <Route path="/restablecer" element={<ResetPassword />} />
      <Route path="/validar" element={<ValidarCertificado />} />
      <Route path="/validar/:codigo" element={<ValidarCertificado />} />
      <Route path="/" element={!user ? <Navigate to="/login" replace /> : user.debeCambiarPassword ? <Navigate to="/cambiar-password" replace /> : <Layout />}>
        {p('/', <Inicio />)}
        {p('/perfil', <Perfil />)}
        {p('/mensajes', <Chat />)}
        {p('/mi-portal', <MiPortal />)}
        {p('/empleados', <Empleados />)}
        {p('/certificados', <Certificados />)}
        {p('/incapacidades', <Incapacidades />)}
        {p('/vacaciones', <Vacaciones />)}
        {p('/prestaciones', <Prestaciones />)}
        {p('/nomina', <Nomina />)}
        {p('/consignaciones', <Consignaciones />)}
        {p('/produccion', <Produccion />)}
        {p('/inventario', <Inventario />)}
        {p('/asistencia', <Asistencia />)}
        {p('/salud', <SaludOcupacional />)}
        {p('/inspecciones', <Inspecciones />)}
        {p('/sgsst', <Sgsst />)}
        {p('/contabilidad', <Contabilidad />)}
        {p('/reportes', <Reportes />)}
        {p('/administracion', <Administracion />)}
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
