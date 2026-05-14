import React, { useState, useRef } from 'react';
import {
  Text,
  View,
  StyleSheet,
  ScrollView,
  TextInput,
  TouchableOpacity,
  Alert,
  Animated,
  StatusBar,
  SafeAreaView,
  Modal,
  ActivityIndicator,
  Platform,
} from 'react-native';
import {
  setDefaultMessage,
  getAllMessages,
  getConversationList,
  getConversationsByPhoneNumber,
  getMessagesList,
  markConversationAsRead,
  markMessageAsRead,
  deleteConversation,
  deleteMessage,
  sendSmsMessage,
  getUnreadMessagesCount,
  markAllConversationsAsRead,
} from 'react-native-messager';

// ─── Types ────────────────────────────────────────────────────────────────────

type Tab = 'conversations' | 'send' | 'tools';

interface ActionResult {
  name: string;
  data: any;
  timestamp: Date;
}

// ─── Color palette ───────────────────────────────────────────────────────────

const C = {
  bg: '#0F0F14',
  surface: '#17171F',
  surfaceAlt: '#1E1E28',
  border: '#2A2A38',
  accent: '#6C63FF',
  accentLight: '#8A84FF',
  accentDim: 'rgba(108,99,255,0.15)',
  green: '#34D399',
  greenDim: 'rgba(52,211,153,0.15)',
  red: '#F87171',
  redDim: 'rgba(248,113,113,0.15)',
  amber: '#FBBF24',
  amberDim: 'rgba(251,191,36,0.15)',
  textPrimary: '#F0EFFB',
  textSecondary: '#8B8AA8',
  textDim: '#4A4A62',
};

// ─── Small Components ────────────────────────────────────────────────────────

const Pill = ({
  label,
  color = C.accent,
  bg,
}: {
  label: string;
  color?: string;
  bg?: string;
}) => (
  <View
    style={[
      pillStyles.pill,
      {
        backgroundColor: bg ?? 'rgba(108,99,255,0.15)',
        borderColor: color + '40',
      },
    ]}
  >
    <Text style={[pillStyles.label, { color }]}>{label}</Text>
  </View>
);

const pillStyles = StyleSheet.create({
  pill: {
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: 999,
    borderWidth: 1,
    alignSelf: 'flex-start',
  },
  label: { fontSize: 11, fontWeight: '600', letterSpacing: 0.5 },
});

const Divider = () => (
  <View style={{ height: 1, backgroundColor: C.border, marginVertical: 6 }} />
);

// ─── StyledInput ─────────────────────────────────────────────────────────────

const StyledInput = ({
  label,
  placeholder,
  value,
  onChangeText,
  keyboardType = 'default',
  icon,
}: {
  label: string;
  placeholder: string;
  value: string;
  onChangeText: (t: string) => void;
  keyboardType?: any;
  icon: string;
}) => (
  <View style={inputStyles.wrapper}>
    <Text style={inputStyles.label}>
      {icon} {label}
    </Text>
    <TextInput
      style={inputStyles.input}
      placeholder={placeholder}
      placeholderTextColor={C.textDim}
      value={value}
      onChangeText={onChangeText}
      keyboardType={keyboardType}
      selectionColor={C.accent}
    />
  </View>
);

const inputStyles = StyleSheet.create({
  wrapper: { marginBottom: 12 },
  label: {
    color: C.textSecondary,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 6,
  },
  input: {
    backgroundColor: C.surfaceAlt,
    borderColor: C.border,
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: Platform.OS === 'ios' ? 13 : 10,
    color: C.textPrimary,
    fontSize: 15,
  },
});

// ─── Action Button ───────────────────────────────────────────────────────────

