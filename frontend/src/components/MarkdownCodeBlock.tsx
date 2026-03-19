import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { useEffect, useRef, useState } from 'react';

type MarkdownCodeBlockProps = {
  language: string;
  code: string;
  className?: string;
  customStyle?: React.CSSProperties;
};

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

export function MarkdownCodeBlock({ language, code, className, customStyle }: MarkdownCodeBlockProps) {
  const [copied, setCopied] = useState(false);
  const copiedTimerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (copiedTimerRef.current !== null) {
        window.clearTimeout(copiedTimerRef.current);
      }
    };
  }, []);

  async function handleCopy() {
    await copyToClipboard(code);
    setCopied(true);
    if (copiedTimerRef.current !== null) {
      window.clearTimeout(copiedTimerRef.current);
    }
    copiedTimerRef.current = window.setTimeout(() => {
      setCopied(false);
      copiedTimerRef.current = null;
    }, 1600);
  }

  return (
    <div className={className ? `markdown-code-block ${className}` : 'markdown-code-block'}>
      <Tooltip title={copied ? '已复制' : '复制代码'}>
        <Button
          size="small"
          type="text"
          className="markdown-code-block-copy"
          icon={copied ? <CheckOutlined /> : <CopyOutlined />}
          onClick={() => {
            void handleCopy();
          }}
        />
      </Tooltip>
      <SyntaxHighlighter
        language={language}
        style={oneLight}
        PreTag="div"
        customStyle={{
          margin: 0,
          borderRadius: 14,
          padding: '16px',
          fontSize: '0.9rem',
          background: '#f3f7f6',
          ...customStyle,
        }}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  );
}
