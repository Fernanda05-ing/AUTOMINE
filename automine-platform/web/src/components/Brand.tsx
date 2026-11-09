import { useState } from 'react';

/**
 * Marca AUTOMINE: el logo (public/logo.png) en un recuadro pequeno y el nombre al lado.
 * Si el archivo no existe se muestra solo el nombre.
 */
export default function Brand({ size = 'md', tagline = true, light = true }: { size?: 'md' | 'lg'; tagline?: boolean; light?: boolean }) {
  const [sinLogo, setSinLogo] = useState(false);
  return (
    <div className="auth-brand" style={{ gap: size === 'lg' ? 14 : 11 }}>
      {!sinLogo && (
        <div className={`logo-tile ${size === 'lg' ? 'lg' : ''}`}>
          <img src="/logo.png" alt="" onError={() => setSinLogo(true)} />
        </div>
      )}
      <div className="wordmark">
        <strong style={{ fontSize: size === 'lg' ? 22 : 16, color: light ? '#fff' : 'var(--text)' }}>
          AUTO<span>MINE</span>
        </strong>
        {tagline && <small>Gestión minera integral</small>}
      </div>
    </div>
  );
}
