import {
  LockOutlined,
  LoginOutlined,
  MailOutlined,
  SafetyCertificateOutlined,
  UserAddOutlined,
} from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, Input, Segmented, Space, Tabs, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { setUserAuthSession } from '../../lib/auth';
import { ApiRequestError, buildErrorSummary } from '../../lib/errors';
import type { UserAuthResponse } from '../../lib/types';

type CodeLoginForm = {
  email: string;
  verificationCode: string;
};

type PasswordLoginForm = {
  email: string;
  password: string;
};

type RegisterForm = {
  email: string;
  verificationCode: string;
  password: string;
};

type AuthView = 'login' | 'register';
type LoginMode = 'code' | 'password';
type CodeScene = 'codeLogin' | 'register';

export function UserLoginPage() {
  const navigate = useNavigate();
  const { message, notification } = App.useApp();
  const [codeLoginForm] = Form.useForm<CodeLoginForm>();
  const [passwordLoginForm] = Form.useForm<PasswordLoginForm>();
  const [registerForm] = Form.useForm<RegisterForm>();
  const [authView, setAuthView] = useState<AuthView>('login');
  const [loginMode, setLoginMode] = useState<LoginMode>('code');
  const [errorSummary, setErrorSummary] = useState<string | null>(null);
  const [countdowns, setCountdowns] = useState<Record<CodeScene, number>>({
    codeLogin: 0,
    register: 0,
  });

  useEffect(() => {
    if (!Object.values(countdowns).some((value) => value > 0)) {
      return undefined;
    }

    const timer = window.setInterval(() => {
      setCountdowns((current) => ({
        codeLogin: current.codeLogin > 0 ? current.codeLogin - 1 : 0,
        register: current.register > 0 ? current.register - 1 : 0,
      }));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [countdowns]);

  function handleAuthSuccess(result: UserAuthResponse, successTitle: string, successDescription: string) {
    setErrorSummary(null);
    setUserAuthSession(result);
    notification.success({
      message: successTitle,
      description: successDescription,
    });
    navigate('/', { replace: true });
  }

  function resetErrorAndSwitch(view: AuthView, mode?: LoginMode) {
    setErrorSummary(null);
    setAuthView(view);
    if (mode) {
      setLoginMode(mode);
    }
  }

  function showAuthErrorNotification(error: unknown) {
    if (!(error instanceof ApiRequestError)) {
      return;
    }

    if (error.code === 'EMAIL_NOT_REGISTERED') {
      notification.warning({
        message: '邮箱尚未注册',
        description: '当前邮箱还没有账号，请前往注册页面完成注册。',
      });
      return;
    }

    if (error.code === 'PASSWORD_LOGIN_NOT_AVAILABLE') {
      notification.info({
        message: '当前账号尚未设置密码',
        description: '这个邮箱可以继续验证码登录，也可以前往注册页面补充设置密码。',
      });
    }
  }

  const sendCodeMutation = useMutation({
    mutationFn: async ({ email, scene }: { email: string; scene: CodeScene }) => {
      if (!email) {
        throw new Error('请先输入邮箱地址');
      }
      const result = await api.sendEmailCode(email);
      return { ...result, scene };
    },
    onSuccess: (result) => {
      setErrorSummary(null);
      setCountdowns((current) => ({
        ...current,
        [result.scene]: 60,
      }));
      message.success(result.message);
    },
    onError: (error) => {
      setErrorSummary(buildErrorSummary(error, '验证码发送失败，请检查 Redis、SMTP 与 QQ 邮箱授权码配置'));
    },
  });

  const codeLoginMutation = useMutation({
    mutationFn: (values: CodeLoginForm) => api.loginByEmailCode(values.email, values.verificationCode),
    onSuccess: (result) => {
      if (result.authAction === 'AUTO_REGISTERED') {
        handleAuthSuccess(
          result,
          '未注册，已自动注册',
          '已为当前邮箱创建账号并完成登录。如需密码登录，请前往注册页面设置密码。',
        );
        return;
      }
      handleAuthSuccess(result, '登录成功', '欢迎回来。');
    },
    onError: (error) => {
      setErrorSummary(buildErrorSummary(error, '验证码登录失败，请检查邮箱和验证码'));
    },
  });

  const passwordLoginMutation = useMutation({
    mutationFn: (values: PasswordLoginForm) => api.loginByPassword(values.email, values.password),
    onSuccess: (result) => {
      handleAuthSuccess(result, '登录成功', '欢迎回来。');
    },
    onError: (error) => {
      showAuthErrorNotification(error);
      setErrorSummary(buildErrorSummary(error, '密码登录失败，请检查邮箱和密码'));
    },
  });

  const registerMutation = useMutation({
    mutationFn: (values: RegisterForm) => api.registerByEmail(values.email, values.password, values.verificationCode),
    onSuccess: (result) => {
      handleAuthSuccess(result, '注册成功', '账号已创建并完成登录。');
    },
    onError: (error) => {
      setErrorSummary(buildErrorSummary(error, '注册失败，请检查邮箱、验证码和密码'));
    },
  });

  return (
    <div className="auth-shell">
      <Card className="auth-card" variant="borderless">
        <div className="page-stack">
          <div>
            <Typography.Title level={2} style={{ marginBottom: 8 }}>
              登录或注册 Knowledge Box
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              验证码登录支持自动注册；密码登录仅面向已完成注册并设置密码的账号。
            </Typography.Paragraph>
          </div>

          {errorSummary ? (
            <Alert
              type="error"
              showIcon
              message="操作失败"
              description={<pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{errorSummary}</pre>}
            />
          ) : null}

          <Segmented<AuthView>
            block
            value={authView}
            onChange={(value) => resetErrorAndSwitch(value)}
            options={[
              { label: '登录', value: 'login' },
              { label: '注册', value: 'register' },
            ]}
          />

          {authView === 'login' ? (
            <>
              <Tabs
                activeKey={loginMode}
                onChange={(value) => {
                  setErrorSummary(null);
                  setLoginMode(value as LoginMode);
                }}
                items={[
                  {
                    key: 'code',
                    label: '验证码登录',
                    children: (
                      <Form<CodeLoginForm>
                        form={codeLoginForm}
                        layout="vertical"
                        onFinish={(values) => codeLoginMutation.mutate(values)}
                        initialValues={{ email: '', verificationCode: '' }}
                      >
                        <Form.Item
                          label="邮箱"
                          name="email"
                          rules={[
                            { required: true, message: '请输入邮箱地址' },
                            { type: 'email', message: '邮箱格式不正确' },
                          ]}
                        >
                          <Input prefix={<MailOutlined />} placeholder="name@example.com" />
                        </Form.Item>

                        <Form.Item label="验证码" required>
                          <Space.Compact style={{ width: '100%' }}>
                            <Form.Item
                              name="verificationCode"
                              noStyle
                              rules={[
                                { required: true, message: '请输入验证码' },
                                { min: 4, max: 8, message: '验证码长度不正确' },
                              ]}
                            >
                              <Input prefix={<SafetyCertificateOutlined />} placeholder="输入邮箱验证码" />
                            </Form.Item>
                            <Button
                              icon={<MailOutlined />}
                              loading={sendCodeMutation.isPending}
                              disabled={countdowns.codeLogin > 0}
                              onClick={() => {
                                const email = codeLoginForm.getFieldValue('email')?.trim();
                                void sendCodeMutation.mutateAsync({ email, scene: 'codeLogin' });
                              }}
                            >
                              {countdowns.codeLogin > 0 ? `${countdowns.codeLogin}s 后重发` : '发送验证码'}
                            </Button>
                          </Space.Compact>
                        </Form.Item>

                        <Button
                          type="primary"
                          htmlType="submit"
                          block
                          size="large"
                          icon={<LoginOutlined />}
                          loading={codeLoginMutation.isPending}
                        >
                          验证码登录
                        </Button>
                      </Form>
                    ),
                  },
                  {
                    key: 'password',
                    label: '密码登录',
                    children: (
                      <Form<PasswordLoginForm>
                        form={passwordLoginForm}
                        layout="vertical"
                        onFinish={(values) => passwordLoginMutation.mutate(values)}
                        initialValues={{ email: '', password: '' }}
                      >
                        <Form.Item
                          label="邮箱"
                          name="email"
                          rules={[
                            { required: true, message: '请输入邮箱地址' },
                            { type: 'email', message: '邮箱格式不正确' },
                          ]}
                        >
                          <Input prefix={<MailOutlined />} placeholder="name@example.com" />
                        </Form.Item>

                        <Form.Item
                          label="密码"
                          name="password"
                          rules={[
                            { required: true, message: '请输入密码' },
                            { min: 8, message: '密码至少 8 位' },
                          ]}
                        >
                          <Input.Password prefix={<LockOutlined />} placeholder="输入已设置的登录密码" />
                        </Form.Item>

                        <Button
                          type="primary"
                          htmlType="submit"
                          block
                          size="large"
                          icon={<LoginOutlined />}
                          loading={passwordLoginMutation.isPending}
                        >
                          密码登录
                        </Button>
                      </Form>
                    ),
                  },
                ]}
              />

              <Button type="link" block onClick={() => resetErrorAndSwitch('register')}>
                还没有账号？前往注册
              </Button>
            </>
          ) : (
            <>
              <Form<RegisterForm>
                form={registerForm}
                layout="vertical"
                onFinish={(values) => registerMutation.mutate(values)}
                initialValues={{ email: '', verificationCode: '', password: '' }}
              >
                <Form.Item
                  label="邮箱"
                  name="email"
                  rules={[
                    { required: true, message: '请输入邮箱地址' },
                    { type: 'email', message: '邮箱格式不正确' },
                  ]}
                >
                  <Input prefix={<MailOutlined />} placeholder="name@example.com" />
                </Form.Item>

                <Form.Item label="验证码" required>
                  <Space.Compact style={{ width: '100%' }}>
                    <Form.Item
                      name="verificationCode"
                      noStyle
                      rules={[
                        { required: true, message: '请输入验证码' },
                        { min: 4, max: 8, message: '验证码长度不正确' },
                      ]}
                    >
                      <Input prefix={<SafetyCertificateOutlined />} placeholder="输入邮箱验证码" />
                    </Form.Item>
                    <Button
                      icon={<MailOutlined />}
                      loading={sendCodeMutation.isPending}
                      disabled={countdowns.register > 0}
                      onClick={() => {
                        const email = registerForm.getFieldValue('email')?.trim();
                        void sendCodeMutation.mutateAsync({ email, scene: 'register' });
                      }}
                    >
                      {countdowns.register > 0 ? `${countdowns.register}s 后重发` : '发送验证码'}
                    </Button>
                  </Space.Compact>
                </Form.Item>

                <Form.Item
                  label="设置密码"
                  name="password"
                  rules={[
                    { required: true, message: '请设置密码' },
                    { min: 8, message: '密码至少 8 位' },
                  ]}
                >
                  <Input.Password prefix={<LockOutlined />} placeholder="至少 8 位，后续可直接密码登录" />
                </Form.Item>

                <Button
                  type="primary"
                  htmlType="submit"
                  block
                  size="large"
                  icon={<UserAddOutlined />}
                  loading={registerMutation.isPending}
                >
                  注册并进入知识库
                </Button>
              </Form>

              <Button type="link" block onClick={() => resetErrorAndSwitch('login', 'code')}>
                已有账号？前往登录
              </Button>
            </>
          )}

          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            如果发送失败，请优先查看后端日志中的 SMTP 详细报错；使用 QQ 邮箱时需要填写 SMTP 授权码，而不是邮箱登录密码。
          </Typography.Paragraph>
        </div>
      </Card>
    </div>
  );
}
