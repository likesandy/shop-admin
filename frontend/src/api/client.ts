import createClient from 'openapi-fetch';
import type {paths} from './schema';
export const client=createClient<paths>({baseUrl:''});
let token=sessionStorage.getItem('shop-token')||'';
export function setToken(value:string){token=value;if(value)sessionStorage.setItem('shop-token',value);else sessionStorage.removeItem('shop-token');}
client.use({onRequest({request}){if(token)request.headers.set('Authorization',`Bearer ${token}`);return request;},onResponse({response}){if(response.status===401 && !response.url.endsWith('/login')){setToken('');window.dispatchEvent(new Event('session-expired'));}return response;}});
export function unwrap<T>(result:{data?:{data?:T;message?:string};error?:unknown}):T{if(result.error||!result.data)throw new Error((result.error as {message?:string})?.message||'请求失败');return result.data.data as T;}
export const hasToken=()=>!!token;
