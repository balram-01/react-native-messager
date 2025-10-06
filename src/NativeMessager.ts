import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  multiply(a: number, b: number): Promise<number>;

  requestMessageRole(): Promise<string>;

  getAllMessages(
    threadId: number | null,
    limit: number,
    offset: number
  ): Promise<string>;

  getConversationList(
    threadId: number | null,
    limit: number,
    offset: number
  ): Promise<
    Array<{
      threadId: number;
      snippet: string;
      date: number;
      read: boolean;
      isScheduled: boolean;
      usesCustomTitle: boolean;
      isArchived: boolean;
      senderName: string;
      senderPhoto: string;
      unreadCount: number;
      phoneNumber: string;
    }>
  >;

  getConversationsByPhoneNumber(
    phoneNumber: string,
    limit: number | null,
    offset: number | null
  ): Promise<
    Array<{
      threadId: number;
      snippet: string;
      date: number;
      read: boolean;
      isScheduled: boolean;
      usesCustomTitle: boolean;
      isArchived: boolean;
      senderName: string;
      senderPhoto: string;
      unreadCount: number;
      phoneNumber: string;
    }>
  >;

  getMessagesList(
    threadId: number,
    getImageResolutions: boolean,
    dateFrom: number,
    includeScheduledMessages: boolean,
    limit: number,
    offset: number
  ): Promise<
    Array<{
      id: number;
      body: string;
      type: number;
      status: number;
      date: number;
      read: boolean;
      threadId: number;
      isMMS: boolean;
      subscriptionId: number;
      isScheduled: boolean;
      senderPhotoUri: string;
      senderName: string;
      senderPhoneNumber: string;
      attachments?: Array<{
        partId: string;
        contentType: string;
        text: string;
        filePath: string;
        width?: number;
        height?: number;
      }>;
    }>
  >;

  markConversationAsRead(threadId: number): Promise<string>;

  markMessageAsRead(
    threadId: number,
    messageId: number,
    isMMS: boolean
  ): Promise<string>;

  deleteConversation(threadId: number): Promise<string>;

  deleteMessage(
    threadId: number,
    messageId: number,
    isMMS: boolean
  ): Promise<string>;

  sendSmsMessage(
    text: string,
    addresses: string[],
    subId: number,
    requireDeliveryReport: boolean
  ): Promise<
    Array<{
      id: number;
      body: string;
      type: number;
      status: number;
      date: number;
      read: boolean;
      threadId: number;
      isMMS: boolean;
      subscriptionId: number;
      isScheduled: boolean;
      senderPhotoUri: string;
      senderName: string;
      senderPhoneNumber: string;
    }>
  >;

  getUnreadMessagesCount(threadId: number | null): Promise<number>;

  markAllConversationsAsRead(): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Messager');
