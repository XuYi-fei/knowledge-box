import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { MarkdownCodeBlock } from '../../components/MarkdownCodeBlock';

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
              <MarkdownCodeBlock
                className="chat-markdown-code-block"
                language={language}
                code={code}
                customStyle={{
                  background: '#f3f7f6',
                }}
              />
            );
          },
        }}
      >
        {content}
      </Markdown>
    </div>
  );
}
