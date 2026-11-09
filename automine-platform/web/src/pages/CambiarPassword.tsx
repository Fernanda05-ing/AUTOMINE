import { KeyRound, Lock } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, session, type Usuario } from '../api';
import { useAuth } from '../auth';
import Brand from '../components/Brand';
import { ErrorBox, Field } from '../components/ui';

/** Pantalla obligatoria cuando el usuario entra con una contraseña temporal. */
export default function CambiarPassword() {
  const { user, refresh, logout } = useAuth();
  const navigate = useNavigate();
  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (nueva !== confirmar) {
      setError('Las contraseñas nuevas no coinciden');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const u = await api.post<Usuario>('/ChangePassword', { passwordActual: actual, password: nueva });
      const token = session.token();
      if (token) session.save(token, u);
      await refresh();
      navigate('/', { replace: true });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-form" style={{ minHeight: '100%' }}>
      <div className="auth-card">
        <div style={{ marginBottom: 24 }}>
          <Brand light={false} />
        </div>
        <div className="stat-icon" style={{ marginBottom: 14 }}>
          <KeyRound aria-hidden />
        </div>
        <h2>Cree su contraseña</h2>
        <p className="sub">
          Hola{user ? `, ${user.nombre.split(' ')[0]}` : ''}. Está usando una contraseña temporal. Por seguridad, cree una
          contraseña personal para continuar.
        </p>
        <form onSubmit={submit}>
          <ErrorBox error={error} />
          <Field label="Contraseña temporal">
            <div className="input-icon">
              <Lock aria-hidden />
              <input type="password" required value={actual} onChange={(e) => setActual(e.target.value)} autoComplete="current-password" autoFocus />
            </div>
          </Field>
          <Field label="Nueva contraseña (8 o más caracteres, con letras y números)">
            <div className="input-icon">
              <KeyRound aria-hidden />
              <input type="password" required minLength={8} value={nueva} onChange={(e) => setNueva(e.target.value)} autoComplete="new-password" />
            </div>
          </Field>
          <Field label="Confirmar nueva contraseña">
            <div className="input-icon">
              <KeyRound aria-hidden />
              <input type="password" required value={confirmar} onChange={(e) => setConfirmar(e.target.value)} autoComplete="new-password" />
            </div>
          </Field>
          <button className="btn primary block" style={{ marginTop: 8 }} disabled={busy}>
            {busy ? 'Guardando...' : 'Guardar y continuar'}
          </button>
        </form>
        <p className="mt" style={{ textAlign: 'center' }}>
          <button className="btn link" onClick={() => void logout()}>
            Cerrar sesión
          </button>
        </p>
      </div>
    </div>
  );
}
