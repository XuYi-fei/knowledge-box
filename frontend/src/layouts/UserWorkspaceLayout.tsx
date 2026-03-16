import { BookOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { Typography } from 'antd';
import type { ReactNode } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

type WorkspaceTab = {
  key: string;
  label: string;
  path: string;
  icon: ReactNode;
  matches: (pathname: string) => boolean;
};

const workspaceTabs: WorkspaceTab[] = [
  {
    key: 'home',
    label: '主页',
    path: '/',
    icon: <BookOutlined />,
    matches: (pathname) => pathname === '/' || pathname.startsWith('/documents/'),
  },
  {
    key: 'about',
    label: '关于',
    path: '/about',
    icon: <InfoCircleOutlined />,
    matches: (pathname) => pathname === '/about',
  },
];

export function UserWorkspaceLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <div className="user-workspace-shell">
      <header className="user-workspace-header">
        <div className="user-workspace-brand">
          <div className="user-workspace-brand-mark">KB</div>
          <div className="user-workspace-brand-copy">
            <Typography.Title level={3} className="user-workspace-title">
              Knowledge Box
            </Typography.Title>
            <Typography.Paragraph className="user-workspace-subtitle">
              邮箱登录、持久化会话、流式 ReAct 问答与引用回溯都集中在统一工作区。
            </Typography.Paragraph>
          </div>
        </div>

        <nav className="user-workspace-tabs" aria-label="用户工作区导航">
          {workspaceTabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              className={`user-workspace-tab ${tab.matches(location.pathname) ? 'user-workspace-tab-active' : ''}`}
              onClick={() => navigate(tab.path)}
            >
              {tab.icon}
              <span>{tab.label}</span>
            </button>
          ))}
        </nav>
      </header>

      <div className="user-workspace-content">
        <Outlet />
      </div>
    </div>
  );
}
