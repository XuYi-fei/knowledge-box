import { Suspense, lazy, type ReactNode } from 'react';
import { Spin } from 'antd';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { getAdminAuthToken, getUserAccessToken } from '../lib/auth';

const AdminLayout = lazy(() => import('../layouts/AdminLayout').then((module) => ({ default: module.AdminLayout })));
const AdminLoginPage = lazy(() => import('../features/admin/AdminLoginPage').then((module) => ({ default: module.AdminLoginPage })));
const DashboardPage = lazy(() => import('../features/admin/DashboardPage').then((module) => ({ default: module.DashboardPage })));
const ProfileVersionsPage = lazy(() => import('../features/admin/ProfileVersionsPage').then((module) => ({ default: module.ProfileVersionsPage })));
const DocumentsPage = lazy(() => import('../features/admin/DocumentsPage').then((module) => ({ default: module.DocumentsPage })));
const DocumentReviewsPage = lazy(() => import('../features/admin/DocumentReviewsPage').then((module) => ({ default: module.DocumentReviewsPage })));
const IntegrationsPage = lazy(() => import('../features/admin/IntegrationsPage').then((module) => ({ default: module.IntegrationsPage })));
const HooksPage = lazy(() => import('../features/admin/HooksPage').then((module) => ({ default: module.HooksPage })));
const TracesPage = lazy(() => import('../features/admin/TracesPage').then((module) => ({ default: module.TracesPage })));
const TraceDetailPage = lazy(() => import('../features/admin/TraceDetailPage').then((module) => ({ default: module.TraceDetailPage })));
const PublicChatPage = lazy(() => import('../features/chat/PublicChatPage').then((module) => ({ default: module.PublicChatPage })));
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
        <PublicChatPage />
      </RequireUserAuth>,
    ),
  },
  {
    path: '/documents/:documentId',
    element: withSuspense(
      <RequireUserAuth>
        <UserDocumentDetailPage />
      </RequireUserAuth>,
    ),
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
      { path: 'integrations', element: withSuspense(<IntegrationsPage />) },
      { path: 'hooks', element: withSuspense(<HooksPage />) },
      { path: 'traces', element: withSuspense(<TracesPage />) },
      { path: 'traces/:traceId', element: withSuspense(<TraceDetailPage />) },
    ],
  },
]);
