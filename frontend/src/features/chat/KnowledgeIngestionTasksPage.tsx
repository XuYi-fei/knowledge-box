import { CheckCircleOutlined, ClockCircleOutlined, DeleteOutlined, FileOutlined, FileSearchOutlined, FileTextOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Empty, List, Progress, Space, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import type { KnowledgeIngestionTask } from '../../lib/types';

function canDeleteTask(status: KnowledgeIngestionTask['status']) {
  return ['CANCELLED', 'FAILED', 'COMPLETED', 'PARTIAL_FAILED'].includes(status);
}

function taskStatusColor(status: KnowledgeIngestionTask['status']) {
  switch (status) {
    case 'CANCELLED':
    case 'FAILED':
      return 'red';
    case 'COMPLETED':
      return 'green';
    case 'PARTIAL_FAILED':
      return 'orange';
    default:
      return 'blue';
  }
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function KnowledgeIngestionTasksPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { modal, message } = App.useApp();
  const tasksQuery = useQuery({
    queryKey: ['knowledgeIngestionTasks'],
    queryFn: api.listKnowledgeIngestionTasks,
    refetchInterval: (query) => {
      const tasks = query.state.data ?? [];
      return tasks.some((task) => ['QUEUED', 'RUNNING', 'PARTIAL_FAILED', 'CANCELLING'].includes(task.status)) ? 3000 : false;
    },
  });
  const deleteMutation = useMutation({
    mutationFn: (taskId: number) => api.deleteKnowledgeIngestionTask(taskId),
    onSuccess: async () => {
      message.success('任务已删除，已保留已生成的待审核单');
      await queryClient.invalidateQueries({ queryKey: ['knowledgeIngestionTasks'] });
    },
    onError: (error) => {
      message.error(buildErrorSummary(error, '删除任务失败，请稍后重试'));
    },
  });

  const openDeleteConfirm = (task: KnowledgeIngestionTask) => {
    modal.confirm({
      title: '确认删除该任务？',
      content: '将删除任务记录、阶段记录、子文档记录和源文件；已生成的待审核单不会删除。',
      okText: '删除任务',
      okButtonProps: { danger: true, loading: deleteMutation.isPending },
      cancelText: '取消',
      onOk: async () => deleteMutation.mutateAsync(task.id),
    });
  };

  return (
    <div className="chat-shell" style={{ overflowY: 'auto', overflowX: 'hidden' }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card
          className="chat-panel chat-card"
          title="知识入库任务中心"
          extra={
            <Button type="primary" onClick={() => void navigate('/ingest')}>
              新建入库任务
            </Button>
          }
        >
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Typography.Paragraph style={{ marginBottom: 0 }}>
              大文件 PDF 会进入异步拆解任务。这里会持续展示阶段进度、已生成的子文档数量，以及每个任务的入口。
            </Typography.Paragraph>
            <Typography.Text type="secondary">
              处理中时会自动刷新；进入任务详情后，可查看阶段日志、实时产物并执行中断。
            </Typography.Text>
          </Space>
        </Card>

        {tasksQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="任务列表加载失败"
            description={buildErrorSummary(tasksQuery.error, '请稍后重试')}
          />
        ) : null}

        <Card className="chat-panel chat-card" title="最近任务">
          {tasksQuery.isLoading ? (
            <Typography.Text type="secondary">正在载入任务列表...</Typography.Text>
          ) : (tasksQuery.data ?? []).length === 0 ? (
            <Empty
              description="还没有异步入库任务"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          ) : (
            <List
              dataSource={tasksQuery.data}
              renderItem={(task) => (
                <List.Item
                  key={task.id}
                  actions={[
                    <Button key="detail" type="link" onClick={() => void navigate(`/ingest/tasks/${task.id}`)}>
                      查看详情
                    </Button>,
                    <Button
                      key="source"
                      type="link"
                      icon={<FileOutlined />}
                      disabled={!task.sourceFileUrl}
                      onClick={() => window.open(task.sourceFileUrl ?? '', '_blank')}
                    >
                      查看源文件
                    </Button>,
                    canDeleteTask(task.status) ? (
                      <Button
                        key="delete"
                        type="link"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => openDeleteConfirm(task)}
                      >
                        删除任务
                      </Button>
                    ) : null,
                  ].filter(Boolean)}
                >
                  <List.Item.Meta
                    avatar={task.status === 'COMPLETED' ? <CheckCircleOutlined /> : <ClockCircleOutlined />}
                    title={
                      <Space wrap>
                        <Typography.Text strong>{task.sourceFilename}</Typography.Text>
                        <Tag color={taskStatusColor(task.status)}>{task.status}</Tag>
                        <Typography.Text type="secondary">{task.taskCode}</Typography.Text>
                      </Space>
                    }
                    description={
                      <Space direction="vertical" size={8} style={{ width: '100%' }}>
                        <Space wrap size={[12, 4]}>
                          <Typography.Text type="secondary">
                            <FileTextOutlined /> 子文档 {task.documents.length} 个
                          </Typography.Text>
                          <Typography.Text type="secondary">
                            <FileSearchOutlined /> 页数 {task.pageCount ?? '未知'}
                          </Typography.Text>
                          <Typography.Text type="secondary">创建于 {formatDateTime(task.createdAt)}</Typography.Text>
                        </Space>
                        <Progress percent={task.progressPercent} status={task.status === 'FAILED' ? 'exception' : 'active'} />
                        {task.failureReason ? <Typography.Text type="danger">{task.failureReason}</Typography.Text> : null}
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          )}
        </Card>
      </Space>
    </div>
  );
}
