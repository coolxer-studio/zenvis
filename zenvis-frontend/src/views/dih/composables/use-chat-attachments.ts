import { computed, onUnmounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { DihService } from '@/service/api';
import type { ChatAttachment } from '@/types/type-dih';
import { withBaseUrl } from '@/utils/url';

const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
const MAX_FILES_PER_PICK = 10;
const UPLOAD_CONCURRENCY = 3;
const PARSE_POLL_INTERVAL_MS = 1500;
const TERMINAL_STATUSES = new Set(['ready', 'partial', 'unsupported', 'failed']);

export const useChatAttachments = () => {
  const pendingAttachments = ref<ChatAttachment[]>([]);
  const isUploadingAttachment = ref(false);
  const uploadProgress = ref(0);
  const pollingTimers = new Map<string, number>();
  const progressByFile = new Map<File, number>();

  const attachmentFileId = (attachment: ChatAttachment) => {
    return attachment.file_id || attachment.fileId || attachment.file_name || attachment.fileName || 'attachment';
  };

  const attachmentFileName = (attachment: ChatAttachment) => {
    return attachment.file_name || attachment.fileName || '未命名文件';
  };

  const attachmentStatus = (attachment: ChatAttachment) => {
    return (attachment.parse_status || attachment.parseStatus || '').toLowerCase();
  };

  const attachmentProgress = (attachment: ChatAttachment) => {
    const progress = attachment.parse_progress ?? attachment.parseProgress ?? 0;
    return Math.max(0, Math.min(100, progress));
  };

  const attachmentStatusText = (attachment: ChatAttachment) => {
    const status = attachmentStatus(attachment);
    if (status === 'pending') return '等待解析';
    if (status === 'processing') return `解析中 ${attachmentProgress(attachment)}%`;
    if (status === 'ready') return '解析完成';
    if (status === 'partial') return '部分可用';
    if (status === 'unsupported') return '仅元信息';
    if (status === 'failed') return '解析失败';
    if (status === 'image' || status === 'readable') return '可用';
    return status || '待解析';
  };

  const attachmentWarning = (attachment: ChatAttachment) => {
    const status = attachmentStatus(attachment);
    if (!['partial', 'unsupported', 'failed'].includes(status)) {
      return attachment.warning || '';
    }
    return attachment.warning || attachment.parse_message || attachment.parseMessage || '';
  };

  const formatFileSize = (size?: number) => {
    if (!size || size <= 0) return '';
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  };

  const attachmentTypeLabel = (attachment: ChatAttachment) => {
    const contentType = attachment.content_type || attachment.contentType || '';
    const fileName = attachmentFileName(attachment);
    const extension = fileName.includes('.') ? fileName.split('.').pop()?.toUpperCase() : '';
    if (extension) return extension;
    return contentType.split('/').pop()?.toUpperCase() || '文件';
  };

  const isImageAttachment = (attachment: ChatAttachment) => {
    const contentType = attachment.content_type || attachment.contentType || '';
    const fileName = attachmentFileName(attachment).toLowerCase();
    return attachment.kind === 'image'
      || ['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/bmp'].includes(contentType.toLowerCase())
      || /\.(png|jpe?g|webp|gif|bmp)$/i.test(fileName);
  };

  const attachmentPreviewUrl = (attachment: ChatAttachment) => {
    const url = attachment.file_url || attachment.fileUrl || '';
    return url ? withBaseUrl(url) : '';
  };

  const saveBlob = (blob: Blob, fileName: string) => {
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = fileName || 'attachment';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
  };

  const openAttachmentPreview = async (attachment: ChatAttachment) => {
    const fileId = attachment.file_id || attachment.fileId;
    if (isImageAttachment(attachment)) {
      const url = attachmentPreviewUrl(attachment);
      if (url) window.open(url, '_blank', 'noopener,noreferrer');
      return;
    }
    if (!fileId) return;
    try {
      const blob = await DihService.downloadAttachment(fileId);
      saveBlob(blob, attachmentFileName(attachment));
    } catch (error) {
      console.error('附件下载失败', error);
      ElMessage.error('附件下载失败，请重试');
    }
  };

  const stopPolling = (fileId: string) => {
    const timer = pollingTimers.get(fileId);
    if (timer) clearTimeout(timer);
    pollingTimers.delete(fileId);
  };

  const updateAttachment = (fileId: string, next: ChatAttachment) => {
    const index = pendingAttachments.value.findIndex(item => attachmentFileId(item) === fileId);
    if (index >= 0) pendingAttachments.value[index] = { ...pendingAttachments.value[index], ...next };
    return index;
  };

  const pollAttachmentStatus = (attachment: ChatAttachment) => {
    const fileId = attachment.file_id || attachment.fileId;
    if (!fileId) return;
    stopPolling(fileId);
    const poll = async () => {
      const index = pendingAttachments.value.findIndex(item => attachmentFileId(item) === fileId);
      if (index < 0) {
        stopPolling(fileId);
        return;
      }
      try {
        const status = await DihService.getAttachmentStatus(fileId);
        updateAttachment(fileId, status);
        if (!TERMINAL_STATUSES.has(attachmentStatus(status))) {
          pollingTimers.set(fileId, window.setTimeout(poll, PARSE_POLL_INTERVAL_MS));
        } else {
          stopPolling(fileId);
          if (attachmentStatus(status) === 'failed') {
            ElMessage.error(`${attachmentFileName(status)} 解析失败，可点击重试`);
          }
        }
      } catch (error) {
        console.warn('附件解析状态查询失败，稍后重试', error);
        pollingTimers.set(fileId, window.setTimeout(poll, PARSE_POLL_INTERVAL_MS * 2));
      }
    };
    pollingTimers.set(fileId, window.setTimeout(poll, 300));
  };

  const removePendingAttachment = async (index: number) => {
    const attachment = pendingAttachments.value[index];
    if (!attachment) return;
    const fileId = attachment.file_id || attachment.fileId;
    pendingAttachments.value.splice(index, 1);
    if (!fileId) return;
    stopPolling(fileId);
    try {
      await DihService.deleteAttachment(fileId);
    } catch (error) {
      console.warn('删除待发送附件失败', error);
    }
  };

  const reparsePendingAttachment = async (attachment: ChatAttachment) => {
    const fileId = attachment.file_id || attachment.fileId;
    if (!fileId) return;
    try {
      const status = await DihService.reparseAttachment(fileId);
      updateAttachment(fileId, status);
      pollAttachmentStatus(status);
    } catch (error) {
      console.error('重新解析附件失败', error);
      ElMessage.error('重新解析失败，请重试');
    }
  };

  const validateFilesBeforeUpload = (files: File[]) => {
    const available = Math.max(MAX_FILES_PER_PICK - pendingAttachments.value.length, 0);
    const selectedFiles = files.slice(0, available);
    if (files.length > available) {
      ElMessage.warning(`一次最多保留 ${MAX_FILES_PER_PICK} 个附件，已自动忽略多余文件`);
    }
    const validFiles = selectedFiles.filter(file => file.size <= MAX_UPLOAD_BYTES);
    const oversizedFiles = selectedFiles.filter(file => file.size > MAX_UPLOAD_BYTES);
    if (oversizedFiles.length) {
      ElMessage.error(`已忽略 ${oversizedFiles.length} 个超过 100MB 的附件`);
    }
    return validFiles;
  };

  const recalculateUploadProgress = () => {
    if (!progressByFile.size) {
      uploadProgress.value = 0;
      return;
    }
    const values = Array.from(progressByFile.values());
    uploadProgress.value = Math.round(values.reduce((total, value) => total + value, 0) / values.length);
  };

  const uploadFilesWithConcurrency = async (files: File[]) => {
    const attachments: ChatAttachment[] = [];
    progressByFile.clear();
    files.forEach(file => progressByFile.set(file, 0));
    for (let index = 0; index < files.length; index += UPLOAD_CONCURRENCY) {
      const batch = files.slice(index, index + UPLOAD_CONCURRENCY);
      const batchAttachments = await Promise.all(batch.map(file => DihService.uploadFile(
        file,
        undefined,
        percent => {
          progressByFile.set(file, percent);
          recalculateUploadProgress();
        },
      )));
      attachments.push(...batchAttachments);
    }
    return attachments;
  };

  const uploadFile = () => {
    if (isUploadingAttachment.value) return;
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.multiple = true;
    fileInput.style.display = 'none';
    fileInput.onchange = async event => {
      const target = event.target as HTMLInputElement;
      if (target.files && target.files.length > 0) {
        const files = validateFilesBeforeUpload(Array.from(target.files));
        if (!files.length) {
          fileInput.remove();
          return;
        }
        isUploadingAttachment.value = true;
        uploadProgress.value = 0;
        try {
          const attachments = await uploadFilesWithConcurrency(files);
          pendingAttachments.value.push(...attachments);
          attachments.forEach(pollAttachmentStatus);
          ElMessage.success(files.length > 1 ? `已添加 ${files.length} 个附件` : `已添加附件「${files[0].name}」`);
        } catch (error) {
          console.error('文件上传失败', error);
          ElMessage.error('文件上传失败，请重试');
        } finally {
          isUploadingAttachment.value = false;
          uploadProgress.value = 0;
          progressByFile.clear();
          fileInput.remove();
        }
      } else {
        fileInput.remove();
      }
    };
    document.body.appendChild(fileInput);
    fileInput.click();
  };

  const hasUnreadyAttachments = computed(() => pendingAttachments.value.some(attachment => {
    const status = attachmentStatus(attachment);
    return !status || status === 'pending' || status === 'processing' || status === 'failed';
  }));

  const hasWarningAttachments = computed(() => pendingAttachments.value.some(attachment => {
    const status = attachmentStatus(attachment);
    return status === 'partial' || status === 'unsupported';
  }));

  const isParsingAttachment = computed(() => pendingAttachments.value.some(attachment => {
    const status = attachmentStatus(attachment);
    return status === 'pending' || status === 'processing';
  }));

  onUnmounted(() => {
    pollingTimers.forEach(timer => clearTimeout(timer));
    pollingTimers.clear();
  });

  return {
    pendingAttachments,
    isUploadingAttachment,
    uploadProgress,
    hasUnreadyAttachments,
    hasWarningAttachments,
    isParsingAttachment,
    attachmentFileId,
    attachmentFileName,
    attachmentStatus,
    attachmentStatusText,
    attachmentProgress,
    attachmentWarning,
    attachmentTypeLabel,
    formatFileSize,
    isImageAttachment,
    attachmentPreviewUrl,
    openAttachmentPreview,
    removePendingAttachment,
    reparsePendingAttachment,
    validateFilesBeforeUpload,
    uploadFilesWithConcurrency,
    uploadFile,
  };
};
