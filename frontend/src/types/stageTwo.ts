import type { RoleCode } from './stageOne';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthMenu {
  name: string;
  path: string;
  icon: string;
}

export type RawAuthMenu = string | AuthMenu;

export interface AuthUser {
  userId: string;
  displayName: string;
  roles: RoleCode[];
  menus: AuthMenu[];
}

export interface LoginResponse extends AuthUser {
  token: string;
}

export interface AuthSession {
  token: string;
  user: AuthUser;
}

export interface BackendAuthResponse {
  token: string;
  userId: string;
  displayName: string;
  roles: string[];
  menus: RawAuthMenu[];
}

export interface DemoAccount {
  username: string;
  password: string;
  userId: string;
  displayName: string;
  roles: RoleCode[];
  menus: RawAuthMenu[];
}
