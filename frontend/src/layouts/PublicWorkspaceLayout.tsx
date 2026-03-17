import { Outlet } from 'react-router-dom';
import { WorkspaceHeader } from '../components/WorkspaceHeader';
import { getUserAccessToken } from '../lib/auth';
import { buildPublicWorkspaceTabs } from './workspaceTabs';

export function PublicWorkspaceLayout() {
  const hasUserSession = Boolean(getUserAccessToken());

  return (
    <div className="user-workspace-shell">
      <WorkspaceHeader
        subtitle="公开文章可按分类与标签筛选浏览；登录后仍可回到完整问答工作区。"
        tabs={buildPublicWorkspaceTabs(hasUserSession)}
      />

      <div className="user-workspace-content">
        <Outlet />
      </div>
    </div>
  );
}
