import { Outlet } from 'react-router-dom';
import { WorkspaceHeader } from '../components/WorkspaceHeader';
import { buildUserWorkspaceTabs } from './workspaceTabs';

export function UserWorkspaceLayout() {
  return (
    <div className="user-workspace-shell">
      <WorkspaceHeader
        subtitle="邮箱登录、持久化会话、流式 ReAct 问答与引用回溯都集中在统一工作区。"
        tabs={buildUserWorkspaceTabs()}
      />

      <div className="user-workspace-content">
        <Outlet />
      </div>
    </div>
  );
}
