import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, Field, PageHeader, useToast } from '../components/ui';
import { fmt } from '../format';

/** Servicios SeeProfile / EditProfile. */
export default function Perfil() {
  const { user, refresh } = useAuth();
  const toast = useToast();
  const [nombre, setNombre] = useState(user?.nombre ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [error, setError] = useState<string | null>(null);
  if (!user) return null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.put('/EditUser', {
        usuarioId: user.id,
        nombre,
        email,
        password: nueva || null,
        passwordActual: nueva ? actual : null,
      });
      await refresh();
      setActual('');
      setNueva('');
      toast.ok('Perfil actualizado');
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <>
      <PageHeader title="Mi perfil" subtitle={`${user.rolNombre} · Último acceso ${fmt.dateTime(user.ultimoAcceso)}`} />
      <form className="card" style={{ maxWidth: 640 }} onSubmit={submit}>
        <ErrorBox error={error} />
        <div className="form-grid">
          <Field label="Nombre">
            <input required value={nombre} onChange={(e) => setNombre(e.target.value)} />
          </Field>
          <Field label="Correo">
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label="Contraseña actual (solo si va a cambiarla)">
            <input type="password" value={actual} onChange={(e) => setActual(e.target.value)} autoComplete="current-password" />
          </Field>
          <Field label="Nueva contraseña">
            <input type="password" value={nueva} onChange={(e) => setNueva(e.target.value)} autoComplete="new-password" placeholder="8+ caracteres, letras y números" />
          </Field>
        </div>
        <div className="actions mt">
          <button className="btn primary">Guardar cambios</button>
        </div>
      </form>
    </>
  );
}
