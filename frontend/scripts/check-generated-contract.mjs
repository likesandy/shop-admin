import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import openapiTS, {astToString} from 'openapi-typescript';
import ts from 'typescript';

const directory = fileURLToPath(new URL('../../backend/admin-generated-example/contract/', import.meta.url));
const contract = JSON.parse(fs.readFileSync(path.join(directory, 'openapi.json'), 'utf8'));
const schemaPath = path.join(directory, 'schema.d.ts');
const clientPath = path.join(directory, 'client.ts');
const options = {strict: true, noEmit: true, skipLibCheck: true, target: ts.ScriptTarget.ES2022,
  module: ts.ModuleKind.ESNext, moduleResolution: ts.ModuleResolutionKind.Bundler, types: []};

async function compile(document) {
  const schema = astToString(await openapiTS(document));
  const host = ts.createCompilerHost(options);
  const readFile = host.readFile.bind(host);
  const fileExists = host.fileExists.bind(host);
  host.readFile = name => path.resolve(name) === schemaPath ? schema : readFile(name);
  host.fileExists = name => path.resolve(name) === schemaPath || fileExists(name);
  return ts.getPreEmitDiagnostics(ts.createProgram([clientPath, schemaPath], options, host))
    .filter(d => d.category === ts.DiagnosticCategory.Error);
}

const baseline = await compile(contract);
if (baseline.length) throw new Error(ts.formatDiagnosticsWithColorAndContext(baseline, {
  getCanonicalFileName: f => f, getCurrentDirectory: () => directory, getNewLine: () => '\n',
}));
console.log('PASS: generated API typed client compiles against its reviewed OpenAPI');
for (const [name, mutate] of [
  ['rename title', input => {
    input.properties.heading = input.properties.title;
    delete input.properties.title;
    input.required = input.required.map(field => field === 'title' ? 'heading' : field);
  }],
  ['change content type', input => { input.properties.content = {type: 'integer'}; }],
  ['add mandatory field', input => {
    input.properties.priority = {type: 'integer'};
    input.required.push('priority');
  }],
]) {
  const changed = structuredClone(contract);
  mutate(changed.components.schemas.NoteInput);
  const errors = (await compile(changed)).filter(d => d.file?.fileName === clientPath);
  if (!errors.length) throw new Error(`Generated contract gate accepted breaking change: ${name}`);
  console.log(`PASS: generated client rejects ${name}`);
}
