import { useQuery } from '@tanstack/react-query';
import { Card, Spin, Typography } from 'antd';
import { api } from '../../lib/api';
import { AuthorProfileContent } from '../about/AuthorProfileContent';

export function AuthorPage() {
  const authorProfileQuery = useQuery({
    queryKey: ['publicAuthorProfile'],
    queryFn: api.publicAuthorProfile,
    staleTime: 5 * 60 * 1000,
  });

  return (
    <div className="chat-shell author-page-shell">
      <Card className="chat-panel chat-card" title="关于作者">
        <div className="author-page-panel">
          <div className="author-page-intro">
            <Typography.Title level={4}>个人主页</Typography.Title>
            <Typography.Paragraph type="secondary">
              这里展示管理员维护的结构化个人资料，面向外部访客和已登录用户统一公开。
            </Typography.Paragraph>
          </div>
          {authorProfileQuery.isLoading ? (
            <div className="chat-empty-state">
              <Spin />
            </div>
          ) : (
            <AuthorProfileContent
              profile={authorProfileQuery.data}
              emptyDescription="当前还没有可展示的作者主页信息。"
            />
          )}
        </div>
      </Card>
    </div>
  );
}
