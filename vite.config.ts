import { defineConfig } from "vitest/config";
import { resolve } from "path";
import dts from "vite-plugin-dts";

export default defineConfig({
  plugins: [
    dts({
      tsconfigPath: "./tsconfig.build.json",
      insertTypesEntry: true,
    }),
  ],
  build: {
    minify: true,
    lib: {
      entry: {
        index: "src/index.ts",
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
