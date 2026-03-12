import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';

type MarkdownMessageProps = {
  content: string;
};

export function MarkdownMessage({ content }: MarkdownMessageProps) {
  return (
    <div className="chat-markdown">
      <Markdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noreferrer">
              {children}
            </a>
          ),
          code(props) {
            const { className, children } = props;
            const language = /language-(\w+)/.exec(className ?? '')?.[1];
            const code = String(children).replace(/\n$/, '');
            if (!language) {
              return <code className="chat-inline-code">{code}</code>;
            }
            return (
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
                }}
              >
                {code}
              </SyntaxHighlighter>
            );
          },
        }}
      >
        {content}
      </Markdown>
    </div>
  );
}
