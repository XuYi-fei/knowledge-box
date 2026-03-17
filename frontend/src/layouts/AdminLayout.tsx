import { KeyOutlined, LogoutOutlined, ProfileOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { ProLayout } from '@ant-design/pro-components';
import { App, Button, Form, Input, Modal, Space } from 'antd';
import { useState } from 'react';
import { Link, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { clearAdminAuthToken, getAdminAuthToken, getAdminAuthUsername, setAdminAuthToken } from '../lib/auth';
import { buildErrorSummary } from '../lib/errors';

type ChangePasswordForm = {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
};

export function AdminLayout() {
  const { message } = App.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const [passwordForm] = Form.useForm<ChangePasswordForm>();
  const [isPasswordModalOpen, setPasswordModalOpen] = useState(false);

  const pathname = location.pathname;

  if (!getAdminAuthToken()) {
    return <Navigate to="/admin/login" replace />;
  }

  const menuItems = [
    { path: '/admin/dashboard', name: '概览' },
    { path: '/admin/profiles', name: 'Agent 配置' },
    { path: '/admin/documents', name: '知识文档' },
    { path: '/admin/document-reviews', name: '文档审核' },
    { path: '/admin/document-duplicates', name: '重复治理' },
    { path: '/admin/app-tools', name: '用户工具' },
    { path: '/admin/app-tool-executions', name: '工具执行日志' },
    { path: '/admin/integrations', name: 'Tools / MCP / Skills' },
    { path: '/admin/hooks', name: 'Hooks' },
    { path: '/admin/traces', name: '运行追踪' },
  ];

  const changePasswordMutation = useMutation({
    mutationFn: async (values: ChangePasswordForm) => api.changeAdminPassword(values.currentPassword, values.newPassword),
    onSuccess: (_, values) => {
      const username = getAdminAuthUsername();
      if (!username) {
        clearAdminAuthToken();
        message.warning('密码已修改，请重新登录');
        navigate('/admin/login', { replace: true });
        return;
      }
      setAdminAuthToken(username, values.newPassword);
      setPasswordModalOpen(false);
      passwordForm.resetFields();
      message.success('管理员密码修改成功');
    },
    onError: (error) => {
      message.error(buildErrorSummary(error, '修改密码失败'));
    },
  });

  const closePasswordModal = () => {
    if (changePasswordMutation.isPending) {
      return;
    }
    setPasswordModalOpen(false);
    passwordForm.resetFields();
  };

  const logout = () => {
    clearAdminAuthToken();
    message.success('已退出管理后台');
    navigate('/admin/login', { replace: true });
  };

  return (
    <div className="admin-shell">
      <ProLayout
        title="Knowledge Box Admin"
        logo={<ProfileOutlined />}
        route={{ routes: menuItems }}
        location={{ pathname }}
        menuItemRender={(item, dom) => <Link to={item.path ?? '/admin/dashboard'}>{dom}</Link>}
        avatarProps={{
          title: 'Admin',
          render: () => (
            <Space>
              <Button icon={<KeyOutlined />} onClick={() => setPasswordModalOpen(true)}>
                改密
              </Button>
              <Button icon={<LogoutOutlined />} onClick={logout}>
                退出
              </Button>
            </Space>
          ),
        }}
        layout="mix"
        style={{ height: '100%', minHeight: 0, background: '#eef4f3' }}
        contentStyle={{ padding: 0, minHeight: 0 }}
      >
        <div className="admin-content-scroll">
          <Outlet />
        </div>
      </ProLayout>
      <Modal
        title="修改管理员密码"
        open={isPasswordModalOpen}
        onCancel={closePasswordModal}
        onOk={() => passwordForm.submit()}
        okText="确认修改"
        cancelText="取消"
        confirmLoading={changePasswordMutation.isPending}
        maskClosable={!changePasswordMutation.isPending}
        destroyOnClose
      >
        <Form<ChangePasswordForm>
          form={passwordForm}
          layout="vertical"
          onFinish={(values) => changePasswordMutation.mutate(values)}
          preserve={false}
        >
          <Form.Item
            label="当前密码"
            name="currentPassword"
            rules={[{ required: true, message: '请输入当前密码' }, { min: 8, message: '密码长度至少为 8 位' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[{ required: true, message: '请输入新密码' }, { min: 8, message: '密码长度至少为 8 位' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value: string | undefined) {
                  if (!value || value === getFieldValue('newPassword')) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的新密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
