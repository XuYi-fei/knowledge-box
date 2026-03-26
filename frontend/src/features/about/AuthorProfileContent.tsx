import { CalendarOutlined, MailOutlined, PhoneOutlined } from '@ant-design/icons';
import { Card, Empty, Image, Space, Tag, Typography } from 'antd';
import type { AuthorCustomSection, AuthorExperienceItem, AuthorProfile } from '../../lib/types';
import { MarkdownRenderer } from '../admin/components/MarkdownRenderer';

type AuthorProfileContentProps = {
  profile: AuthorProfile | null | undefined;
  emptyDescription?: string;
};

function MetaItem({ icon, text }: { icon: React.ReactNode; text: string | null | undefined }) {
  if (!text) {
    return null;
  }
  return (
    <span className="author-profile-meta-item">
      {icon}
      <span>{text}</span>
    </span>
  );
}

function ExperienceSection({ title, items }: { title: string; items: AuthorExperienceItem[] }) {
  if (!items.length) {
    return null;
  }
  return (
    <section className="author-profile-section">
      <div className="author-profile-section-heading">
        <Typography.Title level={4}>{title}</Typography.Title>
      </div>
      <div className="author-profile-timeline">
        {items.map((item, index) => (
          <Card key={`${title}-${item.name}-${index}`} className="author-profile-timeline-card">
            <div className="author-profile-timeline-header">
              <div>
                <Typography.Title level={5}>{item.name}</Typography.Title>
                {item.periodText ? (
                  <Typography.Text type="secondary">{item.periodText}</Typography.Text>
                ) : null}
              </div>
              {item.techStacks.length ? (
                <Space wrap>
                  {item.techStacks.map((tech) => (
                    <Tag key={`${item.name}-${tech}`} color="geekblue" bordered={false}>
                      {tech}
                    </Tag>
                  ))}
                </Space>
              ) : null}
            </div>
            {item.summaryMarkdown ? (
              <div className="author-profile-markdown">
                <MarkdownRenderer content={item.summaryMarkdown} />
              </div>
            ) : null}
            {item.responsibilityItems.length ? (
              <div className="author-profile-responsibilities">
                <Typography.Text strong>所做工作</Typography.Text>
                <ul>
                  {item.responsibilityItems.map((responsibility, responsibilityIndex) => (
                    <li key={`${item.name}-responsibility-${responsibilityIndex}`} className="author-profile-markdown">
                      <MarkdownRenderer content={responsibility} />
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </Card>
        ))}
      </div>
    </section>
  );
}

function CustomSectionBlock({ section }: { section: AuthorCustomSection }) {
  if (!section.items.length) {
    return null;
  }
  return (
    <section className="author-profile-section">
      <div className="author-profile-section-heading">
        <Typography.Title level={4}>{section.sectionTitle || '自定义模块'}</Typography.Title>
      </div>
      <div className="author-profile-timeline">
        {section.items.map((item, index) => (
          <Card key={`${section.sectionTitle || 'custom'}-${index}`} className="author-profile-timeline-card">
            <div className="author-profile-timeline-header">
              <div>
                {item.itemTitle ? <Typography.Title level={5}>{item.itemTitle}</Typography.Title> : null}
                {item.periodText ? <Typography.Text type="secondary">{item.periodText}</Typography.Text> : null}
              </div>
            </div>
            {item.descriptionMarkdown ? (
              <div className="author-profile-markdown">
                <MarkdownRenderer content={item.descriptionMarkdown} />
              </div>
            ) : null}
          </Card>
        ))}
      </div>
    </section>
  );
}

export function AuthorProfileContent({
  profile,
  emptyDescription = '作者主页尚未配置。',
}: AuthorProfileContentProps) {
  if (!profile?.configured) {
    return (
      <div className="chat-empty-state">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription} />
      </div>
    );
  }

  return (
    <div className="author-profile">
      <section className="author-profile-hero">
        <div className="author-profile-hero-copy">
          <Typography.Text className="author-profile-kicker">ABOUT THE AUTHOR</Typography.Text>
          <Typography.Title>{profile.name}</Typography.Title>
          <Typography.Paragraph type="secondary" className="author-profile-summary">
            一个可公开展示的结构化个人主页，覆盖教育背景、技能栈与项目经历。
          </Typography.Paragraph>
          <div className="author-profile-meta">
            <MetaItem icon={<MailOutlined />} text={profile.email} />
            <MetaItem icon={<PhoneOutlined />} text={profile.phone} />
            <MetaItem icon={<CalendarOutlined />} text={profile.age != null ? `${profile.age} 岁` : null} />
            <MetaItem icon={<CalendarOutlined />} text={profile.gender} />
          </div>
        </div>
        <div className="author-profile-hero-photo">
          {profile.photoUrl ? (
            <Image
              src={profile.photoUrl}
              alt={profile.name ?? 'author'}
              preview={false}
              className="author-profile-photo"
            />
          ) : (
            <div className="author-profile-photo author-profile-photo-placeholder">
              <span>{profile.name?.slice(0, 1) ?? 'A'}</span>
            </div>
          )}
        </div>
      </section>

      {profile.educations.length ? (
        <section className="author-profile-section">
          <div className="author-profile-section-heading">
            <Typography.Title level={4}>教育信息</Typography.Title>
          </div>
          <div className="author-profile-grid">
            {profile.educations.map((education, index) => (
              <Card key={`${education.schoolName}-${index}`} className="author-profile-grid-card">
                <Space direction="vertical" size={6}>
                  <Space wrap>
                    {education.stageLabel ? <Tag color="green">{education.stageLabel}</Tag> : null}
                    {education.periodText ? <Tag>{education.periodText}</Tag> : null}
                  </Space>
                  <Typography.Title level={5}>{education.schoolName}</Typography.Title>
                  {education.major ? <Typography.Text>{education.major}</Typography.Text> : null}
                  {education.honors.length ? (
                    <div className="author-profile-awards">
                      {education.honors.map((honor, honorIndex) => (
                        <Tag key={`${education.schoolName}-honor-${honorIndex}`} color="gold">
                          {honor}
                        </Tag>
                      ))}
                    </div>
                  ) : null}
                </Space>
              </Card>
            ))}
          </div>
        </section>
      ) : null}

      {profile.skills.length ? (
        <section className="author-profile-section">
          <div className="author-profile-section-heading">
            <Typography.Title level={4}>专业技能</Typography.Title>
          </div>
          <div className="author-profile-grid">
            {profile.skills.map((skill, index) => (
              <Card key={`${skill.label}-${index}`} className="author-profile-grid-card">
                <Typography.Paragraph className="author-profile-skill-title">
                  <strong>{skill.label}</strong>
                  <span>：</span>
                </Typography.Paragraph>
                {skill.descriptionMarkdown ? (
                  <div className="author-profile-markdown">
                    <MarkdownRenderer content={skill.descriptionMarkdown} />
                  </div>
                ) : null}
              </Card>
            ))}
          </div>
        </section>
      ) : null}

      <ExperienceSection title="工作经历" items={profile.workExperiences} />
      <ExperienceSection title="实习经历" items={profile.internshipExperiences} />
      <ExperienceSection title="项目经历" items={profile.projectExperiences} />

      {profile.customSections.map((section, index) => (
        <CustomSectionBlock key={`${section.sectionTitle || 'section'}-${index}`} section={section} />
      ))}
    </div>
  );
}
