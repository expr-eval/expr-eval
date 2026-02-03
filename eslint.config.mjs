// @ts-check

import { defineConfig } from "eslint/config";
import eslint from "@eslint/js";
import tseslint from "typescript-eslint";
import prettierRecommended from "eslint-plugin-prettier/recommended";
import globals from "globals";

export default defineConfig(
  eslint.configs.recommended,
  tseslint.configs.recommended,
  prettierRecommended,
  {
    files: ["test/**/*.js"],
    languageOptions: {
      globals: { ...globals.vitest },
    },
    rules: {
      "@typescript-eslint/ban-ts-comment": "off",
    },
  },
);
