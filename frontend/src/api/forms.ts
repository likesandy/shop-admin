import type {components} from './schema';

type Schema = components['schemas'];
// UI values are deliberately independent of the generated API types: changing the
// contract must fail at these explicit mappings, rather than silently change a form.
export type LoginValues = {username: string; password: string};
export type PasswordValues = {oldPassword: string; newPassword: string};
export type RecordValues = {
  username?: string;
  displayName?: string;
  deptId?: number;
  enabled?: boolean;
  password?: string;
  code?: string;
  name?: string;
  dataScope?: string;
  permissionIds?: number[];
  parentId?: number;
  type?: string;
  path?: string;
  label?: string;
  value?: string;
  sort?: number;
};

function required<T>(value: T | undefined, field: string): T {
  if (value === undefined) throw new Error(`请填写${field}`);
  return value;
}

export const loginBody = (v: LoginValues) => ({username: v.username, password: v.password} satisfies Schema['Login']);
export const passwordBody = (v: PasswordValues) => ({oldPassword: v.oldPassword, newPassword: v.newPassword} satisfies Schema['PasswordInput']);
export const userBody = (v: RecordValues) => ({
  username: required(v.username, '用户名'), displayName: required(v.displayName, '姓名'),
  deptId: required(v.deptId, '所属部门'), enabled: required(v.enabled, '启用状态'), password: v.password,
} satisfies Schema['UserInput']);
export const roleBody = (v: RecordValues) => ({
  code: required(v.code, '角色编码'), name: required(v.name, '角色名称'),
  dataScope: required(v.dataScope, '数据范围'), enabled: required(v.enabled, '启用状态'),
  permissionIds: required(v.permissionIds, '权限'),
} satisfies Schema['RoleInput']);
export const departmentBody = (v: RecordValues) => ({
  name: required(v.name, '部门名称'), parentId: required(v.parentId, '上级部门'),
} satisfies Schema['DeptInput']);
export const permissionBody = (v: RecordValues) => ({
  code: required(v.code, '权限编码'), name: required(v.name, '权限名称'),
  type: required(v.type, '权限类型'), path: v.path, parentId: v.parentId,
} satisfies Schema['PermissionInput']);
export const dictionaryBody = (v: RecordValues) => ({
  type: required(v.type, '字典类型'), label: required(v.label, '显示文本'), value: required(v.value, '字典值'),
  sort: required(v.sort, '排序'), enabled: required(v.enabled, '启用状态'),
} satisfies Schema['DictInput']);
