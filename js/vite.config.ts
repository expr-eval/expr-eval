import { defineConfig } from "vitest/config";

export default defineConfig({
  publicDir: "types",
  build: {
    target: "es2020",
    lib: {
      entry: { index: "npm/index.js", internal: "npm/internal.js" },
      formats: ["es", "cjs"],
    },
    rolldownOptions: { output: { exports: "named" } },
  },
  test: {
    globals: true,
    environment: "jsdom",
    include: ["test/**/*.spec.(j|t)s"],
    watch: false,
  },
});
