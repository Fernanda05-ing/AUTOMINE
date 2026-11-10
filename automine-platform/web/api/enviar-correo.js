import { timingSafeEqual } from 'node:crypto';
import nodemailer from 'nodemailer';

// Puente de correo: Render gratis bloquea SMTP, asi que la API le pide a esta funcion
// que envie por Gmail. Exige MAIL_RELAY_SECRET para que nadie mas pueda usarla.
function claveValida(recibida, esperada) {
  if (typeof recibida !== 'string' || !esperada || esperada.length < 16) return false;
  const a = Buffer.from(recibida);
  const b = Buffer.from(esperada);
  return a.length === b.length && timingSafeEqual(a, b);
}

export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ message: 'Método no permitido' });
  if (!claveValida(req.headers['x-relay-secret'], process.env.MAIL_RELAY_SECRET)) {
    return res.status(401).json({ message: 'No autorizado' });
  }
  const { to, subject, html, text, fromName } = req.body ?? {};
  if (typeof to !== 'string' || !/^[^\s@,;<>]+@[^\s@,;<>]+$/.test(to) || typeof subject !== 'string') {
    return res.status(400).json({ message: 'Datos del correo inválidos' });
  }
  const user = process.env.MAIL_USERNAME;
  const pass = (process.env.MAIL_PASSWORD ?? '').replace(/\s/g, '');
  if (!user || !pass) return res.status(500).json({ message: 'Faltan MAIL_USERNAME o MAIL_PASSWORD en Vercel' });
  try {
    const transporte = nodemailer.createTransport({ host: 'smtp.gmail.com', port: 465, secure: true, auth: { user, pass } });
    await transporte.sendMail({ from: { name: fromName || 'AUTOMINE', address: user }, to, subject, html, text });
    return res.status(200).json({ ok: true });
  } catch (e) {
    return res.status(502).json({ message: `Gmail rechazó el envío: ${e.message}` });
  }
}
