import { Outlet } from 'react-router-dom';
import { useLocation } from 'react-router-dom';
import { WorkspaceHeader } from '../components/WorkspaceHeader';
import { getUserAccessToken } from '../lib/auth';
import { buildPublicWorkspaceTabs } from './workspaceTabs';

export function PublicWorkspaceLayout() {
  const hasUserSession = Boolean(getUserAccessToken());
  const location = useLocation();
  const subtitle = location.pathname.startsWith('/author')
    ? '公开作者主页面向访客和登录用户统一展示，可作为个人简历与能力介绍页面。'
    : location.pathname.startsWith('/log')
      ? '这里集中展示项目更新日志；历史 /about 链接会自动跳转到 /log。'
      : '公开文章可按分类与标签筛选浏览；登录后仍可回到完整问答工作区。';

  return (
    <div className="user-workspace-shell">
      <WorkspaceHeader
        subtitle={subtitle}
        tabs={buildPublicWorkspaceTabs(hasUserSession)}
      />

      <div className="user-workspace-content">
        <Outlet />
      </div>
    </div>
  );
}
