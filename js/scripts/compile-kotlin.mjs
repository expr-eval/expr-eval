import { spawnSync } from "node:child_process";
import { fileURLToPath, URL } from "node:url";
import process from "node:process";

const root = fileURLToPath(new URL("../../", import.meta.url));
const task = ":library:jsProductionLibraryCompileSync";
const windows = process.platform === "win32";
const result = spawnSync(
  windows ? "cmd.exe" : "./gradlew",
  windows ? ["/d", "/c", "gradlew.bat", task] : [task],
  { cwd: root, stdio: "inherit" },
);
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
