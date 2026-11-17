import { defineConfig } from 'vite'
import type { ProxyOptions } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

function createBackendProxy(): ProxyOptions {
  return {
    target: 'http://127.0.0.1:8080',
    changeOrigin: true,
    configure: (proxy) => {
      proxy.on('error', (err, _req, res) => {
        if ('writeHead' in res && !res.headersSent) {
          res.writeHead(503, { 'Content-Type': 'application/json' })
          res.end(
            JSON.stringify({
              status: 'DOWN',
              message: `Backend API (:8080) is offline (${err.message}). Start backend first.`,
            })
          )
        }
      })
    },
  }
}

function createHardwareAgentProxy(): ProxyOptions {
  return {
    target: 'http://127.0.0.1:9001',
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/hw-agent/, ''),
    configure: (proxy) => {
      proxy.on('error', (err, req, res) => {
        if ('writeHead' in res && !res.headersSent) {
          const isHealth = req.url === '/health' || req.url === '/hw-agent/health';
          res.writeHead(isHealth ? 200 : 503, { 'Content-Type': 'application/json' });
          res.end(
            JSON.stringify({
              status: 'DOWN',
              message: `Hardware agent (:9001) is offline (${err.message}).`,
            })
          );
        }
      });
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': createBackendProxy(),
      '/actuator': createBackendProxy(),
      '/hw-agent': createHardwareAgentProxy(),
    },
  },
})
