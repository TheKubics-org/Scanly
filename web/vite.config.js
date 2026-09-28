import { defineConfig } from 'vite';

export default defineConfig({
  root: '.',
  base: './',
  publicDir: 'public',
  build: {
    outDir: '../docs',
    emptyOutDir: true,
    cssMinify: true,
  },
  server: {
    port: 3000,
    open: true,
  },
});