const ActionButton = ({
  title,
  subtitle,
  icon,
  onPress,
  variant = 'default',
  loading = false,
}: {
  title: string;
  subtitle?: string;
  icon: string;
  onPress: () => void;
  variant?: 'default' | 'danger' | 'success' | 'warning';
  loading?: boolean;
}) => {
  const scale = useRef(new Animated.Value(1)).current;

  const colors: Record<string, { bg: string; border: string; text: string }> = {
    default: { bg: C.accentDim, border: C.accent + '50', text: C.accentLight },
    danger: { bg: C.redDim, border: C.red + '50', text: C.red },
    success: { bg: C.greenDim, border: C.green + '50', text: C.green },
    warning: { bg: C.amberDim, border: C.amber + '50', text: C.amber },
  };
  const { bg, border, text } = colors[variant];

  const handlePress = () => {
    Animated.sequence([
      Animated.timing(scale, {
        toValue: 0.95,
        duration: 80,
        useNativeDriver: true,
      }),
      Animated.timing(scale, {
        toValue: 1,
        duration: 120,
        useNativeDriver: true,
      }),
    ]).start();
    onPress();
  };

  return (
    <Animated.View style={{ transform: [{ scale }] }}>
      <TouchableOpacity
        onPress={handlePress}
        activeOpacity={0.85}
        style={[btnStyles.btn, { backgroundColor: bg, borderColor: border }]}
      >
        <Text style={btnStyles.icon}>{icon}</Text>
        <View style={{ flex: 1 }}>
          <Text style={[btnStyles.title, { color: text }]}>{title}</Text>
          {subtitle && <Text style={btnStyles.subtitle}>{subtitle}</Text>}
        </View>
        {loading ? (
          <ActivityIndicator size="small" color={text} />
        ) : (
          <Text style={[btnStyles.arrow, { color: text }]}>›</Text>
        )}
      </TouchableOpacity>
    </Animated.View>
  );
};

const btnStyles = StyleSheet.create({
  btn: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 13,
    marginBottom: 10,
    gap: 12,
  },
  icon: { fontSize: 20 },
  title: { fontSize: 15, fontWeight: '600' },
  subtitle: { fontSize: 12, color: C.textDim, marginTop: 1 },
  arrow: { fontSize: 22, fontWeight: '300', marginLeft: 4 },
});

// ─── Result Modal ─────────────────────────────────────────────────────────────

const ResultModal = ({
  result,
  onClose,
}: {
  result: ActionResult | null;
  onClose: () => void;
}) => (
  <Modal
    visible={!!result}
    transparent
    animationType="slide"
    onRequestClose={onClose}
  >
    <View style={modalStyles.overlay}>
      <View style={modalStyles.sheet}>
        <View style={modalStyles.handle} />

        <View style={modalStyles.header}>
          <View>
            <Text style={modalStyles.title}>{result?.name}</Text>
            <Text style={modalStyles.ts}>
              {result?.timestamp.toLocaleTimeString()}
            </Text>
          </View>
          <TouchableOpacity onPress={onClose} style={modalStyles.closeBtn}>
            <Text style={modalStyles.closeText}>✕</Text>
          </TouchableOpacity>
        </View>

        <Pill label="SUCCESS" color={C.green} bg={C.greenDim} />

        <ScrollView
          style={modalStyles.codeScroll}
          showsVerticalScrollIndicator={false}
        >
          <Text style={modalStyles.code}>
            {JSON.stringify(result?.data, null, 2)}
          </Text>
        </ScrollView>

        <TouchableOpacity style={modalStyles.doneBtn} onPress={onClose}>
          <Text style={modalStyles.doneBtnText}>Done</Text>
        </TouchableOpacity>
      </View>
    </View>
  </Modal>
);

const modalStyles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: C.surface,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    maxHeight: '75%',
  },
  handle: {
    width: 40,
    height: 4,
    backgroundColor: C.border,
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 14,
  },
  title: { color: C.textPrimary, fontSize: 17, fontWeight: '700' },
  ts: { color: C.textDim, fontSize: 12, marginTop: 2 },
  closeBtn: {
    width: 32,
    height: 32,
    backgroundColor: C.surfaceAlt,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeText: { color: C.textSecondary, fontSize: 14 },
  codeScroll: {
    marginTop: 16,
    backgroundColor: C.bg,
    borderRadius: 12,
    padding: 16,
    maxHeight: 300,
  },
  code: {
    color: '#A8FF78',
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    fontSize: 12,
    lineHeight: 18,
  },
  doneBtn: {
    marginTop: 16,
    backgroundColor: C.accent,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  doneBtnText: { color: '#fff', fontWeight: '700', fontSize: 16 },
});

