// Cliente HTTP de la API AUTOMINE. Guarda el token JWT en localStorage.

const BASE = (import.meta.env.VITE_API_URL ?? '') + '/AutomineApi';
const TOKEN_KEY = 'automine.token';
const USER_KEY = 'automine.user';

export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rol: string;
  rolNombre: string;
  empleadoId: number | null;
  estado: string;
  ultimoAcceso?: string;
  createdAt?: string;
  debeCambiarPassword?: boolean;
  telefono?: string | null;
}

export class ApiError extends Error {
  status: number;
  fields?: Record<string, string>;
  constructor(status: number, message: string, fields?: Record<string, string>) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}

export const session = {
  token(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  user(): Usuario | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as Usuario) : null;
    } catch {
      return null;
    }
  },
  save(token: string, user: Usuario) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    } catch {
      /* almacenamiento no disponible */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch {
      /* nada */
    }
  },
};

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

async function request<T>(method: string, path: string, body?: unknown, isForm = false): Promise<T> {
  const headers: Record<string, string> = {};
  const token = session.token();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  let res: Response;
  try {
    res = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'No hay conexión con el servidor');
  }
  if (res.status === 401 && !path.startsWith('/Login')) {
    session.clear();
    onUnauthorized();
    throw new ApiError(401, 'La sesión vencio. Inicie sesión de nuevo');
  }
  if (!res.ok) {
    let message = `Error ${res.status}`;
    let fields: Record<string, string> | undefined;
    try {
      const data = await res.json();
      message = data.message ?? message;
      fields = data.fields ?? undefined;
    } catch {
      /* respuesta sin JSON */
    }
    throw new ApiError(res.status, message, fields);
  }
  if (res.status === 204) return undefined as T;
  const type = res.headers.get('content-type') ?? '';
  return (type.includes('json') ? await res.json() : undefined) as T;
}

function qs(params?: Record<string, unknown>): string {
  if (!params) return '';
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.append(k, String(v));
  });
  const s = p.toString();
  return s ? `?${s}` : '';
}

export const api = {
  get: <T>(path: string, params?: Record<string, unknown>) => request<T>('GET', path + qs(params)),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {}),
  del: <T>(path: string) => request<T>('DELETE', path),
  upload: <T>(path: string, file: File, extra?: Record<string, string>) => {
    const fd = new FormData();
    fd.append('file', file);
    const q = qs(extra);
    return request<T>('POST', path + q, fd, true);
  },
  /** Envia un formulario con archivos (multipart). */
  form: <T>(method: 'POST' | 'PUT', path: string, fd: FormData) => request<T>(method, path, fd, true),
  /** Abre un archivo autenticado (PDF o imagen) en una pestaña nueva. */
  async open(path: string) {
    const token = session.token();
    const ventana = window.open('', '_blank');
    const res = await fetch(BASE + path, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
    if (!res.ok) {
      ventana?.close();
      let message = `Error ${res.status}`;
      try {
        message = (await res.json()).message ?? message;
      } catch {
        /* nada */
      }
      throw new ApiError(res.status, message);
    }
    const url = URL.createObjectURL(await res.blob());
    if (ventana) ventana.location.href = url;
    else window.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  },
  /** Descarga un archivo (PDF/Excel) autenticado y lo guarda en el equipo. */
  async download(path: string, params?: Record<string, unknown>, fallbackName = 'archivo') {
    const token = session.token();
    const res = await fetch(BASE + path + qs(params), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) {
      let message = `Error ${res.status}`;
      try {
        message = (await res.json()).message ?? message;
      } catch {
        /* nada */
      }
      throw new ApiError(res.status, message);
    }
    const blob = await res.blob();
    const cd = res.headers.get('content-disposition') ?? '';
    const star = /filename\*=UTF-8''([^;]+)/i.exec(cd);
    const plain = /filename="?([^";]+)"?/i.exec(cd);
    const name = star ? decodeURIComponent(star[1]) : plain ? plain[1] : fallbackName;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  },
};
