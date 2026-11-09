import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import Brand from '../components/Brand';
import { fmt } from '../format';

interface Validacion {
  valido: boolean;
  codigo: string;
  estado: string;
  empleado: string | null;
  cedula: string | null;
  cargo: string | null;
  fechaEmision: string | null;
  empresa: string;
}

/** Pagina publica para validar un certificado laboral por su codigo (RF024). */
export default function ValidarCertificado() {
  const { codigo } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState(codigo ?? '');
  const [res, setRes] = useState<Validacion | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!codigo) return;
    setError(null);
    api
      .get<Validacion>(`/Certificate/Validate/${encodeURIComponent(codigo)}`)
      .then(setRes)
      .catch((e: Error) => setError(e.message));
  }, [codigo]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (input.trim()) navigate(`/validar/${input.trim().toUpperCase()}`);
  };

  return (
    <div style={{ maxWidth: 560, margin: '48px auto', padding: '0 16px' }}>
      <div style={{ paddingBottom: 20 }}>
        <Brand light={false} />
      </div>
      <div className="card">
        <h2 style={{ marginBottom: 6 }}>Validar certificado laboral</h2>
        <p className="muted">Ingrese el código impreso en la parte inferior del certificado.</p>
        <form onSubmit={submit} className="actions">
          <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="AMXXXXXXXX" style={{ flex: 1 }} aria-label="Código" />
          <button className="btn primary">Validar</button>
        </form>
        {error && <div className="alert error mt">{error}</div>}
        {res && (
          <div className={`alert mt ${res.valido ? 'success' : 'error'}`}>
            {res.valido ? (
              <>
                <strong>Certificado auténtico y vigente.</strong>
                <dl className="kv mt">
                  <dt>Empresa</dt>
                  <dd>{res.empresa}</dd>
                  <dt>Trabajador</dt>
                  <dd>{res.empleado}</dd>
                  <dt>Cédula</dt>
                  <dd>{res.cedula}</dd>
                  <dt>Cargo</dt>
                  <dd>{res.cargo}</dd>
                  <dt>Emitido</dt>
                  <dd>{fmt.dateTime(res.fechaEmision)}</dd>
                </dl>
              </>
            ) : res.estado === 'ANULADO' ? (
              <strong>Este certificado fue anulado y no es válido.</strong>
            ) : (
              <strong>No existe un certificado con el codigo {res.codigo}.</strong>
            )}
          </div>
        )}
      </div>
      <p className="mt">
        <Link to="/login">Ir a AUTOMINE</Link>
      </p>
    </div>
  );
}