// ─── Tabs ─────────────────────────────────────────────────────────────────────

const TabBar = ({
  active,
  onChange,
}: {
  active: Tab;
  onChange: (t: Tab) => void;
}) => {
  const tabs: { key: Tab; icon: string; label: string }[] = [
    { key: 'conversations', icon: '💬', label: 'Convos' },
    { key: 'send', icon: '✉️', label: 'Send SMS' },
    { key: 'tools', icon: '🔧', label: 'Tools' },
  ];
  return (
    <View style={tabStyles.bar}>
      {tabs.map((t) => (
        <TouchableOpacity
          key={t.key}
          onPress={() => onChange(t.key)}
          style={[tabStyles.tab, active === t.key && tabStyles.tabActive]}
        >
          <Text style={tabStyles.tabIcon}>{t.icon}</Text>
          <Text
            style={[
              tabStyles.tabLabel,
              active === t.key && tabStyles.tabLabelActive,
            ]}
          >
            {t.label}
          </Text>
        </TouchableOpacity>
      ))}
    </View>
  );
};

const tabStyles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    backgroundColor: C.surface,
    borderTopWidth: 1,
    borderTopColor: C.border,
    paddingBottom: Platform.OS === 'ios' ? 20 : 8,
    paddingTop: 8,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 4,
    borderRadius: 8,
    gap: 2,
  },
  tabActive: {
    backgroundColor: C.accentDim,
  },
  tabIcon: { fontSize: 18 },
  tabLabel: { fontSize: 11, color: C.textDim, fontWeight: '600' },
  tabLabelActive: { color: C.accentLight },
});

// ─── Section ──────────────────────────────────────────────────────────────────

