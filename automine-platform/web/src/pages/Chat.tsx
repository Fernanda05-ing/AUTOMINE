import { MessageSquarePlus, Phone, Search, Send } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Modal, PageHeader, useToast } from '../components/ui';
import WhatsApp, { formatoCelular } from '../components/WhatsApp';
import { iniciales } from '../components/Layout';

interface Contacto {
  id: number;
  nombre: string;
  rol: string;
  rolNombre: string;
  cargo: string | null;
  telefono: string | null;
}
interface Conversacion {
  contacto: Contacto;
  ultimoMensaje: string;
  fecha: string;
  ultimoEsMio: boolean;
  noLeidos: number;
}
interface Mensaje {
  id: number;
  remitenteId: number;
  texto: string;
  leido: boolean;
  fecha: string;
  mio: boolean;
}

function hora(iso: string) {
  const d = new Date(iso);
  const hoy = new Date();
  return d.toDateString() === hoy.toDateString()
    ? d.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('es-CO', { day: '2-digit', month: 'short' });
}

/** Chat interno: conversaciones a la izquierda, mensajes a la derecha. */
export default function Chat() {
  const { user } = useAuth();
  const toast = useToast();
  const [conversaciones, setConversaciones] = useState<Conversacion[]>([]);
  const [activo, setActivo] = useState<Contacto | null>(null);
  const [mensajes, setMensajes] = useState<Mensaje[]>([]);
  const [texto, setTexto] = useState('');
  const [filtro, setFiltro] = useState('');
  const [nuevo, setNuevo] = useState(false);
  const [contactos, setContactos] = useState<Contacto[]>([]);
  const [busquedaContacto, setBusquedaContacto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const fin = useRef<HTMLDivElement>(null);

  const cargarConversaciones = useCallback(() => {
    api.get<Conversacion[]>('/Chat/Conversations').then(setConversaciones).catch(() => {});
  }, []);

  // Conversaciones: al entrar y cada 10 segundos
  useEffect(() => {
    cargarConversaciones();
    const t = setInterval(cargarConversaciones, 10000);
    return () => clearInterval(t);
  }, [cargarConversaciones]);

  // Mensajes de la conversacion abierta: carga inicial y luego solo los nuevos cada 4 segundos
  useEffect(() => {
    if (!activo) return;
    let cancelado = false;
    let ultimo = 0;
    setMensajes([]);
    api.get<Mensaje[]>(`/Chat/Messages/${activo.id}`).then((m) => {
      if (cancelado) return;
      setMensajes(m);
      ultimo = m.length ? m[m.length - 1].id : 0;
      cargarConversaciones();
    });
    const t = setInterval(() => {
      api
        .get<Mensaje[]>(`/Chat/Messages/${activo.id}`, { despues: ultimo })
        .then((m) => {
          if (cancelado || m.length === 0) return;
          ultimo = m[m.length - 1].id;
          setMensajes((prev) => [...prev, ...m.filter((x) => !prev.some((p) => p.id === x.id))]);
          cargarConversaciones();
        })
        .catch(() => {});
    }, 4000);
    return () => {
      cancelado = true;
      clearInterval(t);
    };
  }, [activo, cargarConversaciones]);

  useEffect(() => fin.current?.scrollIntoView({ block: 'end' }), [mensajes]);

  const enviar = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!activo || !texto.trim()) return;
    setEnviando(true);
    try {
      const m = await api.post<Mensaje>('/Chat/Messages', { destinatarioId: activo.id, texto: texto.trim() });
      setMensajes((prev) => [...prev, m]);
      setTexto('');
      cargarConversaciones();
    } catch (err) {
      toast.error((err as Error).message);
    } finally {
      setEnviando(false);
    }
  };

  const abrirNuevo = async () => {
    setNuevo(true);
    try {
      setContactos(await api.get<Contacto[]>('/Chat/Contacts'));
    } catch (err) {
      toast.error((err as Error).message);
    }
  };

  const lista = useMemo(() => {
    const q = filtro.trim().toLowerCase();
    return q ? conversaciones.filter((c) => c.contacto.nombre.toLowerCase().includes(q)) : conversaciones;
  }, [conversaciones, filtro]);

  const contactosFiltrados = contactos.filter((c) =>
    `${c.nombre} ${c.rolNombre} ${c.cargo ?? ''}`.toLowerCase().includes(busquedaContacto.trim().toLowerCase()),
  );

  return (
    <>
      <PageHeader title="Mensajes" subtitle="Chat interno entre el personal y los trabajadores de la empresa">
        <button className="btn primary" onClick={() => void abrirNuevo()}>
          <MessageSquarePlus /> Nueva conversación
        </button>
      </PageHeader>
      <div className="chat">
        <aside className={`chat-lista ${activo ? 'oculta-movil' : ''}`}>
          <div className="input-icon chat-buscar">
            <Search aria-hidden />
            <input placeholder="Buscar conversación" value={filtro} onChange={(e) => setFiltro(e.target.value)} aria-label="Buscar conversación" />
          </div>
          {lista.length === 0 && (
            <p className="muted" style={{ padding: 16 }}>
              Aún no tiene conversaciones. Pulse <strong>Nueva conversación</strong> para escribirle a alguien.
            </p>
          )}
          {lista.map((c) => (
            <button key={c.contacto.id} className={`chat-item ${activo?.id === c.contacto.id ? 'activo' : ''}`} onClick={() => setActivo(c.contacto)}>
              <span className="avatar">{iniciales(c.contacto.nombre)}</span>
              <span className="chat-item-info">
                <span className="chat-item-top">
                  <strong>{c.contacto.nombre}</strong>
                  <small>{hora(c.fecha)}</small>
                </span>
                <span className="chat-item-bottom">
                  <span className="chat-preview">
                    {c.ultimoEsMio ? 'Tú: ' : ''}
                    {c.ultimoMensaje}
                  </span>
                  {c.noLeidos > 0 && <span className="chat-badge">{c.noLeidos}</span>}
                </span>
              </span>
            </button>
          ))}
        </aside>

        <section className={`chat-panel ${activo ? '' : 'oculta-movil'}`}>
          {!activo ? (
            <div className="chat-vacio">
              <MessageSquarePlus size={36} />
              <p>Seleccione una conversación o inicie una nueva.</p>
            </div>
          ) : (
            <>
              <header className="chat-cabecera">
                <button className="btn small chat-volver" onClick={() => setActivo(null)} aria-label="Volver a las conversaciones">
                  ←
                </button>
                <span className="avatar">{iniciales(activo.nombre)}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <strong>{activo.nombre}</strong>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {activo.cargo ?? activo.rolNombre}
                    {activo.telefono && (
                      <>
                        {' · '}
                        <Phone size={12} style={{ verticalAlign: '-1px' }} /> {formatoCelular(activo.telefono)}
                      </>
                    )}
                  </div>
                </div>
                <WhatsApp telefono={activo.telefono} mensaje={`Hola ${activo.nombre.split(' ')[0]}, te escribo de AUTOMINE.`} texto="Urgente" />
              </header>
              <div className="chat-mensajes">
                {mensajes.length === 0 && <p className="muted chat-vacio">Escriba el primer mensaje.</p>}
                {mensajes.map((m) => (
                  <div key={m.id} className={`burbuja ${m.mio ? 'mia' : ''}`}>
                    <span>{m.texto}</span>
                    <small>
                      {hora(m.fecha)}
                      {m.mio && (m.leido ? ' · Leído' : ' · Enviado')}
                    </small>
                  </div>
                ))}
                <div ref={fin} />
              </div>
              <form className="chat-enviar" onSubmit={enviar}>
                <textarea
                  rows={1}
                  value={texto}
                  maxLength={2000}
                  placeholder={`Escribir a ${activo.nombre.split(' ')[0]}... (Enter para enviar)`}
                  onChange={(e) => setTexto(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      void enviar();
                    }
                  }}
                  aria-label="Mensaje"
                />
                <button className="btn primary" disabled={enviando || !texto.trim()} aria-label="Enviar">
                  <Send />
                </button>
              </form>
            </>
          )}
        </section>
      </div>

      {nuevo && (
        <Modal title="Nueva conversación" onClose={() => setNuevo(false)}>
          <div className="input-icon mb">
            <Search aria-hidden />
            <input autoFocus placeholder="Buscar por nombre, rol o cargo" value={busquedaContacto} onChange={(e) => setBusquedaContacto(e.target.value)} />
          </div>
          <div style={{ maxHeight: 380, overflowY: 'auto' }}>
            {contactosFiltrados.map((c) => (
              <button
                key={c.id}
                className="chat-item"
                onClick={() => {
                  setActivo(c);
                  setNuevo(false);
                }}
              >
                <span className="avatar">{iniciales(c.nombre)}</span>
                <span className="chat-item-info">
                  <strong>{c.nombre}</strong>
                  <span className="muted" style={{ fontSize: 12 }}>
                    {c.cargo ?? c.rolNombre}
                    {c.telefono ? ` · ${formatoCelular(c.telefono)}` : ''}
                  </span>
                </span>
              </button>
            ))}
            {contactosFiltrados.length === 0 && <p className="muted">No hay contactos.</p>}
          </div>
          {user?.rol === 'EMPLEADO' && <p className="muted" style={{ fontSize: 12 }}>Puede escribirle al personal administrativo de la empresa.</p>}
        </Modal>
      )}
    </>
  );
}
