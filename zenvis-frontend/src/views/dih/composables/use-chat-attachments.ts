import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { DihService } from '@/service/api';
import type { ChatAttachment } from '@/types/type-dih';
import { withBaseUrl } from '@/utils/url';

const MAX_UPLOAD_BYTES = 30 * 1024 * 1024;
const MAX_FILES_PER_PICK = 10;
const UPLOAD_CONCURRENCY = 3;

export const useChatAttachments = () => {
  const pendingAttachments = ref<ChatAttachment[]>([]);
  const isUploadingAttachment = ref(false);

  const attachmentFileId = (attachment: ChatAttachment) => {
    return attachment.file_id || attachment.fileId || attachment.file_name || attachment.fileName || 'attachment';
  };

  const attachmentFileName = (attachment: ChatAttachment) => {
    return attachment.file_name || attachment.fileName || '未命名文件';
  };

  const formatFileSize = (size?: number) => {
    if (!size || size <= 0) return '';
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
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

  const openAttachmentPreview = (attachment: ChatAttachment) => {
    const url = attachmentPreviewUrl(attachment);
    if (url) {
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  };

  const removePendingAttachment = (index: number) => {
    pendingAttachments.value.splice(index, 1);
  };

  const validateFilesBeforeUpload = (files: File[]) => {
    const selectedFiles = files.slice(0, MAX_FILES_PER_PICK);
    if (files.length > MAX_FILES_PER_PICK) {
      ElMessage.warning(`单次最多选择 ${MAX_FILES_PER_PICK} 个附件，已自动忽略多余文件`);
    }
    const validFiles = selectedFiles.filter(file => file.size <= MAX_UPLOAD_BYTES);
    const oversizedFiles = selectedFiles.filter(file => file.size > MAX_UPLOAD_BYTES);
    if (oversizedFiles.length) {
      ElMessage.error(`已忽略 ${oversizedFiles.length} 个超过 30MB 的附件`);
    }
    return validFiles;
  };

  const uploadFilesWithConcurrency = async (files: File[]) => {
    const attachments: ChatAttachment[] = [];
    for (let index = 0; index < files.length; index += UPLOAD_CONCURRENCY) {
      const batch = files.slice(index, index + UPLOAD_CONCURRENCY);
      const batchAttachments = await Promise.all(batch.map(file => DihService.uploadFile(file)));
      attachments.push(...batchAttachments);
    }
    return attachments;
  };

  const uploadFile = () => {
    if (isUploadingAttachment.value) {
      return;
    }
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.multiple = true;
    fileInput.style.display = 'none';
    fileInput.onchange = async event => {
      const target = event.target as HTMLInputElement;
      if (target.files && target.files.length > 0) {
        const files = validateFilesBeforeUpload(Array.from(target.files));
        if (!files.length) {
          if (fileInput.parentNode) {
            document.body.removeChild(fileInput);
          }
          return;
        }
        isUploadingAttachment.value = true;
        try {
          const attachments = await uploadFilesWithConcurrency(files);
          pendingAttachments.value.push(...attachments);
          ElMessage.success(files.length > 1 ? `已添加 ${files.length} 个附件` : `已添加附件「${files[0].name}」`);
        } catch (error) {
          console.error('文件上传失败', error);
          ElMessage.error('文件上传失败，请重试');
        } finally {
          isUploadingAttachment.value = false;
          if (fileInput.parentNode) {
            document.body.removeChild(fileInput);
          }
        }
      } else if (fileInput.parentNode) {
        document.body.removeChild(fileInput);
      }
    };
    document.body.appendChild(fileInput);
    fileInput.click();
  };

  return {
    pendingAttachments,
    isUploadingAttachment,
    attachmentFileId,
    attachmentFileName,
    formatFileSize,
    isImageAttachment,
    attachmentPreviewUrl,
    openAttachmentPreview,
    removePendingAttachment,
    validateFilesBeforeUpload,
    uploadFilesWithConcurrency,
    uploadFile,
  };
};