const Section = ({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) => (
  <View style={{ marginBottom: 24 }}>
    <Text style={sectionStyles.title}>{title}</Text>
    {children}
  </View>
);

const sectionStyles = StyleSheet.create({
  title: {
    color: C.textDim,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    marginBottom: 12,
  },
});

// ─── Main App ─────────────────────────────────────────────────────────────────

export default function App() {
  const [tab, setTab] = useState<Tab>('conversations');
  const [threadId, setThreadId] = useState('');
  const [messageId, setMessageId] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [smsText, setSmsText] = useState('');
  const [result, setResult] = useState<ActionResult | null>(null);
  const [loadingKey, setLoadingKey] = useState<string | null>(null);

  const run = async (name: string, fn: () => Promise<any>) => {
    setLoadingKey(name);
    try {
      const data = await fn();
      setResult({ name, data, timestamp: new Date() });
    } catch (err: any) {
      Alert.alert(`❌ ${name}`, err?.message ?? String(err));
    } finally {
      setLoadingKey(null);
    }
  };

  const needsThread = () => {
    if (!threadId) {
      Alert.alert('Missing Field', 'Thread ID is required.');
      return false;
    }
    return true;
  };
  const needsThreadAndMsg = () => {
    if (!threadId || !messageId) {
      Alert.alert('Missing Fields', 'Thread ID and Message ID are required.');
      return false;
    }
    return true;
  };
  const needsPhone = () => {
    if (!phoneNumber) {
      Alert.alert('Missing Field', 'Phone number is required.');
      return false;
    }
    return true;
  };

  const tid = () => parseInt(threadId, 10);
  const mid = () => parseInt(messageId, 10);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: C.bg }}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />

      {/* Header */}
      <View style={appStyles.header}>
        <View>
          <Text style={appStyles.appName}>Messager</Text>
          <Text style={appStyles.appSub}>react-native-messager testbed</Text>
        </View>
        <Pill label="v1.0" />
      </View>

      {/* Body */}
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={appStyles.scrollContent}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        {/* ── CONVERSATIONS TAB ── */}
        {tab === 'conversations' && (
          <>
            <Section title="Identifiers">
              <StyledInput
                label="Thread ID"
                placeholder="e.g. 1"
                value={threadId}
                onChangeText={setThreadId}
                keyboardType="numeric"
                icon="🧵"
              />
              <StyledInput
                label="Message ID"
                placeholder="e.g. 42"
                value={messageId}
                onChangeText={setMessageId}
                keyboardType="numeric"
                icon="📝"
              />
            </Section>

            <Section title="Read">
              <ActionButton
                icon="📋"
                title="Get All Messages"
                subtitle="Fetch all messages (thread optional)"
                loading={loadingKey === 'getAllMessages'}
                onPress={() =>
                  run('getAllMessages', () =>
                    getAllMessages(threadId ? tid() : null, 20, 0)
                  )
                }
              />
              <ActionButton
                icon="💬"
                title="Get Conversation List"
                subtitle="List all conversations"
                loading={loadingKey === 'getConversationList'}
                onPress={() =>
                  run('getConversationList', () =>
                    getConversationList(threadId ? tid() : null, 20, 0)
                  )
                }
              />
              <ActionButton
                icon="📨"
                title="Get Messages in Thread"
                subtitle="Requires Thread ID"
                loading={loadingKey === 'getMessagesList'}
                onPress={() => {
                  if (!needsThread()) return;
                  run('getMessagesList', () =>
                    getMessagesList(tid(), false, -1, true, 20, 0)
                  );
                }}
              />
              <ActionButton
                icon="🔢"
                title="Get Unread Count"
                subtitle="Count of unread messages"
                loading={loadingKey === 'getUnreadMessagesCount'}
                onPress={() =>
                  run('getUnreadMessagesCount', () =>
                    getUnreadMessagesCount(threadId ? tid() : undefined)
                  )
                }
              />
            </Section>

            <Section title="Mark as Read">
              <ActionButton
                icon="✅"
                title="Mark Conversation as Read"
                subtitle="Requires Thread ID"
                variant="success"
                loading={loadingKey === 'markConversationAsRead'}
                onPress={() => {
                  if (!needsThread()) return;
                  run('markConversationAsRead', () =>
                    markConversationAsRead(tid())
                  );
                }}
              />
              <ActionButton
                icon="✔️"
                title="Mark Message as Read"
                subtitle="Requires Thread ID + Message ID"
                variant="success"
                loading={loadingKey === 'markMessageAsRead'}
                onPress={() => {
                  if (!needsThreadAndMsg()) return;
                  run('markMessageAsRead', () =>
                    markMessageAsRead(tid(), mid(), false)
                  );
                }}
              />
              <ActionButton
                icon="☑️"
                title="Mark All Conversations Read"
                subtitle="Clears all unread state"
                variant="success"
                loading={loadingKey === 'markAllConversationsAsRead'}
                onPress={() =>
                  run('markAllConversationsAsRead', () =>
                    markAllConversationsAsRead()
                  )
                }
              />
            </Section>

            <Section title="Delete">
              <ActionButton
                icon="🗑️"
                title="Delete Conversation"
                subtitle="Requires Thread ID — permanent!"
                variant="danger"
                loading={loadingKey === 'deleteConversation'}
                onPress={() => {
                  if (!needsThread()) return;
                  Alert.alert(
                    'Delete Conversation',
                    `Are you sure you want to delete thread ${threadId}?`,
                    [
                      { text: 'Cancel', style: 'cancel' },
                      {
                        text: 'Delete',
                        style: 'destructive',
                        onPress: () =>
                          run('deleteConversation', () =>
                            deleteConversation(tid())
                          ),
                      },
                    ]
                  );
                }}
              />
              <ActionButton
                icon="❌"
                title="Delete Message"
                subtitle="Requires Thread ID + Message ID"
                variant="danger"
                loading={loadingKey === 'deleteMessage'}
                onPress={() => {
                  if (!needsThreadAndMsg()) return;
                  Alert.alert(
                    'Delete Message',
                    `Delete message ${messageId} from thread ${threadId}?`,
                    [
                      { text: 'Cancel', style: 'cancel' },
                      {
                        text: 'Delete',
                        style: 'destructive',
                        onPress: () =>
                          run('deleteMessage', () =>
                            deleteMessage(tid(), mid(), false)
                          ),
                      },
                    ]
                  );
                }}
              />
            </Section>
          </>
        )}

        {/* ── SEND SMS TAB ── */}
        {tab === 'send' && (
          <>
            <Section title="Compose">
              <StyledInput
                label="Recipient Phone Number"
                placeholder="+91 98765 43210"
                value={phoneNumber}
                onChangeText={setPhoneNumber}
                keyboardType="phone-pad"
                icon="📞"
              />
              <View style={inputStyles.wrapper}>
                <Text style={inputStyles.label}>✍️ Message</Text>
                <TextInput
                  style={[
                    inputStyles.input,
                    {
                      minHeight: 100,
                      textAlignVertical: 'top',
                      paddingTop: 12,
                    },
                  ]}
                  placeholder="Type your message here…"
                  placeholderTextColor={C.textDim}
                  value={smsText}
                  onChangeText={setSmsText}
                  multiline
                  selectionColor={C.accent}
                />
              </View>
              <View
                style={{
                  flexDirection: 'row',
                  justifyContent: 'flex-end',
                  marginTop: -4,
                  marginBottom: 12,
                }}
              >
                <Text style={{ color: C.textDim, fontSize: 12 }}>
                  {smsText.length} chars
                </Text>
              </View>
            </Section>

            <ActionButton
              icon="🚀"
              title="Send SMS"
              subtitle={
                phoneNumber
                  ? `To: ${phoneNumber}`
                  : 'Enter a phone number first'
              }
              variant="success"
              loading={loadingKey === 'sendSmsMessage'}
              onPress={() => {
                if (!needsPhone()) return;
                if (!smsText.trim()) {
                  Alert.alert('Empty Message', 'Type something to send.');
                  return;
                }
                run('sendSmsMessage', () =>
                  sendSmsMessage(smsText, [phoneNumber], -1, false)
                );
              }}
            />
          </>
        )}

        {/* ── TOOLS TAB ── */}
        {tab === 'tools' && (
          <>
            <Section title="Phone Lookup">
              <StyledInput
                label="Phone Number"
                placeholder="+91 98765 43210"
                value={phoneNumber}
                onChangeText={setPhoneNumber}
                keyboardType="phone-pad"
                icon="📞"
              />
              <ActionButton
                icon="🔍"
                title="Find Conversations by Number"
                subtitle="Lookup threads for a phone number"
                loading={loadingKey === 'getConversationsByPhoneNumber'}
                onPress={() => {
                  if (!needsPhone()) return;
                  run('getConversationsByPhoneNumber', () =>
                    getConversationsByPhoneNumber(phoneNumber, 20, 0)
                  );
                }}
              />
            </Section>

            <Divider />

            <Section title="App Settings">
              <ActionButton
                icon="⚙️"
                title="Set as Default SMS App"
                subtitle="Request default messaging role"
                variant="warning"
                loading={loadingKey === 'setDefaultMessage'}
                onPress={() =>
                  run('setDefaultMessage', () => setDefaultMessage())
                }
              />
            </Section>
          </>
        )}
      </ScrollView>

      <TabBar active={tab} onChange={setTab} />
      <ResultModal result={result} onClose={() => setResult(null)} />
    </SafeAreaView>
  );
}

const appStyles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: C.border,
    backgroundColor: C.surface,
  },
  appName: {
    color: C.textPrimary,
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: -0.5,
  },
  appSub: {
    color: C.textDim,
    fontSize: 12,
    marginTop: 1,
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
});
