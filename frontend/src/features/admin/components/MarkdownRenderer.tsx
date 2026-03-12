import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Image } from 'antd';
import type { CSSProperties } from 'react';

type MarkdownRendererProps = {
  content: string;
  className?: string;
};

function decodeBasicHtmlEntities(value: string) {
  return value
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'");
}

function normalizeFontTagAttributes(attributes: string) {
  return decodeBasicHtmlEntities(attributes)
    .replace(/\\"/g, '"')
    .replace(/\\'/g, "'");
}

function normalizeFontMarkup(content: string) {
  if (!content) {
    return content;
  }
  const withDecodedEntityFontTags = content.replace(
    /&lt;font\b([\s\S]*?)&gt;([\s\S]*?)&lt;\/font&gt;/gi,
    (_, rawAttributes: string, rawText: string) => {
      const normalizedAttributes = normalizeFontTagAttributes(rawAttributes);
      const normalizedText = decodeBasicHtmlEntities(rawText);
      return `<font${normalizedAttributes}>${normalizedText}</font>`;
    },
  );
  return withDecodedEntityFontTags.replace(/<font\b([^>]*)>/gi, (_, rawAttributes: string) => {
    const normalizedAttributes = normalizeFontTagAttributes(rawAttributes);
    return `<font${normalizedAttributes}>`;
  });
}

function toCamelCaseCssProperty(property: string) {
  return property.trim().replace(/-([a-z])/g, (_, letter: string) => letter.toUpperCase());
}

function parseInlineStyle(style: unknown) {
  if (style && typeof style === 'object') {
    return style as CSSProperties;
  }
  if (typeof style !== 'string' || !style.trim()) {
    return undefined;
  }
  const result: Record<string, string> = {};
  for (const declaration of style.split(';')) {
    const separatorIndex = declaration.indexOf(':');
    if (separatorIndex <= 0) {
      continue;
    }
    const property = declaration.slice(0, separatorIndex).trim();
    const value = declaration.slice(separatorIndex + 1).trim();
    if (!property || !value) {
      continue;
    }
    result[toCamelCaseCssProperty(property)] = value;
  }
  return Object.keys(result).length > 0 ? (result as CSSProperties) : undefined;
}

type ParsedInlineFontCode = {
  style?: CSSProperties;
  text: string;
  strong: boolean;
};

function parseInlineFontCode(code: string): ParsedInlineFontCode | null {
  const raw = code.trim();
  const strongWrapped = raw.startsWith('**') && raw.endsWith('**') && raw.length > 4;
  const candidate = strongWrapped ? raw.slice(2, -2).trim() : raw;
  const normalizedCandidate = normalizeFontMarkup(decodeBasicHtmlEntities(candidate));
  const normalizedForMatch = normalizedCandidate
    .replace(/\\"/g, '"')
    .replace(/\\'/g, "'");
  const match = normalizedForMatch.match(/^<font\b([^>]*)>([\s\S]*?)<\/font>$/i);
  if (!match) {
    return null;
  }
  const attrs = match[1] ?? '';
  const text = match[2] ?? '';
  const styleMatch = attrs.match(/\bstyle\s*=\s*(["'])([\s\S]*?)\1/i);
  const colorMatch = attrs.match(/\bcolor\s*=\s*(["'])([\s\S]*?)\1/i);
  const mergedStyle = {
    ...(parseInlineStyle(styleMatch?.[2]) ?? {}),
    ...(colorMatch?.[2]?.trim() ? { color: colorMatch[2].trim() } : {}),
  } as CSSProperties;
  return {
    style: Object.keys(mergedStyle).length > 0 ? mergedStyle : undefined,
    text,
    strong: strongWrapped,
  };
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  const normalizedContent = normalizeFontMarkup(content);
  const components = {
    font: ({ style, color, children }: any) => {
      const mergedStyle = {
        ...(parseInlineStyle(style) ?? {}),
        ...(typeof color === 'string' && color.trim() ? { color: color.trim() } : {}),
      } as CSSProperties;
      return <span style={Object.keys(mergedStyle).length > 0 ? mergedStyle : undefined}>{children}</span>;
    },
    span: ({ style, children }: any) => (
      <span style={parseInlineStyle(style)}>
        {children}
      </span>
    ),
    a: ({ href, children }: any) => (
      <a href={href} target="_blank" rel="noreferrer">
        {children}
      </a>
    ),
    img: ({ src, alt }: any) => {
      if (!src) {
        return null;
      }
      return (
        <Image
          src={src}
          alt={alt ?? ''}
          className="admin-markdown-image"
          preview={{
            mask: '点击查看大图',
          }}
        />
      );
    },
    code(props: any) {
      const { className: codeClassName, children } = props;
      const language = /language-(\w+)/.exec(codeClassName ?? '')?.[1];
      const code = String(children).replace(/\n$/, '');
      if (!language) {
        const inlineFontCode = parseInlineFontCode(code);
        if (inlineFontCode) {
          const element = <span style={inlineFontCode.style}>{inlineFontCode.text}</span>;
          return inlineFontCode.strong ? <strong>{element}</strong> : element;
        }
        return <code className="admin-inline-code">{code}</code>;
      }
      return (
        <SyntaxHighlighter
          language={language}
          style={oneLight}
          PreTag="div"
          customStyle={{
            margin: 0,
            borderRadius: 10,
            padding: '14px',
            fontSize: '0.87rem',
            background: '#f5f8f7',
          }}
        >
          {code}
        </SyntaxHighlighter>
      );
    },
  } as any;

  return (
    <div className={className ? `admin-markdown ${className}` : 'admin-markdown'}>
      <Markdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeRaw]}
        components={components}
      >
        {normalizedContent}
      </Markdown>
    </div>
  );
}
