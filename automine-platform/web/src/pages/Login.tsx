import { ArrowRight, FileBadge, Lock, Mail } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import Brand from '../components/Brand';
import { ErrorBox, Field } from '../components/ui';

function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth-page">
      {/* Panel de identidad: solo la fotografia y la marca */}
      <section className="auth-hero">
        <Brand size="lg" />
        <div className="auth-slogan">
          <h1>
            Transformamos
            <br />
            la minería
          </h1>
          <p>Con tecnología, seguridad y talento humano.</p>
        </div>
      </section>
      <section className="auth-form">
        <div className="auth-card">
          <div className="auth-mobile-brand">
            <Brand light={false} />
          </div>
          {children}
        </div>
      </section>
    </div>
  );
}

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/', { replace: true });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout>
      <h2>Bienvenido de nuevo</h2>
      <p className="sub">Ingrese con su correo corporativo para continuar.</p>
      <form onSubmit={submit}>
        <ErrorBox error={error} />
        <Field label="Correo">
          <div className="input-icon">
            <Mail aria-hidden />
            <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="nombre@empresa.co" autoFocus />
          </div>
        </Field>
        <Field label="Contraseña">
          <div className="input-icon">
            <Lock aria-hidden />
            <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
          </div>
        </Field>
        <div style={{ textAlign: 'right', margin: '-2px 0 16px' }}>
          <Link to="/recuperar" style={{ fontSize: 13 }}>¿Olvidó su contraseña?</Link>
        </div>
        <button className="btn primary block" disabled={busy}>
          {busy ? 'Ingresando...' : 'Ingresar'}
          {!busy && <ArrowRight aria-hidden />}
        </button>
      </form>
      <div className="alert info mt" style={{ alignItems: 'center' }}>
        <FileBadge size={18} aria-hidden style={{ flexShrink: 0 }} />
        <span>
          ¿Recibió un certificado laboral? <Link to="/validar">Valide su autenticidad aquí</Link>
        </span>
      </div>
    </AuthLayout>
  );
}

export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await api.post<{ message: string }>('/ForgotPassword', { email: email.trim() });
      setMsg(r.message);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <AuthLayout>
      <h2>Recuperar contraseña</h2>
      <p className="muted">Le enviaremos un enlace a su correo para crear una nueva contraseña.</p>
      {msg ? (
        <div className="alert success">{msg}</div>
      ) : (
        <form onSubmit={submit}>
          <ErrorBox error={error} />
          <Field label="Correo">
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoFocus />
          </Field>
          <button className="btn primary block" style={{ marginTop: 8 }} disabled={busy}>
            Enviar enlace
          </button>
        </form>
      )}
      <p className="mt">
        <Link to="/login">Volver a iniciar sesión</Link>
      </p>
    </AuthLayout>
  );
}

export function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      setError('Las contraseñas no coinciden');
      return;
    }
    setError(null);
    try {
      const r = await api.post<{ message: string }>('/ResetPassword', { token, password });
      setMsg(r.message);
    } catch (err) {
      setError((err as Error).message);
    }
  };
  return (
    <AuthLayout>
      <h2>Nueva contraseña</h2>
      {msg ? (
        <div className="alert success">{msg}</div>
      ) : (
        <form onSubmit={submit}>
          {!token && <div className="alert error">El enlace no es válido.</div>}
          <ErrorBox error={error} />
          <Field label="Nueva contraseña (8+ caracteres, letras y números)">
            <input type="password" required minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          <Field label="Confirmar contraseña">
            <input type="password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </Field>
          <button className="btn primary block" style={{ marginTop: 8 }} disabled={!token}>
            Guardar contraseña
          </button>
        </form>
      )}
      <p className="mt">
        <Link to="/login">Ir a iniciar sesión</Link>
      </p>
    </AuthLayout>
  );
}
