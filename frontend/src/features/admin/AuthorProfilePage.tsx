import { PlusOutlined, UploadOutlined, DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Col, Form, Input, InputNumber, Row, Space, Typography, Upload } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import type { UploadProps } from 'antd';
import { api } from '../../lib/api';
import type { AuthorProfile } from '../../lib/types';
import { buildErrorSummary } from '../../lib/errors';
import { AuthorProfileContent } from '../about/AuthorProfileContent';
import { MarkdownWorkbench } from './components/MarkdownWorkbench';

type AuthorProfileFormValues = {
  name?: string;
  gender?: string;
  email?: string;
  phone?: string;
  age?: number | null;
  educations: AuthorProfile['educations'];
  skills: AuthorProfile['skills'];
  workExperiences: AuthorProfile['workExperiences'];
  internshipExperiences: AuthorProfile['internshipExperiences'];
  projectExperiences: AuthorProfile['projectExperiences'];
  customSections: AuthorProfile['customSections'];
};

function emptyFormValues(): AuthorProfileFormValues {
  return {
    name: '',
    gender: '',
    email: '',
    phone: '',
    age: null,
    educations: [],
    skills: [],
    workExperiences: [],
    internshipExperiences: [],
    projectExperiences: [],
    customSections: [],
  };
}

function toFormValues(profile: AuthorProfile | undefined): AuthorProfileFormValues {
  if (!profile) {
    return emptyFormValues();
  }
  return {
    name: profile.name ?? '',
    gender: profile.gender ?? '',
    email: profile.email ?? '',
    phone: profile.phone ?? '',
    age: profile.age ?? null,
    educations: profile.educations ?? [],
    skills: profile.skills ?? [],
    workExperiences: profile.workExperiences ?? [],
    internshipExperiences: profile.internshipExperiences ?? [],
    projectExperiences: profile.projectExperiences ?? [],
    customSections: profile.customSections ?? [],
  };
}

function normalizeStringArray(values: string[] | undefined) {
  return (values ?? []).map((item) => item.trim()).filter(Boolean);
}

function buildPreviewProfile(
  values: Partial<AuthorProfileFormValues> | undefined,
  photoState: { photoUrl: string | null; photoContentType: string | null; photoContentLength: number | null },
): AuthorProfile {
  return {
    configured: Boolean(values?.name?.trim()),
    name: values?.name?.trim() || null,
    gender: values?.gender?.trim() || null,
    email: values?.email?.trim() || null,
    phone: values?.phone?.trim() || null,
    age: values?.age ?? null,
    photoUrl: photoState.photoUrl,
    photoContentType: photoState.photoContentType,
    photoContentLength: photoState.photoContentLength,
    educations: (values?.educations ?? []).map((item) => ({
      stageLabel: item.stageLabel?.trim() || null,
      schoolName: item.schoolName?.trim() || '',
      periodText: item.periodText?.trim() || null,
      major: item.major?.trim() || null,
      honors: normalizeStringArray(item.honors),
    })),
    skills: (values?.skills ?? []).map((item) => ({
      label: item.label?.trim() || '',
      descriptionMarkdown: item.descriptionMarkdown?.trim() || null,
    })),
    workExperiences: (values?.workExperiences ?? []).map((item) => ({
      name: item.name?.trim() || '',
      periodText: item.periodText?.trim() || null,
      summaryMarkdown: item.summaryMarkdown?.trim() || null,
      responsibilityItems: normalizeStringArray(item.responsibilityItems),
      techStacks: normalizeStringArray(item.techStacks),
    })),
    internshipExperiences: (values?.internshipExperiences ?? []).map((item) => ({
      name: item.name?.trim() || '',
      periodText: item.periodText?.trim() || null,
      summaryMarkdown: item.summaryMarkdown?.trim() || null,
      responsibilityItems: normalizeStringArray(item.responsibilityItems),
      techStacks: normalizeStringArray(item.techStacks),
    })),
    projectExperiences: (values?.projectExperiences ?? []).map((item) => ({
      name: item.name?.trim() || '',
      periodText: item.periodText?.trim() || null,
      summaryMarkdown: item.summaryMarkdown?.trim() || null,
      responsibilityItems: normalizeStringArray(item.responsibilityItems),
      techStacks: normalizeStringArray(item.techStacks),
    })),
    customSections: (values?.customSections ?? []).map((section) => ({
      sectionTitle: section.sectionTitle?.trim() || null,
      items: (section.items ?? []).map((item) => ({
        itemTitle: item.itemTitle?.trim() || null,
        periodText: item.periodText?.trim() || null,
        descriptionMarkdown: item.descriptionMarkdown?.trim() || null,
      })),
    })),
  };
}

