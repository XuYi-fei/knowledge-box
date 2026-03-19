import type { UserAuthResponse, UserView } from './types';

const ADMIN_STORAGE_KEY = 'knowledge-box-admin-basic-auth';
const USER_STORAGE_KEY = 'knowledge-box-user-auth';

export function getAdminAuthToken() {
  return localStorage.getItem(ADMIN_STORAGE_KEY);
}

export function getAdminUsernameFromToken(token: string | null) {
  if (!token) {
    return null;
  }
  try {
    const decoded = atob(token);
    const separator = decoded.indexOf(':');
    if (separator <= 0) {
      return null;
    }
    const username = decoded.slice(0, separator).trim();
    return username.length > 0 ? username : null;
  } catch {
    return null;
  }
}

export function getAdminAuthUsername() {
  return getAdminUsernameFromToken(getAdminAuthToken());
}

export function buildAdminAuthToken(username: string, password: string) {
  return btoa(`${username}:${password}`);
}

export function setAdminAuthToken(username: string, password: string) {
  const token = buildAdminAuthToken(username, password);
  localStorage.setItem(ADMIN_STORAGE_KEY, token);
}

export function clearAdminAuthToken() {
  localStorage.removeItem(ADMIN_STORAGE_KEY);
}

type StoredUserAuth = {
  accessToken: string;
  expiresAt: string;
  user: UserView;
};

function getUserStorage() {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as StoredUserAuth;
  } catch {
    localStorage.removeItem(USER_STORAGE_KEY);
    return null;
  }
}

export function setUserAuthSession(session: UserAuthResponse) {
  localStorage.setItem(
    USER_STORAGE_KEY,
    JSON.stringify({
      accessToken: session.accessToken,
      expiresAt: session.expiresAt,
      user: session.user,
    } satisfies StoredUserAuth),
  );
}

export function getUserAccessToken() {
  return getUserStorage()?.accessToken ?? null;
}

export function getCurrentUser() {
  return getUserStorage()?.user ?? null;
}

export function clearUserAuthSession() {
  const currentUser = getCurrentUser();
  if (currentUser) {
    localStorage.removeItem(buildLastSessionKey(currentUser.id));
  }
  localStorage.removeItem(USER_STORAGE_KEY);
}

function buildLastSessionKey(userId: number) {
  return `knowledge-box-active-session:${userId}`;
}

function buildDebugLastSessionKey(userId: number, profileCode: string) {
  return `knowledge-box-agent-debug-active-session:${userId}:${profileCode}`;
}

export function setUserLastSessionId(userId: number, sessionId: string) {
  localStorage.setItem(buildLastSessionKey(userId), sessionId);
}

export function getUserLastSessionId(userId: number) {
  return localStorage.getItem(buildLastSessionKey(userId));
}

export function setUserDebugLastSessionId(userId: number, profileCode: string, sessionId: string) {
  localStorage.setItem(buildDebugLastSessionKey(userId, profileCode), sessionId);
}

export function getUserDebugLastSessionId(userId: number, profileCode: string) {
  return localStorage.getItem(buildDebugLastSessionKey(userId, profileCode));
}
