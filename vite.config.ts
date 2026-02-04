import { defineConfig } from "vitest/config";
import { resolve } from "path";

export default defineConfig({
  build: {
    minify: true,
    lib: {
      entry: {
        index: "index.ts",
      },
      formats: ["es", "cjs"],
    },
    rollupOptions: {
      output: {
        dir: "./dist",
        exports: "named",
        interop: "compat",
      },
    },
  },
  resolve: {
    alias: {
      "@": resolve(__dirname, "./src"),
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    include: ["test/**/*.spec.(j|t)s"],
    watch: false,
  },
});
