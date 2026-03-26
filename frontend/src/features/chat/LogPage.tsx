import { useQuery } from '@tanstack/react-query';
import { Card, Empty, Space, Spin, Tag, Typography } from 'antd';
import { api } from '../../lib/api';
import type { AboutReleaseNote } from '../../lib/types';
import { MarkdownMessage } from './MarkdownMessage';

export function LogPage() {
  const releaseNotesQuery = useQuery({
    queryKey: ['releaseNotes'],
    queryFn: api.releaseNotes,
    staleTime: 5 * 60 * 1000,
  });

  const releaseNotes = releaseNotesQuery.data ?? [];

  return (
    <div className="chat-shell about-page-shell">
      <Card className="chat-panel chat-card" title="更新日志">
        <div className="about-panel">
          <div className="about-panel-intro">
            <Typography.Title level={4}>项目更新日志</Typography.Title>
            <Typography.Paragraph type="secondary">
              这里展示的是由后端数据库返回的版本更新记录，便于快速了解当前项目近期的能力演进与维护重点。
            </Typography.Paragraph>
          </div>

          {releaseNotesQuery.isLoading ? (
            <div className="chat-empty-state">
              <Spin />
            </div>
          ) : releaseNotes.length ? (
            <div className="about-release-list">
              {releaseNotes.map((note: AboutReleaseNote) => (
                <Card
                  key={note.id}
                  size="small"
                  className={`about-release-card ${note.highlighted ? 'about-release-card-highlighted' : ''}`}
                >
                  <Space wrap className="about-release-meta">
                    <Tag color={note.highlighted ? 'gold' : 'geekblue'} bordered={false}>
                      {note.versionLabel}
                    </Tag>
                    <Typography.Text type="secondary">
                      {new Date(note.publishedAt).toLocaleDateString('zh-CN')}
                    </Typography.Text>
                  </Space>
                  <Typography.Title level={5}>{note.title}</Typography.Title>
                  <Typography.Paragraph>{note.summary}</Typography.Paragraph>
                  <div className="about-release-content">
                    <MarkdownMessage content={note.contentMarkdown} />
                  </div>
                </Card>
              ))}
            </div>
          ) : (
            <div className="chat-empty-state">
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前还没有可展示的更新日志。" />
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
