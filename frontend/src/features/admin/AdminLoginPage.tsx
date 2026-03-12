import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { setAdminAuthToken } from '../../lib/auth';
import { buildErrorSummary } from '../../lib/errors';

type LoginForm = {
  username: string;
  password: string;
};

export function AdminLoginPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [errorSummary, setErrorSummary] = useState<string | null>(null);

  const loginMutation = useMutation({
    mutationFn: async (values: LoginForm) => {
      await api.verifyAdminLogin(values.username, values.password);
      return values;
    },
    onSuccess: (values) => {
      setErrorSummary(null);
      setAdminAuthToken(values.username, values.password);
      message.success('登录成功');
      navigate('/admin/dashboard', { replace: true });
    },
    onError: (error) => {
      setErrorSummary(buildErrorSummary(error, '请检查管理员账号密码和后端启动配置'));
    },
  });

  const onFinish = (values: LoginForm) => {
    loginMutation.mutate(values);
  };

  return (
    <div className="auth-shell">
      <Card className="auth-card" variant="borderless">
        <Typography.Title level={2}>管理后台登录</Typography.Title>
        <Typography.Paragraph type="secondary">
          当前采用 Basic Auth，对接后端 `knowledge-box.admin` 配置。登录时会立即校验后端账号密码。
        </Typography.Paragraph>
        {errorSummary ? (
          <Alert
            type="error"
            showIcon
            message="登录失败"
            description={<pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{errorSummary}</pre>}
            style={{ marginBottom: 16 }}
          />
        ) : null}
        <Form<LoginForm> layout="vertical" onFinish={onFinish}>
          <Form.Item label="用户名" name="username" rules={[{ required: true }]}>
            <Input prefix={<UserOutlined />} />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true }]}>
            <Input.Password prefix={<LockOutlined />} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={loginMutation.isPending}>
            进入控制台
          </Button>
        </Form>
      </Card>
    </div>
  );
}
