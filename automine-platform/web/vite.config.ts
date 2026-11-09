import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo la API corre en 8081; Vite reenvia /AutomineApi para evitar CORS.
// En produccion la web se compila dentro de la API (mismo dominio).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/AutomineApi': 'http://localhost:8081',
    },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 900,
  },
});
