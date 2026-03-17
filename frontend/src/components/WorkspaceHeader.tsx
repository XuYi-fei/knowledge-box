import { Typography } from 'antd';
import type { ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

export type WorkspaceTab = {
  key: string;
  label: string;
  path: string;
  icon: ReactNode;
  matches: (pathname: string) => boolean;
};

type WorkspaceHeaderProps = {
  subtitle: string;
  tabs: WorkspaceTab[];
};

export function WorkspaceHeader({ subtitle, tabs }: WorkspaceHeaderProps) {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <header className="user-workspace-header">
      <div className="user-workspace-brand">
        <div className="user-workspace-brand-mark">KB</div>
        <div className="user-workspace-brand-copy">
          <Typography.Title level={3} className="user-workspace-title">
            Knowledge Box
          </Typography.Title>
          <Typography.Paragraph className="user-workspace-subtitle">{subtitle}</Typography.Paragraph>
        </div>
      </div>

      <nav className="user-workspace-tabs" aria-label="工作区导航">
        {tabs.map((tab) => (
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
  );
}
