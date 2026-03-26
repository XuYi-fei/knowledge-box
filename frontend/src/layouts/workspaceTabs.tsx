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
      key: 'author',
      label: '关于作者',
      path: '/author',
      icon: <InfoCircleOutlined />,
      matches: (pathname) => pathname === '/author',
    },
    {
      key: 'log',
      label: '更新日志',
      path: '/log',
      icon: <InfoCircleOutlined />,
      matches: (pathname) => pathname === '/log' || pathname === '/about',
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
      key: 'author',
      label: '关于作者',
      path: '/author',
      icon: <InfoCircleOutlined />,
      matches: (pathname) => pathname === '/author',
    },
    {
      key: 'log',
      label: '更新日志',
      path: '/log',
      icon: <InfoCircleOutlined />,
      matches: (pathname) => pathname === '/log' || pathname === '/about',
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
