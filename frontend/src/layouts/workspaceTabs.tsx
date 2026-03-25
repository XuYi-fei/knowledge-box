import { BookOutlined, BugOutlined, FileAddOutlined, InfoCircleOutlined, LoginOutlined, ReadOutlined, ToolOutlined } from '@ant-design/icons';
import type { WorkspaceTab } from '../components/WorkspaceHeader';

export function buildUserWorkspaceTabs(): WorkspaceTab[] {
  return [
    {
      key: 'home',
      label: '主页',
      path: '/',
      icon: <BookOutlined />,
      matches: (pathname) => pathname === '/' || pathname.startsWith('/documents/'),
    },
    {
      key: 'articles',
      label: '文库',
      path: '/articles',
      icon: <ReadOutlined />,
      matches: (pathname) => pathname.startsWith('/articles'),
    },
    {
      key: 'ingest',
      label: '知识入库',
      path: '/ingest',
      icon: <FileAddOutlined />,
      matches: (pathname) => pathname.startsWith('/ingest'),
    },
    {
      key: 'tools',
      label: '工具',
      path: '/tools',
      icon: <ToolOutlined />,
      matches: (pathname) => pathname === '/tools',
    },
    {
      key: 'agent-debug',
      label: 'Agent 调试',
      path: '/agent-debug',
      icon: <BugOutlined />,
      matches: (pathname) => pathname.startsWith('/agent-debug'),
    },
    {
      key: 'about',
      label: '关于',
      path: '/about',
      icon: <InfoCircleOutlined />,
      matches: (pathname) => pathname === '/about',
    },
  ];
}

export function buildPublicWorkspaceTabs(isAuthenticated: boolean): WorkspaceTab[] {
  if (isAuthenticated) {
    return buildUserWorkspaceTabs();
  }
  return [
    {
      key: 'articles',
      label: '文库',
      path: '/articles',
      icon: <ReadOutlined />,
      matches: (pathname) => pathname.startsWith('/articles'),
    },
    {
      key: 'login',
      label: '登录',
      path: '/login',
      icon: <LoginOutlined />,
      matches: (pathname) => pathname === '/login',
    },
  ];
}
