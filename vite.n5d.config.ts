import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  base: './',
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { host: '0.0.0.0', port: 5191 },
  build: {
    target: 'es2020', cssTarget:'chrome52', outDir: 'dist-n5d',
    rollupOptions: { input: 'n5d.html' }
  }
})
