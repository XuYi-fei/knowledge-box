import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const frontendRoot = path.resolve(__dirname, '..');
const isWindows = process.platform === 'win32';

const [, , command, ...rawArgs] = process.argv;

if (!command || !['dev', 'build', 'preview'].includes(command)) {
  console.error('Usage: node scripts/run-vite-profile.mjs <dev|build|preview> [--profile <name>] [extra vite args]');
  process.exit(1);
}

function extractArg(flagName) {
  const prefixed = `--${flagName}=`;
  const directIndex = rawArgs.findIndex((arg) => arg === `--${flagName}`);
  if (directIndex >= 0) {
    const value = rawArgs[directIndex + 1];
    rawArgs.splice(directIndex, value ? 2 : 1);
    return value;
  }
  const prefixedArg = rawArgs.find((arg) => arg.startsWith(prefixed));
  if (prefixedArg) {
    rawArgs.splice(rawArgs.indexOf(prefixedArg), 1);
    return prefixedArg.slice(prefixed.length);
  }
  return undefined;
}

const explicitMode = extractArg('mode');
const explicitProfile = extractArg('profile');
const npmProfile = process.env.npm_config_profile;
const envProfile = process.env.KB_FRONTEND_PROFILE;
const defaultProfileByCommand = {
  dev: 'development',
  build: 'production',
  preview: 'production',
};

const profile = explicitProfile || npmProfile || envProfile || defaultProfileByCommand[command];
const mode = explicitMode || profile;

function resolveBin(binName) {
  return path.resolve(frontendRoot, 'node_modules', '.bin', isWindows ? `${binName}.cmd` : binName);
}

function runStep(binName, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(resolveBin(binName), args, {
      cwd: frontendRoot,
      stdio: 'inherit',
      env: {
        ...process.env,
        KB_FRONTEND_PROFILE: profile,
      },
    });
    child.on('exit', (code, signal) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`${binName} exited with code ${code ?? 'null'} signal ${signal ?? 'null'}`));
    });
    child.on('error', reject);
  });
}

console.log(`[knowledge-box-frontend] command=${command} profile=${profile} mode=${mode}`);

try {
  if (command === 'build') {
    await runStep('tsc', ['-b']);
  }
  const viteArgs = [command, '--mode', mode, ...rawArgs];
  await runStep('vite', viteArgs);
} catch (error) {
  console.error(`[knowledge-box-frontend] ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
