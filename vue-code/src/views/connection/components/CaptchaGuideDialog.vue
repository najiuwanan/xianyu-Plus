<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue';
import {
  closeCaptchaSession,
  refreshCaptchaPreview,
  replayCaptchaDrag,
  startCaptchaSession,
  type CaptchaDragPoint
} from '@/api/websocket';
import { showError, showSuccess } from '@/utils';

interface Props {
  modelValue: boolean;
  accountId: number;
  captchaUrl: string;
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void;
  (e: 'success'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const imageRef = ref<HTMLImageElement | null>(null);
const sessionId = ref('');
const screenshot = ref('');
const statusText = ref('正在加载验证页面...');
const loading = ref(false);
const submitting = ref(false);
const previewReady = ref(false);
const previewRefreshing = ref(false);
const dragFeedback = ref({ visible: false, left: 0, top: 0, distance: 0 });
let dragging = false;
let dragPoints: CaptchaDragPoint[] = [];
let lastPointTime = 0;
let requestSequence = 0;
let previewTimer: ReturnType<typeof setTimeout> | undefined;
let dragStartClientX = 0;
const MIN_HORIZONTAL_DRAG_DISTANCE = 36;
const AUTO_PREVIEW_REFRESH_COUNT = 2;

const getErrorMessage = (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback;

const clearPreviewTimer = () => {
  if (previewTimer) {
    clearTimeout(previewTimer);
    previewTimer = undefined;
  }
};

const resetDragFeedback = () => {
  dragFeedback.value = { visible: false, left: 0, top: 0, distance: 0 };
};

const releaseSession = () => {
  const currentSessionId = sessionId.value;
  sessionId.value = '';
  if (currentSessionId) {
    closeCaptchaSession(props.accountId, currentSessionId).catch(() => undefined);
  }
};

const loadCaptchaSession = async () => {
  const currentSequence = ++requestSequence;
  clearPreviewTimer();
  releaseSession();
  screenshot.value = '';
  previewReady.value = false;
  statusText.value = '正在加载验证页面...';
  loading.value = true;
  try {
    const response = await startCaptchaSession(props.accountId, props.captchaUrl);
    if (currentSequence !== requestSequence || !props.modelValue) {
      if (response.data?.sessionId) {
        closeCaptchaSession(props.accountId, response.data.sessionId).catch(() => undefined);
      }
      return;
    }
    if ((response.code === 0 || response.code === 200) && response.data) {
      sessionId.value = response.data.sessionId;
      screenshot.value = response.data.screenshot || '';
      statusText.value = response.data.message || '验证页面正在加载，请稍候，画面会自动刷新';
      schedulePreviewRefresh(currentSequence);
    } else {
      throw new Error(response.msg || '加载验证页面失败');
    }
  } catch (error: unknown) {
    statusText.value = getErrorMessage(error, '加载验证页面失败');
    showError(statusText.value);
  } finally {
    if (currentSequence === requestSequence) {
      loading.value = false;
    }
  }
};

const refreshPreview = async (automatic = false) => {
  if (!sessionId.value || submitting.value || dragging) return;
  const activeSessionId = sessionId.value;
  previewRefreshing.value = true;
  if (automatic) {
    statusText.value = '正在刷新验证画面，请稍候...';
  }
  try {
    const response = await refreshCaptchaPreview(props.accountId, sessionId.value);
    if ((response.code === 0 || response.code === 200) && response.data
      && activeSessionId === sessionId.value && props.modelValue) {
      screenshot.value = response.data.screenshot || screenshot.value;
      previewReady.value = true;
      statusText.value = response.data.message || '请确认滑块出现后按住向右拖动，松开后提交验证';
    } else {
      throw new Error(response.msg || '刷新验证画面失败');
    }
  } catch (error: unknown) {
    statusText.value = getErrorMessage(error, '刷新验证画面失败');
    if (!automatic) showError(statusText.value);
  } finally {
    previewRefreshing.value = false;
  }
};

const schedulePreviewRefresh = (currentSequence: number, attempt = 0) => {
  if (attempt >= AUTO_PREVIEW_REFRESH_COUNT) return;
  previewTimer = setTimeout(async () => {
    if (currentSequence !== requestSequence || !props.modelValue || !sessionId.value || dragging || submitting.value) return;
    await refreshPreview(true);
    if (currentSequence === requestSequence && props.modelValue && !dragging && !submitting.value) {
      schedulePreviewRefresh(currentSequence, attempt + 1);
    }
  }, 900 + attempt * 700);
};

const handlePreviewRefresh = () => {
  if (sessionId.value) {
    clearPreviewTimer();
    void refreshPreview();
    return;
  }
  void loadCaptchaSession();
};

const getDragPoint = (event: PointerEvent): CaptchaDragPoint | null => {
  const image = imageRef.value;
  if (!image) return null;
  const rect = image.getBoundingClientRect();
  if (!rect.width || !rect.height || !image.naturalWidth || !image.naturalHeight) return null;

  const now = performance.now();
  const point = {
    x: Math.max(0, Math.min(image.naturalWidth, (event.clientX - rect.left) * image.naturalWidth / rect.width)),
    y: Math.max(0, Math.min(image.naturalHeight, (event.clientY - rect.top) * image.naturalHeight / rect.height)),
    delayMs: lastPointTime ? Math.max(0, Math.min(40, Math.round(now - lastPointTime))) : 0
  };
  lastPointTime = now;
  return point;
};

const updateDragFeedback = (event: PointerEvent) => {
  const image = imageRef.value;
  if (!image) return;
  const rect = image.getBoundingClientRect();
  dragFeedback.value = {
    visible: true,
    left: Math.max(0, Math.min(rect.width, event.clientX - rect.left)),
    top: Math.max(0, Math.min(rect.height, event.clientY - rect.top)),
    distance: Math.max(0, Math.round(event.clientX - dragStartClientX))
  };
};

const handlePointerDown = (event: PointerEvent) => {
  if (submitting.value || !sessionId.value) return;
  if (!previewReady.value) {
    statusText.value = '验证页面还在加载，请等待画面自动刷新后再拖动';
    return;
  }
  const point = getDragPoint(event);
  if (!point) return;

  dragging = true;
  dragStartClientX = event.clientX;
  dragPoints = [point];
  updateDragFeedback(event);
  statusText.value = '正在记录拖动轨迹，松开后会提交到服务器验证';
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
};

const handlePointerMove = (event: PointerEvent) => {
  if (!dragging || dragPoints.length >= 159) return;
  const point = getDragPoint(event);
  const previous = dragPoints[dragPoints.length - 1];
  if (!point || !previous || (Math.abs(point.x - previous.x) < 1 && Math.abs(point.y - previous.y) < 1)) return;
  dragPoints.push(point);
  updateDragFeedback(event);
};

const handlePointerUp = async (event: PointerEvent) => {
  if (!dragging) return;
  dragging = false;
  updateDragFeedback(event);
  const finalPoint = getDragPoint(event);
  if (finalPoint) {
    if (dragPoints.length >= 160) {
      dragPoints[159] = finalPoint;
    } else {
      dragPoints.push(finalPoint);
    }
  }
  if (dragPoints.length < 2 || !sessionId.value) return;

  const firstPoint = dragPoints[0];
  const lastPoint = dragPoints[dragPoints.length - 1];
  if (!firstPoint || !lastPoint || lastPoint.x - firstPoint.x < MIN_HORIZONTAL_DRAG_DISTANCE) {
    statusText.value = `已记录拖动 ${dragFeedback.value.distance}px，请按住滑块向右拖动一段距离后再松开`;
    dragPoints = [];
    resetDragFeedback();
    return;
  }

  submitting.value = true;
  statusText.value = '正在提交验证结果...';
  try {
    const response = await replayCaptchaDrag(props.accountId, sessionId.value, dragPoints);
    if ((response.code === 0 || response.code === 200) && response.data) {
      statusText.value = response.data.message;
      if (response.data.success) {
        showSuccess(response.data.message);
        sessionId.value = '';
        emit('success');
        emit('update:modelValue', false);
        return;
      }
      screenshot.value = response.data.screenshot || screenshot.value;
    } else {
      throw new Error(response.msg || '滑块验证失败');
    }
  } catch (error: unknown) {
    statusText.value = getErrorMessage(error, '滑块验证失败');
    showError(statusText.value);
  } finally {
    submitting.value = false;
    dragPoints = [];
    resetDragFeedback();
  }
};

const handlePointerCancel = () => {
  dragging = false;
  dragPoints = [];
  resetDragFeedback();
};

const handleClose = () => {
  requestSequence++;
  clearPreviewTimer();
  resetDragFeedback();
  releaseSession();
  emit('update:modelValue', false);
};

watch(() => props.modelValue, (visible) => {
  if (visible) {
    loadCaptchaSession();
  } else {
    requestSequence++;
    clearPreviewTimer();
    resetDragFeedback();
    releaseSession();
  }
});

onUnmounted(() => {
  requestSequence++;
  clearPreviewTimer();
  releaseSession();
});
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-overlay" @click.self="handleClose">
        <div class="modal-container">
          <div class="modal-header">
            <div>
              <h2 class="modal-title">滑块验证</h2>
              <p class="modal-subtitle">验证在服务器浏览器中完成；画面会自动刷新，松开鼠标后才会同步拖动并更新 Cookie</p>
            </div>
            <button class="modal-close" type="button" aria-label="关闭" @click="handleClose">×</button>
          </div>

          <div class="modal-body">
            <div v-if="loading" class="captcha-state">正在加载验证页面...</div>
            <div
              v-else-if="screenshot"
              class="captcha-viewer"
              :class="{ 'is-submitting': submitting, 'is-loading-preview': !previewReady }"
            >
              <img
                ref="imageRef"
                class="captcha-image"
                :src="screenshot"
                alt="滑块验证页面"
                draggable="false"
                @pointerdown.prevent="handlePointerDown"
                @pointermove.prevent="handlePointerMove"
                @pointerup.prevent="handlePointerUp"
                @pointercancel="handlePointerCancel"
              >
              <div v-if="!previewReady" class="captcha-loading-mask">
                <span class="captcha-loading-spinner" />
                <span>验证组件正在打开，画面准备好后即可拖动</span>
              </div>
              <div
                v-if="dragFeedback.visible"
                class="captcha-drag-indicator"
                :style="{ left: `${dragFeedback.left}px`, top: `${dragFeedback.top}px` }"
              >
                <span class="captcha-drag-dot" />
                <span class="captcha-drag-distance">已拖动 {{ dragFeedback.distance }} px</span>
              </div>
            </div>
            <div v-else class="captcha-state captcha-state--error">{{ statusText }}</div>
            <p v-if="screenshot" class="captcha-status">{{ statusText }}</p>
          </div>

          <div class="modal-footer">
            <button class="btn btn-secondary" type="button" @click="handleClose">取消</button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="loading || submitting || previewRefreshing"
              @click="handlePreviewRefresh"
            >
              {{ sessionId ? '刷新画面' : '重新加载' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.42);
}

.modal-container {
  width: min(920px, 96vw);
  max-height: 92vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.18);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  border-bottom: 1px solid #eef0f3;
}

.modal-title {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.modal-subtitle {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.5;
}

.modal-close {
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #6b7280;
  font-size: 20px;
  cursor: pointer;
}

.modal-close:hover {
  background: #f3f4f6;
  color: #111827;
}

.modal-body {
  min-height: 0;
  padding: 20px;
  overflow: auto;
}

.captcha-viewer {
  position: relative;
  overflow: hidden;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #f7f8fa;
  line-height: 0;
}

.captcha-viewer.is-submitting {
  opacity: 0.65;
  pointer-events: none;
}

.captcha-viewer.is-loading-preview {
  cursor: wait;
}

.captcha-image {
  display: block;
  width: 100%;
  height: auto;
  user-select: none;
  touch-action: none;
  cursor: grab;
}

.captcha-image:active {
  cursor: grabbing;
}

.captcha-loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.72);
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
  pointer-events: none;
}

.captcha-loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #dbeafe;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: captcha-spin 0.8s linear infinite;
}

.captcha-drag-indicator {
  position: absolute;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.82);
  color: #ffffff;
  font-size: 12px;
  line-height: 1;
  pointer-events: none;
  transform: translate(8px, calc(-100% - 8px));
}

.captcha-drag-dot {
  width: 8px;
  height: 8px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.26);
}

.captcha-drag-distance {
  white-space: nowrap;
}

.captcha-state {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 14px;
}

.captcha-state--error {
  color: #b42318;
}

.captcha-status {
  margin: 12px 0 0;
  color: #4b5563;
  font-size: 13px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 20px;
  border-top: 1px solid #eef0f3;
}

.btn {
  height: 34px;
  padding: 0 16px;
  border: 1px solid #d1d5db;
  border-radius: 7px;
  font-size: 13px;
  cursor: pointer;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.btn-secondary {
  background: #ffffff;
  color: #374151;
}

.btn-secondary:hover:not(:disabled) {
  background: #f7f8fa;
}

@keyframes captcha-spin {
  to {
    transform: rotate(360deg);
  }
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.16s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

@media (max-width: 640px) {
  .modal-overlay {
    padding: 10px;
  }

  .modal-header,
  .modal-body,
  .modal-footer {
    padding-left: 14px;
    padding-right: 14px;
  }
}
</style>
