// Tema de la interfaz: claro por defecto; el usuario puede activar el oscuro.
// La preferencia se guarda solo en este navegador.
export type Tema = 'light' | 'dark';

const KEY = 'automine.tema';

export function temaGuardado(): Tema {
  try {
    return localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

export function aplicarTema(tema: Tema) {
  document.documentElement.dataset.theme = tema;
  try {
    localStorage.setItem(KEY, tema);
  } catch {
    /* almacenamiento no disponible */
  }
}
