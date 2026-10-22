import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');

const shell = readFileSync(join(here, 'shell.html'), 'utf8').replace(/\r\n/g, '\n');
const model = readFileSync(join(root, 'system-model.json'), 'utf8');
const app = readFileSync(join(here, 'app.js'), 'utf8');

let html = shell.replace('__MODEL_JSON__', () => model);
html = html.replace(/<\/script>\s*<\/body>\s*<\/html>\s*$/, () => `\n${app}\n</script>\n</body>\n</html>\n`);

writeFileSync(join(root, 'system-architecture.html'), html, 'utf8');
console.log(`built ${join(root, 'system-architecture.html')} (${(html.length / 1024).toFixed(1)} KB)`);
