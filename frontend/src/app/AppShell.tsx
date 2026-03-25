import { Alert } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { API_BASE_URL, buildApiUrl } from '../lib/api';

type BackendProbeState = 'HEALTHY' | 'DEGRADED' | 'UNREACHABLE';

type BackendStatus = {
  state: BackendProbeState;
  errorMessage: string | null;
  httpStatus: number | null;
};

async function probeOne(path: string) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), 5000);
  try {
    return await fetch(buildApiUrl(path), {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
      cache: 'no-store',
      signal: controller.signal,
    });
  } finally {
    window.clearTimeout(timer);
  }
}

async function probeBackendHealth() {
  const response = await probeOne('/api/public/system/availability');
  if (response.status >= 500) {
    return {
      state: 'DEGRADED' as const,
      httpStatus: response.status,
      errorMessage: `HTTP ${response.status}`,
    };
  }
  return {
    state: 'HEALTHY' as const,
    httpStatus: response.status,
    errorMessage: null,
  };
}

function resolveProbeError(error: unknown) {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return 'request timeout';
  }
  if (error instanceof Error) {
    return error.message || 'unknown error';
  }
  return 'unknown error';
}

export function AppShell({ children }: { children: ReactNode }) {
  const [backendStatus, setBackendStatus] = useState<BackendStatus>({
    state: 'HEALTHY',
    errorMessage: null,
    httpStatus: null,
  });

  useEffect(() => {
    let active = true;
    async function checkBackendHealth() {
      try {
        const result = await probeBackendHealth();
        if (!active) {
          return;
        }
        setBackendStatus({
          state: result.state,
          errorMessage: result.errorMessage,
          httpStatus: result.httpStatus,
        });
      } catch (error) {
        if (!active) {
          return;
        }
        const message = resolveProbeError(error);
        setBackendStatus({
          state: 'UNREACHABLE',
          errorMessage: message,
          httpStatus: null,
        });
      }
    }

    void checkBackendHealth();
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="app-shell">
      {backendStatus.state !== 'HEALTHY' ? (
        <div className="app-shell-health-card">
          <Alert
            showIcon
            type={backendStatus.state === 'DEGRADED' ? 'warning' : 'error'}
            message={backendStatus.state === 'DEGRADED' ? '后端可达但状态异常' : '后端服务不可达'}
            description={
              backendStatus.state === 'DEGRADED'
                ? `服务返回异常状态（${backendStatus.httpStatus ?? '-'}）。页面继续可用，请按需重试关键操作。`
                : `无法连接 ${API_BASE_URL}（${backendStatus.errorMessage ?? 'network error'}）`
            }
          />
        </div>
      ) : null}
      <main className="app-shell-main">
        <div className="app-shell-main-content">{children}</div>
        <footer className="app-shell-footer">
          <a href="https://beian.miit.gov.cn/" target="_blank" rel="noreferrer">
            备案号:冀ICP备2023036490号-1
          </a>
        </footer>
      </main>
    </div>
  );
}
