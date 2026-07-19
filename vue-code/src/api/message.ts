import { request } from '@/utils/request';
import type { ApiResponse } from '@/types';

// 消息信息
export interface ChatMessage {
  id: number;
  xianyuAccountId: number;
  lwp: string;
  pnmId: string;
  sid: string;
  contentType: number;
  msgContent: string;
  senderUserName: string;
  senderUserId: string;
  senderAppV: string;
  senderOsType: string;
  reminderUrl: string;
  xyGoodsId: string;
  completeMsg: string;
  messageTime: string | number;
  createTime: string;
  isNew?: boolean;
}

// 消息列表响应
export interface MessageListResponse {
  list: ChatMessage[];
  totalCount: number;
  totalPage: number;
  pageNum: number;
  pageSize: number;
}

export interface ChatSession {
  sid: string;
  buyerUserName: string;
  buyerUserId: string;
  buyerAvatarUrl?: string;
  buyerAvatar?: string;
  goodsTitle?: string;
  xyGoodsId?: string;
  lastMessage?: string;
  lastMessageTime?: string | number;
  lastContentType?: number;
  takeoverEndTime?: string;
  unreadCount?: number;
  buyerTags?: string;
}

export interface ChatUserProfile {
  avatarUrl?: string;
  nick?: string;
}

export interface ChatAvatarQueryResponse {
  accountAvatarUrl?: string;
  buyerProfiles: Record<string, ChatUserProfile>;
}

// 获取消息列表
export function getMessageList(data: {
  xianyuAccountId: number;
  xyGoodsId?: string;
  pageNum?: number;
  pageSize?: number;
  filterCurrentAccount?: boolean; // 过滤当前账号消息
}) {
  return request<MessageListResponse>({
    url: '/msg/list',
    method: 'POST',
    data
  });
}

// 根据会话ID获取上下文消息
export function getContextMessages(data: {
  sid: string;
  xianyuAccountId?: number;
  limit?: number;
  offset?: number;
}) {
  return request<ChatMessage[]>({
    url: '/msg/context',
    method: 'POST',
    data: {
      sid: data.sid,
      xianyuAccountId: data.xianyuAccountId,
      limit: data.limit || 20,
      offset: data.offset || 0
    }
  });
}

export function getChatSessions(xianyuAccountId: number, limit = 80) {
  return request<ChatSession[]>({
    url: '/msg/sessions',
    method: 'POST',
    data: { xianyuAccountId, limit }
  });
}

export function queryChatAvatars(data: {
  xianyuAccountId: number;
  includeOwner?: boolean;
  queries: Array<{ buyerUserId: string; sid: string }>;
}) {
  return request<ChatAvatarQueryResponse>({
    url: '/msg/avatars',
    method: 'POST',
    data
  });
}

export function markChatSessionRead(data: { xianyuAccountId: number; sid: string }) {
  return request<string>({
    url: '/msg/session/read',
    method: 'POST',
    data
  });
}

export function addChatBuyerTag(data: { xianyuAccountId: number; buyerUserId: string; tagName: string }) {
  return request<string>({
    url: '/msg/buyer-tags/add',
    method: 'POST',
    data
  });
}

export function removeChatBuyerTag(data: { xianyuAccountId: number; buyerUserId: string; tagName: string }) {
  return request<string>({
    url: '/msg/buyer-tags/remove',
    method: 'POST',
    data
  });
}

export function updateChatTakeover(data: {
  xianyuAccountId: number;
  sid: string;
  xyGoodsId?: string;
  enabled: boolean;
  durationMinutes?: number;
}) {
  return request<string>({
    url: '/msg/takeover',
    method: 'POST',
    data
  });
}

// 发送消息
export function sendMessage(data: {
  xianyuAccountId: number;
  cid: string;
  toId: string;
  text: string;
  xyGoodsId?: string;
}) {
  return request<string>({
    url: '/websocket/sendMessage',
    method: 'POST',
    data
  });
}
