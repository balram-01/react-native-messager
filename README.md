<div align="center">

<br />

<img src="https://capsule-render.vercel.app/api?type=waving&color=6C63FF&height=120&section=header&text=react-native-messager&fontSize=32&fontColor=ffffff&fontAlignY=38&desc=Powerful%20%C2%B7%20Native%20SMS%20Management%20%C2%B7%20Zero%20Hassle&descAlignY=60&descSize=14" width="100%" />

<br />

[![npm version](https://img.shields.io/npm/v/react-native-messager?color=6C63FF&style=for-the-badge&logo=npm&logoColor=white)](https://www.npmjs.com/package/react-native-messager)
[![npm downloads](https://img.shields.io/npm/dm/react-native-messager?color=10B981&style=for-the-badge&logo=npm&logoColor=white)](https://www.npmjs.com/package/react-native-messager)
[![TypeScript](https://img.shields.io/badge/TypeScript-100%25-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-lightgrey?style=for-the-badge)](#)

<br />

> **The ultimate React Native library to manage, read, and send SMS messages seamlessly on Android.**

<br />

<div align="center">
  <img src="https://github.com/balram-01/react-native-messager/raw/main/assets/demo.gif" width="250" alt="React Native Messager Demo" />
</div>

<br /><br />

</div>

---

## ✦ Why this library?

Reading and managing SMS on Android is a notoriously complex process, especially when dealing with threading, permissions, and the default messaging role. `react-native-messager` simplifies it all.

| | **react-native-messager** |
|---|:---:|
| Full SMS Read/Write access | ✅ |
| Native Default SMS app request | ✅ |
| 100% TypeScript | ✅ |
| Supports TurboModules (New Arch) | ✅ |
| Fetch specific threads & conversations | ✅ |
| Mark as read & delete functionalities | ✅ |

---

## 📦 Installation

```bash
# npm
npm install react-native-messager

# yarn
yarn add react-native-messager
```

### Android Setup

Add the required permissions to your `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions required for reading and sending SMS -->
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <!-- Needed for foreground service / notifications if applicable -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- ... -->
```

---

## ⚡ Quick Start

```tsx
import { useEffect, useState } from 'react';
import { Button, View, Text } from 'react-native';
import {
  setDefaultMessage,
  getConversationList,
  Conversation
} from 'react-native-messager';

export function App() {
  const [conversations, setConversations] = useState<Conversation[]>([]);

  useEffect(() => {
    // 1. Request to be the Default SMS App
    setDefaultMessage()
      .then(() => {
        // 2. Fetch recent conversations
        return getConversationList(null, 20, 0);
      })
      .then(setConversations)
      .catch(console.error);
  }, []);

  return (
    <View>
      {conversations.map(conv => (
        <Text key={conv.threadId}>{conv.senderName || conv.phoneNumber}: {conv.snippet}</Text>
      ))}
    </View>
  );
}
```

---

## 📖 Features & Usage

### 1. Requesting Default Role

Before deleting or modifying messages, your app often needs to be the default messaging app on modern Android versions.

```tsx
import { setDefaultMessage } from 'react-native-messager';

await setDefaultMessage(); // Prompts the native Android OS dialog
```

### 2. Fetching Conversations

```tsx
import { getConversationList, getConversationsByPhoneNumber } from 'react-native-messager';

// Get paginated list of all threads
const threads = await getConversationList(null, 20, 0);

// Look up threads for a specific phone number
const specificThreads = await getConversationsByPhoneNumber('+1234567890', 20, 0);
```

### 3. Reading Messages

```tsx
import { getMessagesList, getAllMessages } from 'react-native-messager';

// Fetch messages inside a specific thread
const threadId = 12;
const messages = await getMessagesList(threadId, false, -1, true, 20, 0);

// Get a raw dump of all messages (advanced)
const everything = await getAllMessages(null, 50, 0);
```

### 4. Sending Messages

```tsx
import { sendSmsMessage } from 'react-native-messager';

const text = "Hello from React Native!";
const recipients = ["+1234567890"];

await sendSmsMessage(text, recipients, -1, false);
```

### 5. Managing Read Status

```tsx
import { markConversationAsRead, markMessageAsRead, markAllConversationsAsRead } from 'react-native-messager';

// Mark a whole thread as read
await markConversationAsRead(threadId);

// Mark a specific message as read
await markMessageAsRead(threadId, messageId, false);

// Mark everything read
await markAllConversationsAsRead();
```

### 6. Deleting Messages

```tsx
import { deleteConversation, deleteMessage } from 'react-native-messager';

// Delete a whole thread
await deleteConversation(threadId);

// Delete a specific message
await deleteMessage(threadId, messageId, false);
```

---

## 📋 API Reference

### Exported Methods

| Method | Returns | Description |
|---|---|---|
| `setDefaultMessage()` | `Promise<string>` | Requests the OS default messaging role. |
| `getConversationList(threadId, limit, offset)` | `Promise<Conversation[]>` | Returns recent threads/conversations. |
| `getConversationsByPhoneNumber(phone, limit, offset)` | `Promise<Conversation[]>` | Search threads by phone number. |
| `getMessagesList(threadId, getImages, dateFrom, inclScheduled, limit, offset)` | `Promise<Message[]>` | Get messages for a specific thread ID. |
| `getAllMessages(threadId, limit, offset)` | `Promise<any>` | Fetch all raw messages across threads. |
| `getUnreadMessagesCount(threadId?)` | `Promise<number>` | Count of unread messages (globally or per thread). |
| `sendSmsMessage(text, addresses, subId, reqDelivery)` | `Promise<Message[]>` | Send an SMS to one or more recipients. |
| `markConversationAsRead(threadId)` | `Promise<string>` | Marks a thread as read. |
| `markMessageAsRead(threadId, messageId, isMms)` | `Promise<string>` | Marks a specific message as read. |
| `markAllConversationsAsRead()` | `Promise<string>` | Global read status wipe. |
| `deleteConversation(threadId)` | `Promise<string>` | Deletes an entire thread. |
| `deleteMessage(threadId, messageId, isMms)` | `Promise<string>` | Deletes a single message. |

---

### Types

#### `Conversation`
```ts
type Conversation = {
  threadId: number;
  snippet: string;
  date: number;
  read: boolean;
  phoneNumber: string;
  senderName: string;
  senderPhoto: string;
  isScheduled: boolean;
  usesCustomTitle: boolean;
  isArchived: boolean;
  unreadCount: number;
};
```

#### `Message`
```ts
type Message = {
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
```

---

## 🤝 Contributing

Contributions, bug reports, and feature requests are welcome!

```bash
git clone https://github.com/balram-01/react-native-messager.git
cd react-native-messager
yarn install

# Run the example app
cd example && yarn install
yarn example android   # Android emulator
```

Please read [CONTRIBUTING.md](./CONTRIBUTING.md) before opening a pull request.

---

## 📄 License

MIT © [Balram](https://github.com/balram-01)

<br />

<div align="center">

If this library helped you manage SMS effectively, consider giving it a ⭐ on GitHub!

<br />

<img src="https://capsule-render.vercel.app/api?type=waving&color=6C63FF&height=80&section=footer" width="100%" />

</div>
