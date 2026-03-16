import { Suspense, lazy, type ReactNode } from 'react';
import { Spin } from 'antd';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { getAdminAuthToken, getUserAccessToken } from '../lib/auth';
import { UserWorkspaceLayout } from '../layouts/UserWorkspaceLayout';

const AdminLayout = lazy(() => import('../layouts/AdminLayout').then((module) => ({ default: module.AdminLayout })));
const AdminLoginPage = lazy(() => import('../features/admin/AdminLoginPage').then((module) => ({ default: module.AdminLoginPage })));
const DashboardPage = lazy(() => import('../features/admin/DashboardPage').then((module) => ({ default: module.DashboardPage })));
const ProfileVersionsPage = lazy(() => import('../features/admin/ProfileVersionsPage').then((module) => ({ default: module.ProfileVersionsPage })));
const DocumentsPage = lazy(() => import('../features/admin/DocumentsPage').then((module) => ({ default: module.DocumentsPage })));
const DocumentReviewsPage = lazy(() => import('../features/admin/DocumentReviewsPage').then((module) => ({ default: module.DocumentReviewsPage })));
const AppToolsPage = lazy(() => import('../features/admin/AppToolsPage').then((module) => ({ default: module.AppToolsPage })));
const AppToolExecutionsPage = lazy(() => import('../features/admin/AppToolExecutionsPage').then((module) => ({ default: module.AppToolExecutionsPage })));
const IntegrationsPage = lazy(() => import('../features/admin/IntegrationsPage').then((module) => ({ default: module.IntegrationsPage })));
const HooksPage = lazy(() => import('../features/admin/HooksPage').then((module) => ({ default: module.HooksPage })));
const TracesPage = lazy(() => import('../features/admin/TracesPage').then((module) => ({ default: module.TracesPage })));
const TraceDetailPage = lazy(() => import('../features/admin/TraceDetailPage').then((module) => ({ default: module.TraceDetailPage })));
const AboutPage = lazy(() => import('../features/chat/AboutPage').then((module) => ({ default: module.AboutPage })));
const PublicChatPage = lazy(() => import('../features/chat/PublicChatPage').then((module) => ({ default: module.PublicChatPage })));
const UserToolsPage = lazy(() => import('../features/chat/UserToolsPage').then((module) => ({ default: module.UserToolsPage })));
const UserDocumentDetailPage = lazy(() => import('../features/chat/UserDocumentDetailPage').then((module) => ({ default: module.UserDocumentDetailPage })));
const UserLoginPage = lazy(() => import('../features/auth/UserLoginPage').then((module) => ({ default: module.UserLoginPage })));

function withSuspense(element: ReactNode) {
  return (
    <Suspense fallback={<div style={{ minHeight: '100%', display: 'grid', placeItems: 'center' }}><Spin size="large" /></div>}>
      {element}
    </Suspense>
  );
}

function RequireUserAuth({ children }: { children: ReactNode }) {
  return getUserAccessToken() ? <>{children}</> : <Navigate to="/login" replace />;
}

function RedirectAuthenticatedUser({ children }: { children: ReactNode }) {
  return getUserAccessToken() ? <Navigate to="/" replace /> : <>{children}</>;
}

function RedirectAuthenticatedAdmin({ children }: { children: ReactNode }) {
  return getAdminAuthToken() ? <Navigate to="/admin/dashboard" replace /> : <>{children}</>;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: withSuspense(
      <RequireUserAuth>
        <UserWorkspaceLayout />
      </RequireUserAuth>,
    ),
    children: [
      { index: true, element: withSuspense(<PublicChatPage />) },
      { path: 'tools', element: withSuspense(<UserToolsPage />) },
      { path: 'about', element: withSuspense(<AboutPage />) },
      { path: 'documents/:documentId', element: withSuspense(<UserDocumentDetailPage />) },
    ],
  },
  {
    path: '/login',
    element: withSuspense(
      <RedirectAuthenticatedUser>
        <UserLoginPage />
      </RedirectAuthenticatedUser>,
    ),
  },
  {
    path: '/admin/login',
    element: withSuspense(
      <RedirectAuthenticatedAdmin>
        <AdminLoginPage />
      </RedirectAuthenticatedAdmin>,
    ),
  },
  {
    path: '/admin',
    element: withSuspense(<AdminLayout />),
    children: [
      { index: true, element: <Navigate to="/admin/dashboard" replace /> },
      { path: 'dashboard', element: withSuspense(<DashboardPage />) },
      { path: 'profiles', element: withSuspense(<ProfileVersionsPage />) },
      { path: 'documents', element: withSuspense(<DocumentsPage />) },
      { path: 'document-reviews', element: withSuspense(<DocumentReviewsPage />) },
      { path: 'app-tools', element: withSuspense(<AppToolsPage />) },
      { path: 'app-tool-executions', element: withSuspense(<AppToolExecutionsPage />) },
      { path: 'integrations', element: withSuspense(<IntegrationsPage />) },
      { path: 'hooks', element: withSuspense(<HooksPage />) },
      { path: 'traces', element: withSuspense(<TracesPage />) },
      { path: 'traces/:traceId', element: withSuspense(<TraceDetailPage />) },
    ],
  },
]);