export function AuthorProfilePage() {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<AuthorProfileFormValues>();
  const [photoState, setPhotoState] = useState<{
    photoUrl: string | null;
    photoContentType: string | null;
    photoContentLength: number | null;
  }>({
    photoUrl: null,
    photoContentType: null,
    photoContentLength: null,
  });

  const authorProfileQuery = useQuery({
    queryKey: ['adminAuthorProfile'],
    queryFn: api.adminAuthorProfile,
  });

  useEffect(() => {
    if (!authorProfileQuery.data) {
      form.setFieldsValue(emptyFormValues());
      return;
    }
    form.setFieldsValue(toFormValues(authorProfileQuery.data));
    setPhotoState({
      photoUrl: authorProfileQuery.data.photoUrl,
      photoContentType: authorProfileQuery.data.photoContentType,
      photoContentLength: authorProfileQuery.data.photoContentLength,
    });
  }, [authorProfileQuery.data, form]);

  const saveMutation = useMutation({
    mutationFn: async (values: AuthorProfileFormValues) => api.updateAuthorProfile(buildPreviewProfile(values, photoState)),
    onSuccess: (profile) => {
      message.success('关于作者资料已保存');
      void queryClient.invalidateQueries({ queryKey: ['adminAuthorProfile'] });
      void queryClient.invalidateQueries({ queryKey: ['publicAuthorProfile'] });
      form.setFieldsValue(toFormValues(profile));
    },
    onError: (error) => {
      modal.error({
        title: '保存关于作者资料失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查表单内容后重试')}</pre>,
      });
    },
  });

  const uploadPhotoMutation = useMutation({
    mutationFn: (file: File) => api.uploadAuthorProfilePhoto(file),
    onSuccess: (result) => {
      setPhotoState({
        photoUrl: result.url,
        photoContentType: result.contentType,
        photoContentLength: result.contentLength,
      });
      message.success('作者照片已上传');
      void queryClient.invalidateQueries({ queryKey: ['publicAuthorProfile'] });
    },
    onError: (error) => {
      modal.error({
        title: '上传作者照片失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查图片后重试')}</pre>,
      });
    },
  });

  const currentValues = Form.useWatch([], form) as Partial<AuthorProfileFormValues> | undefined;
  const previewProfile = useMemo(() => buildPreviewProfile(currentValues, photoState), [currentValues, photoState]);

  const uploadProps: UploadProps = {
    accept: 'image/*',
    showUploadList: false,
    customRequest: async ({ file, onSuccess, onError }) => {
      try {
        if (!(file instanceof File)) {
          throw new Error('无效的图片文件');
        }
        await uploadPhotoMutation.mutateAsync(file);
        onSuccess?.({}, new XMLHttpRequest());
      } catch (error) {
        onError?.(error as Error);
      }
    },
  };

  const renderExperienceList = (
    fieldName: 'workExperiences' | 'internshipExperiences' | 'projectExperiences',
    title: string,
  ) => (
    <Form.List name={fieldName}>
      {(fields, { add, remove }) => (
        <Card
          title={title}
          extra={
            <Button icon={<PlusOutlined />} onClick={() => add({ responsibilityItems: [], techStacks: [] })}>
              新增
            </Button>
          }
        >
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {fields.map((field) => (
              <Card
                key={field.key}
                size="small"
                type="inner"
                title={`${title} #${field.name + 1}`}
                extra={<Button danger type="text" icon={<DeleteOutlined />} onClick={() => remove(field.name)} />}
              >
                <Form.Item
                  label="名称"
                  name={[field.name, 'name']}
                  rules={[{ required: true, message: `请输入${title}名称` }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item label="时间范围" name={[field.name, 'periodText']}>
                  <Input placeholder="例如 2023.07 - 2024.02" />
                </Form.Item>
                <Form.Item label="项目介绍 / 经历介绍" name={[field.name, 'summaryMarkdown']}>
                  <MarkdownWorkbench rows={10} />
                </Form.Item>
                <Form.List name={[field.name, 'responsibilityItems']}>
                  {(responsibilityFields, responsibilityOps) => (
                    <Card
                      size="small"
                      title="所做工作"
                      extra={
                        <Button icon={<PlusOutlined />} onClick={() => responsibilityOps.add('')}>
                          新增条目
                        </Button>
                      }
                    >
                      <Space direction="vertical" size={12} style={{ width: '100%' }}>
                        {responsibilityFields.map((responsibilityField) => (
                          <Space key={responsibilityField.key} align="start" style={{ display: 'flex' }}>
                            <Form.Item
                              style={{ flex: 1, marginBottom: 0 }}
                              name={[responsibilityField.name]}
                              rules={[{ required: true, message: '请输入工作描述' }]}
                            >
                              <Input.TextArea rows={3} placeholder="支持 Markdown，例如 **加粗** 内容" />
                            </Form.Item>
                            <Button danger type="text" icon={<DeleteOutlined />} onClick={() => responsibilityOps.remove(responsibilityField.name)} />
                          </Space>
                        ))}
                      </Space>
                    </Card>
                  )}
                </Form.List>
                <Form.List name={[field.name, 'techStacks']}>
                  {(techFields, techOps) => (
                    <Card
                      size="small"
                      title="技术栈"
                      extra={
                        <Button icon={<PlusOutlined />} onClick={() => techOps.add('')}>
                          新增技术项
                        </Button>
                      }
                    >
                      <Space direction="vertical" size={8} style={{ width: '100%' }}>
                        {techFields.map((techField) => (
                          <Space key={techField.key} align="start" style={{ display: 'flex' }}>
                            <Form.Item style={{ flex: 1, marginBottom: 0 }} name={[techField.name]}>
                              <Input placeholder="例如 Spring Boot / React / PostgreSQL" />
                            </Form.Item>
                            <Button danger type="text" icon={<DeleteOutlined />} onClick={() => techOps.remove(techField.name)} />
                          </Space>
                        ))}
                      </Space>
                    </Card>
                  )}
                </Form.List>
              </Card>
            ))}
            {!fields.length ? <Typography.Text type="secondary">暂未配置{title}。</Typography.Text> : null}
          </Space>
        </Card>
      )}
    </Form.List>
  );

  return (
    <div className="admin-page author-admin-page">
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={13}>
          <Card title="关于作者配置">
            <Form<AuthorProfileFormValues>
              form={form}
              layout="vertical"
              onFinish={(values) => saveMutation.mutate(values)}
              initialValues={emptyFormValues()}
            >
              <Card title="基本信息" size="small" className="author-admin-section-card">
                <Row gutter={12}>
                  <Col xs={24} md={12}>
                    <Form.Item label="名字" name="name" rules={[{ required: true, message: '请输入作者名字' }]}>
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="性别" name="gender">
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="邮箱" name="email">
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="电话" name="phone">
                      <Input />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="年龄" name="age">
                      <InputNumber min={0} max={150} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="照片">
                      <Space direction="vertical" size={8}>
                        <Upload {...uploadProps}>
                          <Button icon={<UploadOutlined />} loading={uploadPhotoMutation.isPending}>
                            上传作者照片
                          </Button>
                        </Upload>
                        {photoState.photoUrl ? (
                          <Typography.Text type="secondary">
                            已上传照片{photoState.photoContentLength ? `（${Math.round(photoState.photoContentLength / 1024)} KB）` : ''}
                          </Typography.Text>
                        ) : (
                          <Typography.Text type="secondary">尚未上传作者照片</Typography.Text>
                        )}
                      </Space>
                    </Form.Item>
                  </Col>
                </Row>
              </Card>

              <Form.List name="educations">
                {(fields, { add, remove }) => (
                  <Card
                    title="教育信息"
                    className="author-admin-section-card"
                    extra={<Button icon={<PlusOutlined />} onClick={() => add({ honors: [] })}>新增教育经历</Button>}
                  >
                    <Space direction="vertical" size={16} style={{ width: '100%' }}>
                      {fields.map((field) => (
                        <Card
                          key={field.key}
                          size="small"
                          type="inner"
                          title={`教育经历 #${field.name + 1}`}
                          extra={<Button danger type="text" icon={<DeleteOutlined />} onClick={() => remove(field.name)} />}
                        >
                          <Row gutter={12}>
                            <Col xs={24} md={12}>
                              <Form.Item label="阶段" name={[field.name, 'stageLabel']}>
                                <Input placeholder="本科 / 硕士研究生" />
                              </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                              <Form.Item label="学校" name={[field.name, 'schoolName']} rules={[{ required: true, message: '请输入学校名称' }]}>
                                <Input />
                              </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                              <Form.Item label="时间" name={[field.name, 'periodText']}>
                                <Input placeholder="例如 2018.09 - 2022.06" />
                              </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                              <Form.Item label="专业" name={[field.name, 'major']}>
                                <Input />
                              </Form.Item>
                            </Col>
                          </Row>
                          <Form.List name={[field.name, 'honors']}>
                            {(honorFields, honorOps) => (
                              <Card
                                size="small"
                                title="荣誉与奖项"
                                extra={<Button icon={<PlusOutlined />} onClick={() => honorOps.add('')}>新增奖项</Button>}
                              >
                                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                                  {honorFields.map((honorField) => (
                                    <Space key={honorField.key} align="start" style={{ display: 'flex' }}>
                                      <Form.Item style={{ flex: 1, marginBottom: 0 }} name={[honorField.name]}>
                                        <Input />
                                      </Form.Item>
                                      <Button danger type="text" icon={<DeleteOutlined />} onClick={() => honorOps.remove(honorField.name)} />
                                    </Space>
                                  ))}
                                </Space>
                              </Card>
                            )}
                          </Form.List>
                        </Card>
                      ))}
                      {!fields.length ? <Typography.Text type="secondary">暂未配置教育信息。</Typography.Text> : null}
                    </Space>
                  </Card>
                )}
              </Form.List>

              <Form.List name="skills">
                {(fields, { add, remove }) => (
                  <Card
                    title="专业技能"
                    className="author-admin-section-card"
                    extra={<Button icon={<PlusOutlined />} onClick={() => add({ label: '', descriptionMarkdown: '' })}>新增技能</Button>}
                  >
                    <Space direction="vertical" size={16} style={{ width: '100%' }}>
                      {fields.map((field) => (
                        <Card
                          key={field.key}
                          size="small"
                          type="inner"
                          title={`技能 #${field.name + 1}`}
                          extra={<Button danger type="text" icon={<DeleteOutlined />} onClick={() => remove(field.name)} />}
                        >
                          <Form.Item label="短语标题" name={[field.name, 'label']} rules={[{ required: true, message: '请输入技能短语' }]}>
                            <Input placeholder="例如 分布式系统 / LLM 应用开发" />
                          </Form.Item>
                          <Form.Item label="具体描述（支持 Markdown）" name={[field.name, 'descriptionMarkdown']}>
                            <MarkdownWorkbench rows={8} />
                          </Form.Item>
                        </Card>
                      ))}
                      {!fields.length ? <Typography.Text type="secondary">暂未配置专业技能。</Typography.Text> : null}
                    </Space>
                  </Card>
                )}
              </Form.List>

              {renderExperienceList('workExperiences', '工作经历')}
              {renderExperienceList('internshipExperiences', '实习经历')}
              {renderExperienceList('projectExperiences', '项目经历')}

              <Form.List name="customSections">
                {(fields, { add, remove }) => (
                  <Card
                    title="自定义模块"
                    className="author-admin-section-card"
                    extra={<Button icon={<PlusOutlined />} onClick={() => add({ sectionTitle: '', items: [] })}>新增模块</Button>}
                  >
                    <Space direction="vertical" size={16} style={{ width: '100%' }}>
                      {fields.map((field) => (
                        <Card
                          key={field.key}
                          size="small"
                          type="inner"
                          title={`自定义模块 #${field.name + 1}`}
                          extra={<Button danger type="text" icon={<DeleteOutlined />} onClick={() => remove(field.name)} />}
                        >
                          <Form.Item label="模块名称" name={[field.name, 'sectionTitle']}>
                            <Input />
                          </Form.Item>
                          <Form.List name={[field.name, 'items']}>
                            {(itemFields, itemOps) => (
                              <Card
                                size="small"
                                title="模块内容项"
                                extra={<Button icon={<PlusOutlined />} onClick={() => itemOps.add({ itemTitle: '', periodText: '', descriptionMarkdown: '' })}>新增条目</Button>}
                              >
                                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                                  {itemFields.map((itemField) => (
                                    <Card
                                      key={itemField.key}
                                      size="small"
                                      type="inner"
                                      title={`条目 #${itemField.name + 1}`}
                                      extra={<Button danger type="text" icon={<DeleteOutlined />} onClick={() => itemOps.remove(itemField.name)} />}
                                    >
                                      <Form.Item label="条目名称" name={[itemField.name, 'itemTitle']}>
                                        <Input />
                                      </Form.Item>
                                      <Form.Item label="时间" name={[itemField.name, 'periodText']}>
                                        <Input />
                                      </Form.Item>
                                      <Form.Item label="具体描述（支持 Markdown）" name={[itemField.name, 'descriptionMarkdown']}>
                                        <MarkdownWorkbench rows={8} />
                                      </Form.Item>
                                    </Card>
                                  ))}
                                </Space>
                              </Card>
                            )}
                          </Form.List>
                        </Card>
                      ))}
                      {!fields.length ? <Typography.Text type="secondary">暂未配置自定义模块。</Typography.Text> : null}
                    </Space>
                  </Card>
                )}
              </Form.List>

              <Space>
                <Button type="primary" htmlType="submit" loading={saveMutation.isPending}>
                  保存关于作者页
                </Button>
                <Button onClick={() => form.setFieldsValue(toFormValues(authorProfileQuery.data))} disabled={saveMutation.isPending}>
                  重置为已保存内容
                </Button>
              </Space>
            </Form>
          </Card>
        </Col>

        <Col xs={24} xl={11}>
          <Card title="实时预览" className="author-admin-preview-card">
            <AuthorProfileContent
              profile={previewProfile}
              emptyDescription="填写左侧内容后，这里会实时显示“关于作者”页面效果。"
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
