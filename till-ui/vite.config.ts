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
    },
  },
})
