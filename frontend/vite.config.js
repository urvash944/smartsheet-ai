import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Vite configuration file.
// "plugins" adds React support.
// "server.proxy" forwards API calls to our Spring Boot backend during development.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Any request starting with /api is sent to the Java backend on port 8080
      '/api': 'http://localhost:8080',
    },
  },
});