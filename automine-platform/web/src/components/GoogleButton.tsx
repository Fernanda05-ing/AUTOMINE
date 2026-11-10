import { useEffect, useRef, useState } from 'react';
import { api } from '../api';

// Botón oficial "Iniciar sesión con Google" (Google Identity Services).
// Solo se muestra si el servidor tiene GOOGLE_CLIENT_ID configurado.

interface GoogleId {
  initialize: (cfg: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: string }) => void;
  renderButton: (el: HTMLElement, opts: Record<string, unknown>) => void;
}
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
  }
}

const SCRIPT = 'https://accounts.google.com/gsi/client';

function cargarScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  const existente = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT}"]`);
  return new Promise((resolve, reject) => {
    const s = existente ?? document.createElement('script');
    s.addEventListener('load', () => resolve());
    s.addEventListener('error', () => reject(new Error('No se pudo cargar Google')));
    if (!existente) {
      s.src = SCRIPT;
      s.async = true;
      document.head.appendChild(s);
    }
  });
}

export default function GoogleButton({ onCredential }: { onCredential: (credential: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [clientId, setClientId] = useState<string | null>(null);
  const callback = useRef(onCredential);
  callback.current = onCredential;

  useEffect(() => {
    api
      .get<{ googleClientId: string }>('/PublicConfig')
      .then((c) => setClientId(c.googleClientId || null))
      .catch(() => setClientId(null));
  }, []);

  useEffect(() => {
    if (!clientId || !ref.current) return;
    let cancelado = false;
    cargarScript()
      .then(() => {
        if (cancelado || !ref.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (r) => callback.current(r.credential),
        });
        window.google.accounts.id.renderButton(ref.current, {
          theme: 'outline',
          size: 'large',
          shape: 'rectangular',
          text: 'signin_with',
          logo_alignment: 'center',
          locale: 'es',
          width: Math.min(ref.current.offsetWidth || 400, 400),
        });
      })
      .catch(() => {
        /* sin conexion con Google: se oculta el boton */
      });
    return () => {
      cancelado = true;
    };
  }, [clientId]);

  if (!clientId) return null;
  return (
    <>
      <div className="divider-o">
        <span>o</span>
      </div>
      <div ref={ref} className="google-btn" />
    </>
  );
}
