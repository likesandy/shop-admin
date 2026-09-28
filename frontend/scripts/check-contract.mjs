import ts from 'typescript';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const configPath = path.join(root, 'tsconfig.json');
const config = ts.readConfigFile(configPath, ts.sys.readFile);
if (config.error) throw new Error(ts.flattenDiagnosticMessageText(config.error.messageText, '\n'));
const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, root);
const schemaPath = path.join(root, 'src/api/schema.d.ts');
const source = ts.sys.readFile(schemaPath);
const format = {getCanonicalFileName: f => f, getCurrentDirectory: () => root, getNewLine: () => '\n'};

function compile(schema) {
  const host = ts.createCompilerHost(parsed.options);
  const readFile = host.readFile.bind(host);
  host.readFile = filename => path.resolve(filename) === schemaPath ? schema : readFile(filename);
  const program = ts.createProgram(parsed.fileNames, {...parsed.options, noEmit: true}, host);
  return [...parsed.errors, ...ts.getPreEmitDiagnostics(program)].filter(d => d.category === ts.DiagnosticCategory.Error);
}

const baseline = compile(source);
if (baseline.length) throw new Error(ts.formatDiagnosticsWithColorAndContext(baseline, format));

// Mutate only the in-memory generated contract. The real UI/request mappings must
// reject each breaking change; no fixture client or repository file is modified.
for (const [name, schema, before, after] of [
  ['rename user field', 'UserInput', 'displayName: string;', 'fullName: string;'],
  ['change department ID type', 'UserInput', 'deptId?: number;', 'deptId?: string;'],
  ['add required input', 'UserInput', 'username: string;', 'username: string; employeeCode: string;'],
  ['rename password field', 'PasswordInput', 'newPassword: string;', 'replacementPassword: string;'],
]) {
  const block = new RegExp(`(        ${schema}: \\{)([\\s\\S]*?)(        \\};)`);
  const match = source.match(block);
  if (!match || !match[2].includes(before)) throw new Error(`Mutation no longer applies: ${name}`);
  const mutated = source.replace(block, (_, start, body, end) => start + body.replace(before, after) + end);
  const errors = compile(mutated).filter(d => d.file?.fileName.endsWith('/src/api/forms.ts'));
  if (!errors.length) throw new Error(`Contract gate failed: UI accepted ${name}`);
  console.log(`PASS: ${name} rejected by the production form mappings`);
}
