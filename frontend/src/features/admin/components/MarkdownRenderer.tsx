import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Image } from 'antd';
import { createElement, type CSSProperties, type ReactNode } from 'react';

type MarkdownRendererProps = {
  content: string;
  className?: string;
  headingIdPrefix?: string;
};

export type MarkdownOutlineItem = {
  id: string;
  text: string;
  level: number;
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

function normalizeHeadingText(text: string) {
  return decodeBasicHtmlEntities(text)
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\[(.*?)\]\((.*?)\)/g, '$1')
    .replace(/[*_~>#-]/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function slugifyHeading(text: string) {
  const normalized = normalizeHeadingText(text)
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return normalized || 'section';
}

function resolveHeadingId(base: string, counter: Map<string, number>) {
  const current = counter.get(base) ?? 0;
  counter.set(base, current + 1);
  return current === 0 ? base : `${base}-${current + 1}`;
}

function flattenNodeText(node: ReactNode): string {
  if (node == null || typeof node === 'boolean') {
    return '';
  }
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map((item) => flattenNodeText(item)).join('');
  }
  if (typeof node === 'object' && 'props' in node) {
    const candidate = node as { props?: { children?: ReactNode } };
    return flattenNodeText(candidate.props?.children);
  }
  return '';
}

export function extractMarkdownOutline(content: string, headingIdPrefix = 'markdown-heading'): MarkdownOutlineItem[] {
  const lines = content.split(/\r?\n/);
  const outline: MarkdownOutlineItem[] = [];
  const counter = new Map<string, number>();
  let inCodeFence = false;

  for (const line of lines) {
    if (/^\s*```/.test(line)) {
      inCodeFence = !inCodeFence;
      continue;
    }
    if (inCodeFence) {
      continue;
    }
    const match = line.match(/^\s*(#{1,6})\s+(.*)$/);
    if (!match) {
      continue;
    }
    const rawText = normalizeHeadingText(match[2] ?? '');
    if (!rawText) {
      continue;
    }
    const baseId = `${headingIdPrefix}-${slugifyHeading(rawText)}`;
    outline.push({
      id: resolveHeadingId(baseId, counter),
      text: rawText,
      level: match[1].length,
    });
  }

  return outline;
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

export function MarkdownRenderer({ content, className, headingIdPrefix = 'markdown-heading' }: MarkdownRendererProps) {
  const normalizedContent = normalizeFontMarkup(content);
  const headingIdCounter = new Map<string, number>();
  const renderHeading = (level: number, children: ReactNode) => {
    const tagName = `h${level}` as 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';
    const headingText = flattenNodeText(children);
    const baseId = `${headingIdPrefix}-${slugifyHeading(headingText)}`;
    const id = resolveHeadingId(baseId, headingIdCounter);
    return createElement(tagName, { id }, children);
  };
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
    h1: ({ children }: any) => renderHeading(1, children),
    h2: ({ children }: any) => renderHeading(2, children),
    h3: ({ children }: any) => renderHeading(3, children),
    h4: ({ children }: any) => renderHeading(4, children),
    h5: ({ children }: any) => renderHeading(5, children),
    h6: ({ children }: any) => renderHeading(6, children),
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
