import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-messager' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

// @ts-expect-error
const isTurboModuleEnabled = global.__turboModuleProxy != null;
const MessagerModule = isTurboModuleEnabled
  ? require('./NativeMessager').default
  : NativeModules.Messager;

const Messager = MessagerModule
  ? MessagerModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export const messageEvents = new NativeEventEmitter(Messager);

export function multiply(a: number, b: number): Promise<number> {
  return Messager.multiply(a, b);
}

// Message type definition (moved up for reuse)
export type Message = {
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
  senderPhotoUri: string | null;
  senderName: string | null;
  senderPhoneNumber: string;
  attachments?: Array<{
    partId: string;
    contentType: string;
    text: string | null;
    filePath: string;
    width?: number;
    height?: number;
  }>;
};

export type Messages = {
  body: string;
  type: number;
  date: number;
  read: boolean;
  threadId: number;
  isMMS: boolean;
  senderPhoneNumber: string;
  senderName: string;
  senderPhotoUri: string;
  subscriptionId: number;
  isScheduled: boolean;
};

// New Conversation type definition to match getConversationsByPhoneNumber
export type Conversation = {
  threadId: number;
  snippet: string;
  timestamp: number;
  read: boolean;
  senderPhoneNumber: string;
  senderName: string;
  senderPhotoUri: string;
  isScheduled: boolean;
  usesCustomTitle: boolean;
  isArchived: boolean;
  unreadCount: number;
};

export function getAllMessages(
  threadId: number | null,
  limit: number = 20,
  offset: number = 0
): Promise<Messages[]> {
  return Messager.getAllMessages(threadId, limit, offset);
}

export function setDefaultMessage(): Promise<string> {
  return Messager.requestMessageRole();
}

export function sendSmsMessage(
  text: string,
  addresses: string[],
  subId: number,
  requireDeliveryReport: boolean
): Promise<Message[]> {
  return Messager.sendSmsMessage(text, addresses, subId, requireDeliveryReport);
}

// ✅ New method for fetching conversations
export function getConversationList(
  threadId: number | null,
  limit: number = 20,
  offset: number = 0
): Promise<Message[]> {
  return Messager.getConversationList(threadId, limit, offset);
}

// New getMessagesList function
export function getMessagesList(
  threadId: number,
  getImageResolutions: boolean = false,
  dateFrom: number = -1,
  includeScheduledMessages: boolean = true,
  limit: number = 20,
  offset: number = 0
): Promise<Message[]> {
  return Messager.getMessagesList(
    threadId,
    getImageResolutions,
    dateFrom,
    includeScheduledMessages,
    limit,
    offset
  );
}

// New method to mark a conversation as read
export function markConversationAsRead(threadId: number): Promise<string> {
  return Messager.markConversationAsRead(threadId);
}

// New method to mark a specific message as read
export function markMessageAsRead(
  threadId: number,
  messageId: number,
  isMMS: boolean = false
): Promise<string> {
  return Messager.markMessageAsRead(threadId, messageId, isMMS);
}

// New method to delete a conversation
export function deleteConversation(threadId: number): Promise<string> {
  return Messager.deleteConversation(threadId);
}

// New method to delete a specific message
export function deleteMessage(
  threadId: number,
  messageId: number,
  isMMS: boolean = false
): Promise<string> {
  return Messager.deleteMessage(threadId, messageId, isMMS);
}

export function getUnreadMessagesCount(threadId?: number): Promise<number> {
  return Messager.getUnreadMessagesCount(threadId);
}

export function markAllConversationsAsRead(): Promise<string> {
  return Messager.markAllConversationsAsRead();
}

// ✅ New method for fetching conversations by phone number
export function getConversationsByPhoneNumber(
  phoneNumber: string,
  limit: number = 20,
  offset: number = 0
): Promise<Conversation[]> {
  return Messager.getConversationsByPhoneNumber(phoneNumber, limit, offset);
}
