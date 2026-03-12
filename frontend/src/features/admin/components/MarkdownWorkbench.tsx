import {
  BoldOutlined,
  CodeOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
  ItalicOutlined,
  LinkOutlined,
  PictureOutlined,
} from '@ant-design/icons';
import { App, Button, Input, Segmented, Space, Typography } from 'antd';
import type { InputRef } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { MarkdownRenderer } from './MarkdownRenderer';

type EditorMode = 'split' | 'edit' | 'preview';

type MarkdownWorkbenchProps = {
  value?: string;
  onChange?: (next: string) => void;
  readOnly?: boolean;
  rows?: number;
  onPasteImage?: (file: File) => Promise<string>;
};

type SelectionRange = {
  start: number;
  end: number;
};

function normalizeImageAlt(name: string) {
  const cleaned = name.replace(/\.[^.]+$/, '').trim();
  if (!cleaned) {
    return 'image';
  }
  return cleaned.replaceAll(/\s+/g, '-');
}

function replaceRange(source: string, start: number, end: number, replacement: string) {
  return source.slice(0, start) + replacement + source.slice(end);
}

export function MarkdownWorkbench({
  value = '',
  onChange,
  readOnly = false,
  rows = 22,
  onPasteImage,
}: MarkdownWorkbenchProps) {
  const { message } = App.useApp();
  const textAreaRef = useRef<InputRef>(null);
  const previewRef = useRef<HTMLDivElement | null>(null);
  const imageInputRef = useRef<HTMLInputElement | null>(null);
  const syncingSourceRef = useRef<'editor' | 'preview' | null>(null);
  const valueRef = useRef(value);
  const [mode, setMode] = useState<EditorMode>('split');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [uploadingPasteCount, setUploadingPasteCount] = useState(0);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  useEffect(() => {
    if (readOnly || !isFullscreen) {
      return;
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsFullscreen(false);
      }
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [isFullscreen, readOnly]);

  const uploadStatusText = uploadingPasteCount > 0 ? `图片上传中 (${uploadingPasteCount})` : '';
  const getTextAreaNode = () => {
    const candidate = textAreaRef.current as unknown as { resizableTextArea?: { textArea?: HTMLTextAreaElement } } | null;
    return candidate?.resizableTextArea?.textArea ?? null;
  };
  const resolveSelectionRange = (source: string, textarea: HTMLTextAreaElement | null): SelectionRange => ({
    start: textarea?.selectionStart ?? source.length,
    end: textarea?.selectionEnd ?? source.length,
  });
  const focusAndSetSelection = (selection: SelectionRange) => {
    requestAnimationFrame(() => {
      const textarea = getTextAreaNode();
      if (!textarea) {
        return;
      }
      textarea.focus();
      textarea.setSelectionRange(selection.start, selection.end);
    });
  };
  const commitValue = (nextValue: string, nextSelection?: SelectionRange) => {
    valueRef.current = nextValue;
    onChange?.(nextValue);
    if (nextSelection) {
      focusAndSetSelection(nextSelection);
    }
  };
  const applyInlineWrapper = (prefix: string, suffix: string, placeholder: string) => {
    const source = valueRef.current;
    const textarea = getTextAreaNode();
    const { start, end } = resolveSelectionRange(source, textarea);
    const selectedText = source.slice(start, end);
    const content = selectedText || placeholder;
    const replacement = `${prefix}${content}${suffix}`;
    const nextValue = replaceRange(source, start, end, replacement);
    const selectionStart = start + prefix.length;
    const selectionEnd = selectionStart + content.length;
    commitValue(nextValue, { start: selectionStart, end: selectionEnd });
  };
  const applyLink = () => {
    const source = valueRef.current;
    const textarea = getTextAreaNode();
    const { start, end } = resolveSelectionRange(source, textarea);
    const selectedText = source.slice(start, end);
    const label = selectedText || '链接文本';
    const urlPlaceholder = 'https://';
    const replacement = `[${label}](${urlPlaceholder})`;
    const nextValue = replaceRange(source, start, end, replacement);

    if (selectedText) {
      const urlStart = start + label.length + 3;
      commitValue(nextValue, { start: urlStart, end: urlStart + urlPlaceholder.length });
      return;
    }
    const labelStart = start + 1;
    commitValue(nextValue, { start: labelStart, end: labelStart + label.length });
  };
  const insertUploadedImages = async (files: File[]) => {
    if (readOnly || !onPasteImage || files.length === 0) {
      return;
    }
    setUploadingPasteCount((current) => current + files.length);

    const textarea = getTextAreaNode();
    let nextValue = valueRef.current;
    let selection = resolveSelectionRange(nextValue, textarea);
    try {
      for (const image of files) {
        const uploadedUrl = await onPasteImage(image);
        const markdownSnippet = `\n![${normalizeImageAlt(image.name || 'image')}](${uploadedUrl})\n`;
        nextValue = replaceRange(nextValue, selection.start, selection.end, markdownSnippet);
        const nextCaret = selection.start + markdownSnippet.length;
        selection = { start: nextCaret, end: nextCaret };
      }
      commitValue(nextValue, selection);
      message.success('图片已上传并插入 Markdown');
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '图片上传失败';
      message.error(errorMessage);
    } finally {
      setUploadingPasteCount((current) => Math.max(0, current - files.length));
      if (imageInputRef.current) {
        imageInputRef.current.value = '';
      }
    }
  };

  useEffect(() => {
    if (readOnly || mode !== 'split') {
      return;
    }
    const editorNode = getTextAreaNode();
    const previewNode = previewRef.current;
    if (!editorNode || !previewNode) {
      return;
    }

    const maxScrollable = (node: HTMLElement) => Math.max(node.scrollHeight - node.clientHeight, 0);
    const toRatio = (node: HTMLElement) => {
      const max = maxScrollable(node);
      return max <= 0 ? 0 : node.scrollTop / max;
    };
    const syncToRatio = (node: HTMLElement, ratio: number) => {
      const max = maxScrollable(node);
      node.scrollTop = max <= 0 ? 0 : ratio * max;
    };
    const releaseSyncLock = (source: 'editor' | 'preview') => {
      requestAnimationFrame(() => {
        if (syncingSourceRef.current === source) {
          syncingSourceRef.current = null;
        }
      });
    };

    const handleEditorScroll = () => {
      if (syncingSourceRef.current === 'preview') {
        return;
      }
      syncingSourceRef.current = 'editor';
      syncToRatio(previewNode, toRatio(editorNode));
      releaseSyncLock('editor');
    };
    const handlePreviewScroll = () => {
      if (syncingSourceRef.current === 'editor') {
        return;
      }
      syncingSourceRef.current = 'preview';
      syncToRatio(editorNode, toRatio(previewNode));
      releaseSyncLock('preview');
    };

    syncToRatio(previewNode, toRatio(editorNode));
    editorNode.addEventListener('scroll', handleEditorScroll, { passive: true });
    previewNode.addEventListener('scroll', handlePreviewScroll, { passive: true });
    return () => {
      editorNode.removeEventListener('scroll', handleEditorScroll);
      previewNode.removeEventListener('scroll', handlePreviewScroll);
      syncingSourceRef.current = null;
    };
  }, [mode, readOnly]);

  const handlePaste = async (event: React.ClipboardEvent<HTMLTextAreaElement>) => {
    if (readOnly || !onPasteImage) {
      return;
    }
    const imageFiles = Array.from(event.clipboardData.items)
      .filter((item) => item.type.startsWith('image/'))
      .map((item) => item.getAsFile())
      .filter((item): item is File => item instanceof File);
    if (imageFiles.length === 0) {
      return;
    }
    event.preventDefault();
    await insertUploadedImages(imageFiles);
  };
  const handleImageSelect = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const imageFiles = Array.from(event.target.files ?? []).filter((file) => file.type.startsWith('image/'));
    await insertUploadedImages(imageFiles);
  };

  const showEditor = !readOnly && mode !== 'preview';
  const showPreview = readOnly || mode !== 'edit';
  const imageActionDisabled = !onPasteImage || uploadingPasteCount > 0;

  return (
    <div className={`markdown-workbench${isFullscreen ? ' markdown-workbench-fullscreen' : ''}`}>
      {!readOnly ? (
        <div className="markdown-workbench-toolbar">
          <Space>
            <Typography.Text type="secondary">
              支持 Markdown + 代码块；可直接粘贴截图自动上传
            </Typography.Text>
            {uploadStatusText ? <Typography.Text type="secondary">{uploadStatusText}</Typography.Text> : null}
          </Space>
          <div className="markdown-workbench-toolbar-actions">
            <Space wrap size={6}>
              <Button size="small" icon={<BoldOutlined />} onClick={() => applyInlineWrapper('**', '**', '文本')}>
                加粗
              </Button>
              <Button size="small" icon={<ItalicOutlined />} onClick={() => applyInlineWrapper('*', '*', '文本')}>
                斜体
              </Button>
              <Button size="small" icon={<CodeOutlined />} onClick={() => applyInlineWrapper('`', '`', 'code')}>
                行内代码
              </Button>
              <Button size="small" icon={<LinkOutlined />} onClick={applyLink}>
                链接
              </Button>
              <Button
                size="small"
                icon={<PictureOutlined />}
                disabled={imageActionDisabled}
                loading={uploadingPasteCount > 0}
                onClick={() => imageInputRef.current?.click()}
              >
                图片
              </Button>
            </Space>
            <Segmented<EditorMode>
              value={mode}
              options={[
                { label: '左右预览', value: 'split' },
                { label: '全编辑', value: 'edit' },
                { label: '全预览', value: 'preview' },
              ]}
              onChange={setMode}
            />
            <Button
              icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
              onClick={() => setIsFullscreen((current) => !current)}
            >
              {isFullscreen ? '退出全屏' : '全屏编辑'}
            </Button>
          </div>
          <input
            ref={imageInputRef}
            type="file"
            accept="image/*"
            multiple
            style={{ display: 'none' }}
            onChange={(event) => {
              void handleImageSelect(event);
            }}
          />
        </div>
      ) : null}

      <div className={`markdown-workbench-body markdown-workbench-body-${readOnly ? 'preview' : mode}`}>
        {showEditor ? (
          <div className="markdown-workbench-panel">
            <Input.TextArea
              ref={textAreaRef}
              value={value}
              onChange={(event) => onChange?.(event.target.value)}
              onPaste={(event) => {
                void handlePaste(event);
              }}
              rows={rows}
              autoSize={false}
            />
          </div>
        ) : null}

        {showPreview ? (
          <div ref={previewRef} className="markdown-workbench-panel markdown-workbench-preview">
            <MarkdownRenderer content={value} />
          </div>
        ) : null}
      </div>
    </div>
  );
}
