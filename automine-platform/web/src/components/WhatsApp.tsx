// Botón "clic para chatear" de WhatsApp: abre WhatsApp (web o app) con el mensaje ya escrito.
// No necesita cuenta de WhatsApp Business ni tiene costo.

/** Convierte un celular colombiano a formato internacional (57 + 10 dígitos) o null si no es válido. */
export function celularWhatsApp(telefono?: string | null): string | null {
  if (!telefono) return null;
  const d = telefono.replace(/\D/g, '');
  if (d.length === 10 && d.startsWith('3')) return '57' + d;
  if (d.length === 12 && d.startsWith('573')) return d;
  return null;
}

/** Muestra el celular con espacios: 300 123 4567. */
export function formatoCelular(telefono?: string | null): string {
  if (!telefono) return '';
  const d = telefono.replace(/\D/g, '').replace(/^57(?=3\d{9}$)/, '');
  return d.length === 10 ? `${d.slice(0, 3)} ${d.slice(3, 6)} ${d.slice(6)}` : telefono;
}

function Icono() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M17.47 14.38c-.3-.15-1.75-.86-2.02-.96-.27-.1-.47-.15-.67.15-.2.3-.77.96-.94 1.16-.17.2-.35.22-.64.07-.3-.15-1.26-.46-2.4-1.48-.89-.79-1.49-1.77-1.66-2.07-.17-.3-.02-.46.13-.6.13-.13.3-.35.45-.52.15-.17.2-.3.3-.5.1-.2.05-.37-.02-.52-.08-.15-.67-1.62-.92-2.22-.24-.58-.49-.5-.67-.51h-.57c-.2 0-.52.07-.79.37-.27.3-1.04 1.02-1.04 2.48s1.07 2.88 1.21 3.08c.15.2 2.1 3.2 5.08 4.49.71.31 1.26.49 1.69.63.71.22 1.36.19 1.87.12.57-.09 1.75-.72 2-1.41.25-.69.25-1.29.17-1.41-.07-.12-.27-.2-.57-.35zM12.05 21.5h-.01a9.4 9.4 0 0 1-4.8-1.31l-.34-.2-3.57.94.95-3.48-.22-.36a9.43 9.43 0 1 1 7.99 4.41zm8.02-17.45A11.27 11.27 0 0 0 12.05.75 11.32 11.32 0 0 0 2.24 17.68L.75 23.25l5.7-1.5a11.3 11.3 0 0 0 5.6 1.43h.01a11.32 11.32 0 0 0 8-19.32z" />
    </svg>
  );
}

/**
 * Enlace a WhatsApp con mensaje. Si el celular no es válido no muestra nada.
 * `soloIcono` lo deja compacto para tablas.
 */
export default function WhatsApp({
  telefono,
  mensaje,
  texto = 'WhatsApp',
  soloIcono = false,
}: {
  telefono?: string | null;
  mensaje?: string;
  texto?: string;
  soloIcono?: boolean;
}) {
  const numero = celularWhatsApp(telefono);
  if (!numero) return null;
  const url = `https://wa.me/${numero}${mensaje ? `?text=${encodeURIComponent(mensaje)}` : ''}`;
  return (
    <a
      className={`wa-btn ${soloIcono ? 'solo' : ''}`}
      href={url}
      target="_blank"
      rel="noreferrer"
      title={`Escribir por WhatsApp a ${formatoCelular(telefono)}`}
      aria-label={`Escribir por WhatsApp a ${formatoCelular(telefono)}`}
    >
      <Icono />
      {!soloIcono && texto}
    </a>
  );
}
