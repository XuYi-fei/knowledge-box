import { ApiRequestError, isApiErrorPayload } from './errors';

type StreamJsonOptions<T> = {
  signal?: AbortSignal;
  onEvent: (eventName: string, payload: T) => void;
};

export async function streamJsonSse<T>(input: RequestInfo | URL, init: RequestInit, options: StreamJsonOptions<T>) {
  const response = await fetch(input, {
    ...init,
    signal: options.signal,
  });

  if (!response.ok) {
    const responseText = await response.text();
    let payload: unknown = responseText;
    try {
      payload = responseText ? (JSON.parse(responseText) as unknown) : undefined;
    } catch {
      payload = responseText;
    }

    if (isApiErrorPayload(payload)) {
      throw new ApiRequestError(payload.message ?? `Request failed: ${response.status}`, {
        status: payload.status ?? response.status,
        code: payload.code,
        path: payload.path,
        fieldErrors: payload.fieldErrors,
      });
    }

    throw new ApiRequestError(
      typeof payload === 'string' && payload.trim() ? payload : `Request failed: ${response.status}`,
      { status: response.status },
    );
  }

  if (!response.body) {
    throw new Error('浏览器当前环境不支持流式响应');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let currentEventName = 'message';
  let currentDataLines: string[] = [];

  const emit = () => {
    if (!currentDataLines.length) {
      currentEventName = 'message';
      return;
    }

    const data = currentDataLines.join('\n');
    currentDataLines = [];
    try {
      options.onEvent(currentEventName, JSON.parse(data) as T);
    } finally {
      currentEventName = 'message';
    }
  };

  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });

    let lineBreakIndex = buffer.indexOf('\n');
    while (lineBreakIndex >= 0) {
      const rawLine = buffer.slice(0, lineBreakIndex);
      buffer = buffer.slice(lineBreakIndex + 1);
      const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine;

      if (!line) {
        emit();
      } else if (line.startsWith('event:')) {
        currentEventName = line.slice(6).trim() || 'message';
      } else if (line.startsWith('data:')) {
        currentDataLines.push(line.slice(5).trimStart());
      }

      lineBreakIndex = buffer.indexOf('\n');
    }

    if (done) {
      if (buffer.trim()) {
        const trailing = buffer.endsWith('\r') ? buffer.slice(0, -1) : buffer;
        if (trailing.startsWith('data:')) {
          currentDataLines.push(trailing.slice(5).trimStart());
        }
      }
      emit();
      break;
    }
  }
}
