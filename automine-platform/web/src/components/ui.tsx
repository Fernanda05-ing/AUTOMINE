import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
  type ReactNode,
} from 'react';
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Inbox,
  Search as SearchIcon,
  TrendingDown,
  TrendingUp,
  X as XIcon,
  XCircle,
  type LucideIcon,
} from 'lucide-react';
import { ApiError } from '../api';
import { fmt } from '../format';

// ------------------------------- Datos -------------------------------

export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const reload = useCallback(() => {
    setLoading(true);
    setError(null);
    fnRef
      .current()
      .then(setData)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(reload, deps);
  return { data, loading, error, reload, setData };
}

/** Estado de formulario sencillo: valores como texto, cambios por nombre. */
export function useForm<T extends Record<string, unknown>>(initial: T) {
  const [values, setValues] = useState<T>(initial);
  const bind = (name: keyof T) => ({
    name: String(name),
    value: (values[name] ?? '') as string,
    onChange: (e: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
      setValues((v) => ({ ...v, [name]: e.target.value })),
  });
  const check = (name: keyof T) => ({
    name: String(name),
    checked: Boolean(values[name]),
    onChange: (e: ChangeEvent<HTMLInputElement>) => setValues((v) => ({ ...v, [name]: e.target.checked })),
  });
  return { values, setValues, bind, check, reset: () => setValues(initial) };
}

// ------------------------------- Toasts -------------------------------

interface Toast {
  id: number;
  text: string;
  error: boolean;
}

const ToastCtx = createContext<{ ok: (t: string) => void; error: (t: string) => void }>({
  ok: () => {},
  error: () => {},
});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const push = useCallback((text: string, error: boolean) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, text, error }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), error ? 6000 : 3500);
  }, []);
  const value = useRef({ ok: (t: string) => push(t, false), error: (t: string) => push(t, true) }).current;
  return (
    <ToastCtx.Provider value={value}>
      {children}
      <div className="toast-area" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.error ? 'error' : ''}`}>
            {t.error ? <XCircle aria-hidden /> : <CheckCircle2 aria-hidden />}
            <span>{t.text}</span>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

export const useToast = () => useContext(ToastCtx);

// ----------------------------- Componentes -----------------------------

export function PageHeader({ title, subtitle, children }: { title: string; subtitle?: string; children?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {children && <div className="actions">{children}</div>}
    </div>
  );
}

export function Spinner({ text = 'Cargando...' }: { text?: string }) {
  return <div className="spinner">{text}</div>;
}

export function ErrorBox({ error }: { error: string | null }) {
  return error ? <div className="alert error">{error}</div> : null;
}

export function Stat({
  label,
  value,
  delta,
  hint,
  icon: Icon,
  tone = '',
  invert,
}: {
  label: string;
  value: ReactNode;
  delta?: number | null;
  hint?: string;
  icon?: LucideIcon;
  tone?: '' | 'ok' | 'info' | 'danger';
  /** Para indicadores donde subir es malo (gastos, costos). */
  invert?: boolean;
}) {
  const bueno = delta !== undefined && delta !== null && (invert ? delta <= 0 : delta >= 0);
  return (
    <div className="stat">
      {Icon && (
        <div className={`stat-icon ${tone}`}>
          <Icon aria-hidden />
        </div>
      )}
      <div className="stat-body">
        <div className="label">{label}</div>
        <div className="value">{value}</div>
        {delta !== undefined && delta !== null && (
          <div className={`delta ${bueno ? 'up' : 'down'}`} title="Frente al mismo periodo del mes anterior">
            {delta >= 0 ? <TrendingUp /> : <TrendingDown />}
            {delta >= 0 ? '+' : ''}
            {fmt.num(delta)} %
          </div>
        )}
        {hint && <div className="hint">{hint}</div>}
      </div>
    </div>
  );
}

const TONES: Record<string, string> = {
  ACTIVO: 'ok', VIGENTE: 'ok', APROBADA: 'ok', APROBADO: 'ok', PAGADA: 'ok', PAGADO: 'ok', OPERATIVA: 'ok',
  REALIZADA: 'ok', CERRADO: 'ok', OK: 'ok', BAJO: 'ok', LEVE: 'info', INCIDENTE: 'info', PROGRAMADA: 'info',
  INACTIVO: 'danger', ANULADA: 'danger', ANULADO: 'danger', FUERA_SERVICIO: 'danger', CANCELADA: 'danger',
  GRAVE: 'danger', MORTAL: 'danger', CRITICO: 'danger', ALTA: 'danger', ACCIDENTE: 'danger', TERMINADO: 'danger',
  PENDIENTE: 'warn', ABIERTO: 'warn', REGISTRADA: 'warn', MANTENIMIENTO: 'warn', MODERADO: 'warn', ALTO: 'warn',
  MEDIO: 'warn', MEDIA: 'warn', LOGIN_FALLIDO: 'danger', LOGIN_BLOQUEADO: 'danger', LOGIN_OK: 'ok', LOGIN_GOOGLE: 'ok',
};

export function Badge({ value, tone }: { value?: string | null; tone?: string }) {
  if (!value) return null;
  return <span className={`badge ${tone ?? TONES[value] ?? ''}`}>{fmt.label(value)}</span>;
}

export interface Column<T> {
  key: string;
  label: string;
  num?: boolean;
  /** Texto largo: se recorta a dos lineas y se ve completo al pasar el mouse. */
  clamp?: boolean;
  render?: (row: T) => ReactNode;
}

const TAMANOS = [10, 25, 50, 100];

/** Quita tildes y pasa a minusculas para buscar sin importar acentos. */
function normalizar(s: string) {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/**
 * Tabla con paginacion (10 por pagina por defecto) y busqueda opcional.
 * `search` indica el texto de cada fila en el que se busca.
 */
export function DataTable<T>({
  columns,
  rows,
  empty = 'No hay registros',
  rowKey,
  pageSize = 10,
  search,
  searchPlaceholder = 'Buscar...',
}: {
  columns: Column<T>[];
  rows: T[] | null | undefined;
  empty?: string;
  rowKey?: (row: T, i: number) => string | number;
  pageSize?: number;
  search?: (row: T) => string;
  searchPlaceholder?: string;
}) {
  const [pagina, setPagina] = useState(0);
  const [tamano, setTamano] = useState(pageSize);
  const [query, setQuery] = useState('');

  const filtradas = useMemo(() => {
    const lista = rows ?? [];
    if (!search || !query.trim()) return lista;
    const q = normalizar(query.trim());
    return lista.filter((r) => normalizar(search(r)).includes(q));
  }, [rows, search, query]);

  const total = filtradas.length;
  const paginas = Math.max(1, Math.ceil(total / tamano));
  const actual = Math.min(pagina, paginas - 1);
  const desde = actual * tamano;
  const visibles = filtradas.slice(desde, desde + tamano);

  // Al cambiar los datos o el filtro, volver a la primera pagina
  useEffect(() => {
    setPagina(0);
  }, [rows, query, tamano]);

  return (
    <div className="table-block">
      {search && (
        <div className="table-search">
          <div className="input-icon">
            <SearchIcon aria-hidden />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={searchPlaceholder} aria-label="Buscar en la tabla" />
          </div>
          {query && <span className="muted">{total} resultado{total === 1 ? '' : 's'}</span>}
        </div>
      )}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c.key} className={c.num ? 'num' : ''}>
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visibles.length === 0 && (
              <tr>
                <td colSpan={columns.length} className="empty">
                  <Inbox aria-hidden />
                  {query ? 'Ningún registro coincide con la búsqueda' : empty}
                </td>
              </tr>
            )}
            {visibles.map((row, i) => (
              <tr key={rowKey ? rowKey(row, desde + i) : ((row as { id?: number }).id ?? desde + i)}>
                {columns.map((c) => {
                  const contenido = c.render ? c.render(row) : String((row as Record<string, unknown>)[c.key] ?? '');
                  return (
                    <td key={c.key} className={c.num ? 'num' : ''}>
                      {c.clamp ? (
                        <span className="clamp" title={typeof contenido === 'string' ? contenido : undefined}>
                          {contenido}
                        </span>
                      ) : (
                        contenido
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {total > TAMANOS[0] && (
        <div className="pager">
          <span className="muted">
            {desde + 1}–{Math.min(desde + tamano, total)} de {total}
          </span>
          <label className="pager-size">
            Mostrar
            <select value={tamano} onChange={(e) => setTamano(Number(e.target.value))}>
              {TAMANOS.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
          <div className="pager-nav">
            <button className="icon-btn" onClick={() => setPagina(0)} disabled={actual === 0} aria-label="Primera página">
              <ChevronsLeft />
            </button>
            <button className="icon-btn" onClick={() => setPagina(actual - 1)} disabled={actual === 0} aria-label="Página anterior">
              <ChevronLeft />
            </button>
            <span className="pager-pos">
              Página {actual + 1} de {paginas}
            </span>
            <button className="icon-btn" onClick={() => setPagina(actual + 1)} disabled={actual >= paginas - 1} aria-label="Página siguiente">
              <ChevronRight />
            </button>
            <button className="icon-btn" onClick={() => setPagina(paginas - 1)} disabled={actual >= paginas - 1} aria-label="Última página">
              <ChevronsRight />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export function Modal({
  title,
  onClose,
  children,
  footer,
  wide,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Cerrar">
            <XIcon />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

/** Modal con formulario: maneja envio, errores de la API y errores por campo. */
export function FormModal({
  title,
  onClose,
  onSubmit,
  children,
  submitLabel = 'Guardar',
  wide,
}: {
  title: string;
  onClose: () => void;
  onSubmit: () => Promise<unknown>;
  children: (fieldErrors: Record<string, string>) => ReactNode;
  submitLabel?: string;
  wide?: boolean;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      await onSubmit();
    } catch (err) {
      setError((err as Error).message);
      if (err instanceof ApiError && err.fields) setFields(err.fields);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose()}>
      <form className={`modal ${wide ? 'wide' : ''}`} onSubmit={submit} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Cerrar">
            <XIcon />
          </button>
        </div>
        <div className="modal-body">
          <ErrorBox error={error} />
          {children(fields)}
        </div>
        <div className="modal-foot">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancelar
          </button>
          <button type="submit" className="btn primary" disabled={busy}>
            {busy ? 'Guardando...' : submitLabel}
          </button>
        </div>
      </form>
    </div>
  );
}

export function Field({
  label,
  error,
  children,
  full,
}: {
  label: string;
  error?: string;
  children: ReactNode;
  full?: boolean;
}) {
  return (
    <div className={`field ${full ? 'full' : ''}`}>
      <label>{label}</label>
      {children}
      {error && <span className="error">{error}</span>}
    </div>
  );
}

export function Tabs<K extends string>({
  tabs,
  active,
  onChange,
}: {
  tabs: { key: K; label: string }[];
  active: K;
  onChange: (k: K) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.key} role="tab" aria-selected={active === t.key} className={active === t.key ? 'active' : ''} onClick={() => onChange(t.key)}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

/** Boton que ejecuta una accion de la API mostrando el resultado en un toast. */
export function ActionButton({
  children,
  action,
  confirm,
  success,
  className = 'btn small',
  onDone,
}: {
  children: ReactNode;
  action: () => Promise<unknown>;
  confirm?: string;
  success?: string;
  className?: string;
  onDone?: () => void;
}) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  return (
    <button
      type="button"
      className={className}
      disabled={busy}
      onClick={async () => {
        if (confirm && !window.confirm(confirm)) return;
        setBusy(true);
        try {
          await action();
          if (success) toast.ok(success);
          onDone?.();
        } catch (e) {
          toast.error((e as Error).message);
        } finally {
          setBusy(false);
        }
      }}
    >
      {busy ? '...' : children}
    </button>
  );
}
