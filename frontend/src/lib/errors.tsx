export type ApiErrorPayload = {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
};

export class ApiRequestError extends Error {
  status?: number;
  code?: string;
  path?: string;
  fieldErrors?: Record<string, string>;

  constructor(message: string, options?: Omit<ApiRequestError, 'name' | 'message'>) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = options?.status;
    this.code = options?.code;
    this.path = options?.path;
    this.fieldErrors = options?.fieldErrors;
  }
}

export function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
  if (!value || typeof value !== 'object') {
    return false;
  }
  return 'message' in value || 'status' in value || 'code' in value;
}

export function buildErrorSummary(error: unknown, fallback: string) {
  if (!(error instanceof ApiRequestError) || !error.fieldErrors || !Object.keys(error.fieldErrors).length) {
    return error instanceof Error ? error.message : fallback;
  }

  const fieldSummary = Object.entries(error.fieldErrors)
    .map(([field, message]) => `${field}: ${message}`)
    .join('\n');

  return `${error.message}\n\n${fieldSummary}`;
}
