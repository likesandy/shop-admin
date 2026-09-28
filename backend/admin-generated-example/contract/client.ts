// Example consumer of the generated API. schema.d.ts is supplied by the contract gate.
import type {paths} from './schema';

type CreateInput = paths['/api/generated/notes']['post']['requestBody']['content']['application/json'];
type CreateResponse = paths['/api/generated/notes']['post']['responses'][200]['content']['*/*'];

export interface NoteForm {
  title: string;
  content: string;
}

export function noteInput(form: NoteForm): CreateInput {
  return {title: form.title, content: form.content};
}

export async function createNote(baseUrl: string, token: string, form: NoteForm): Promise<CreateResponse> {
  const response = await fetch(`${baseUrl}/api/generated/notes`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json', Authorization: `Bearer ${token}`},
    body: JSON.stringify(noteInput(form)),
  });
  if (!response.ok) throw new Error(`Create note failed: ${response.status}`);
  return response.json() as Promise<CreateResponse>;
}
