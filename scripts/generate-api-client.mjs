#!/usr/bin/env node
// ADR-011: OpenAPI ist die Single Source of Truth. Holt die Spezifikation von einem laufenden
// Backend (`./gradlew bootRun`, lokales Profil) und generiert daraus den TS-Client in
// packages/api-client. Nutzung: node scripts/generate-api-client.mjs [baseUrl]
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const apiClientDir = join(repoRoot, "packages", "api-client");
const baseUrl = process.argv[2] ?? "http://localhost:8080";

const specUrl = `${baseUrl}/v3/api-docs`;
console.log(`Hole OpenAPI-Spezifikation von ${specUrl} ...`);

const response = await fetch(specUrl);
if (!response.ok) {
  console.error(`Fehlgeschlagen: ${response.status} ${response.statusText}. Läuft das Backend (./gradlew bootRun)?`);
  process.exit(1);
}
const spec = await response.text();

mkdirSync(apiClientDir, { recursive: true });
writeFileSync(join(apiClientDir, "openapi.json"), spec);
console.log("openapi.json aktualisiert.");

execFileSync("npx", ["openapi-typescript", "./openapi.json", "-o", "./src/schema.ts"], {
  cwd: apiClientDir,
  stdio: "inherit",
  shell: process.platform === "win32",
});
console.log("src/schema.ts neu generiert.");
