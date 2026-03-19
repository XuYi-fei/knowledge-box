import { CheckOutlined, CopyOutlined, FileMarkdownOutlined } from '@ant-design/icons';
import { App, Button, Space, Tooltip } from 'antd';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { useEffect, useRef, useState } from 'react';

type MarkdownCodeBlockProps = {
  language: string;
  code: string;
  className?: string;
  customStyle?: React.CSSProperties;
  showLineNumbers?: boolean;
};

type CopyMode = 'plain' | 'fenced';

function toFencedMarkdown(code: string, language: string) {
  const normalizedLanguage = language.trim();
  return `\`\`\`${normalizedLanguage}\n${code}\n\`\`\``;
}

async function copyToClipboard(content: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(content);
    return;
  }

  const textarea = document.createElement('textarea');
  textarea.value = content;
  textarea.setAttribute('readonly', 'true');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  document.execCommand('copy');
  document.body.removeChild(textarea);
}

export function MarkdownCodeBlock({ language, code, className, customStyle, showLineNumbers = false }: MarkdownCodeBlockProps) {
  const { message } = App.useApp();
  const [copiedMode, setCopiedMode] = useState<CopyMode | null>(null);
  const copiedTimerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (copiedTimerRef.current !== null) {
        window.clearTimeout(copiedTimerRef.current);
      }
    };
  }, []);

  async function handleCopy(mode: CopyMode) {
    const content = mode === 'fenced' ? toFencedMarkdown(code, language) : code;
    await copyToClipboard(content);
    setCopiedMode(mode);
    if (copiedTimerRef.current !== null) {
      window.clearTimeout(copiedTimerRef.current);
    }
    copiedTimerRef.current = window.setTimeout(() => {
      setCopiedMode(null);
      copiedTimerRef.current = null;
    }, 1600);
    message.success(mode === 'fenced' ? '已复制 Markdown 代码块' : '已复制代码');
  }

  return (
    <div className={className ? `markdown-code-block ${className}` : 'markdown-code-block'}>
      <Space size={6} className="markdown-code-block-toolbar">
        <Tooltip title="复制纯代码，不包含围栏和行号">
          <Button
            size="small"
            type={copiedMode === 'plain' ? 'primary' : 'text'}
            className={`markdown-code-block-copy ${copiedMode === 'plain' ? 'markdown-code-block-copy-active' : ''}`}
            icon={copiedMode === 'plain' ? <CheckOutlined /> : <CopyOutlined />}
            onClick={() => {
              void handleCopy('plain');
            }}
          >
            {copiedMode === 'plain' ? '已复制代码' : '复制代码'}
          </Button>
        </Tooltip>
        <Tooltip title="复制带语言标记的 Markdown fenced code block">
          <Button
            size="small"
            type={copiedMode === 'fenced' ? 'primary' : 'text'}
            className={`markdown-code-block-copy ${copiedMode === 'fenced' ? 'markdown-code-block-copy-active' : ''}`}
            icon={copiedMode === 'fenced' ? <CheckOutlined /> : <FileMarkdownOutlined />}
            onClick={() => {
              void handleCopy('fenced');
            }}
          >
            {copiedMode === 'fenced' ? '已复制 Markdown' : '复制 Markdown'}
          </Button>
        </Tooltip>
      </Space>
      <SyntaxHighlighter
        language={language}
        style={oneLight}
        PreTag="div"
        showLineNumbers={showLineNumbers}
        wrapLongLines
        customStyle={{
          margin: 0,
          borderRadius: 14,
          padding: '16px',
          fontSize: '0.9rem',
          background: '#f3f7f6',
          ...customStyle,
        }}
        lineNumberStyle={{
          minWidth: '2.5em',
          paddingRight: '12px',
          color: 'rgba(16, 42, 43, 0.38)',
          userSelect: 'none',
        }}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  );
}
