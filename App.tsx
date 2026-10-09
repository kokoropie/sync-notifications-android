import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  BackHandler,
  FlatList,
  Image,
  Linking,
  AppState,
  NativeModules,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

const { NotifyModule } = NativeModules;

type Status = {
  notificationAccess: boolean;
  phoneState: boolean;
  callLog: boolean;
  contacts: boolean;
  clipboardAccessibility: boolean;
  batteryUnrestricted: boolean;
};

const Button = ({ title, onPress, secondary }: { title: string; onPress: () => void; secondary?: boolean }) => (
  <Pressable onPress={onPress} style={[s.btn, secondary && s.btnSecondary]}>
    <Text style={[s.btnText, secondary && s.btnTextSecondary]}>{title}</Text>
  </Pressable>
);

const Row = ({ label, ok, action }: { label: string; ok: boolean; action?: () => void }) => (
  <View style={s.row}>
    <Text style={s.rowLabel}>
      {ok ? '✅' : '⚠️'} {label}
    </Text>
    {!ok && action && <Button title="Cấp quyền" onPress={action} secondary />}
  </View>
);

type AppInfo = { packageName: string; label: string; icon?: string };

function AppPicker({ onClose }: { onClose: () => void }) {
  const [apps, setApps] = useState<AppInfo[] | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [pinned, setPinned] = useState<Set<string>>(new Set()); // app đã bật lúc mở màn hình: lên đầu, không nhảy khi đang bật/tắt
  const [query, setQuery] = useState('');

  useEffect(() => {
    NotifyModule.getWhitelist().then((w: any) => {
      setEnabled(w.enabled);
      setSelected(new Set(w.packages));
      setPinned(new Set(w.packages));
    });
    NotifyModule.getInstalledApps().then(setApps);
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => sub.remove();
  }, [onClose]);

  const persist = (en: boolean, sel: Set<string>) => NotifyModule.setWhitelist(en, [...sel]);

  const toggleApp = (pkg: string) => {
    const next = new Set(selected);
    next.has(pkg) ? next.delete(pkg) : next.add(pkg);
    setSelected(next);
    persist(enabled, next);
  };

  const toggleEnabled = (v: boolean) => {
    setEnabled(v);
    persist(v, selected);
  };

  const q = query.trim().toLowerCase();
  const shown = (apps ?? [])
    .filter(a => !q || a.label.toLowerCase().includes(q) || a.packageName.includes(q))
    .sort((a, b) => Number(pinned.has(b.packageName)) - Number(pinned.has(a.packageName)) || a.label.localeCompare(b.label, 'vi', { sensitivity: 'base' }));

  return (
    <SafeAreaView style={s.root} edges={['top', 'bottom', 'left', 'right']}>
      <View style={s.body}>
        <View style={s.row}>
          <Button title="← Quay lại" onPress={onClose} secondary />
          <Text style={s.rowLabel}>Chọn app ({selected.size})</Text>
        </View>
        <View style={s.row}>
          <Text style={s.rowLabel}>Chỉ gửi thông báo của app đã chọn{enabled ? '' : ' (đang tắt: gửi tất cả)'}</Text>
          <Switch value={enabled} onValueChange={toggleEnabled} />
        </View>
        <TextInput style={s.input} value={query} onChangeText={setQuery} placeholder="Tìm app…" placeholderTextColor="#888" autoCapitalize="none" />
      </View>
      {apps === null ? (
        <Text style={[s.hint, { padding: 16 }]}>Đang tải danh sách app…</Text>
      ) : (
        <FlatList
          data={shown}
          keyExtractor={a => a.packageName}
          contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 24 }}
          renderItem={({ item }) => (
            <View style={[s.row, { paddingVertical: 6 }]}>
              {item.icon ? <Image source={{ uri: 'data:image/png;base64,' + item.icon }} style={{ width: 36, height: 36 }} /> : <View style={{ width: 36 }} />}
              <Text style={s.rowLabel} numberOfLines={1}>{item.label}</Text>
              <Switch value={selected.has(item.packageName)} onValueChange={() => toggleApp(item.packageName)} />
            </View>
          )}
        />
      )}
    </SafeAreaView>
  );
}

function Main() {
  const [picker, setPicker] = useState(false);
  const [serverUrl, setServerUrl] = useState('');
  const [accountKey, setAccountKey] = useState('');
  const [deviceName, setDeviceName] = useState('');
  const [clipboardSync, setClipboardSync] = useState(true);
  const [status, setStatus] = useState<Status | null>(null);
  const [msg, setMsg] = useState('');
  const [version, setVersion] = useState('');

  const refresh = useCallback(async () => setStatus(await NotifyModule.getStatus()), []);

  useEffect(() => {
    NotifyModule.getConfig().then((c: any) => {
      setServerUrl(c.serverUrl);
      setAccountKey(c.accountKey);
      setDeviceName(c.deviceName);
      setClipboardSync(c.clipboardSync);
    });
    NotifyModule.getAppInfo().then((i: any) => setVersion(i.version));
    refresh();
    const sub = AppState.addEventListener('change', st => st === 'active' && refresh());
    return () => sub.remove();
  }, [refresh]);

  const requestRuntime = async (perms: string[]) => {
    await PermissionsAndroid.requestMultiple(perms as any);
    refresh();
  };

  const save = async () => {
    await NotifyModule.saveConfig({ serverUrl, accountKey, deviceName, clipboardSync });
    try {
      await NotifyModule.testConnection();
      setMsg('✅ Kết nối server thành công');
    } catch (e: any) {
      setMsg('❌ ' + (e?.message ?? 'Không kết nối được'));
    }
  };

  const sendClip = async () => {
    try {
      await NotifyModule.sendClipboard();
      setMsg('📋 Đã gửi clipboard sang Mac');
    } catch (e: any) {
      Alert.alert('Không gửi được', e?.message ?? '');
    }
  };

  const closePicker = useCallback(() => setPicker(false), []);
  if (picker) return <AppPicker onClose={closePicker} />;

  return (
    <SafeAreaView style={s.root} edges={['top', 'bottom', 'left', 'right']}>
      <ScrollView contentContainerStyle={s.body}>
        <View style={s.header}>
          <Image source={require('./assets/logo-bell.png')} style={s.logo} />
          <Text style={s.h1}>Sync Notification</Text>
        </View>

        <Text style={s.h2}>Server</Text>
        <TextInput style={s.input} value={serverUrl} onChangeText={setServerUrl} placeholder="https://server.example.com" placeholderTextColor="#888" autoCapitalize="none" autoCorrect={false} />
        <TextInput style={s.input} value={accountKey} onChangeText={setAccountKey} placeholder="Account key (ntf_...)" placeholderTextColor="#888" autoCapitalize="none" autoCorrect={false} secureTextEntry />
        <TextInput style={s.input} value={deviceName} onChangeText={setDeviceName} placeholder="Tên thiết bị" placeholderTextColor="#888" />
        <View style={s.row}>
          <Text style={s.rowLabel}>Đồng bộ clipboard</Text>
          <Switch value={clipboardSync} onValueChange={setClipboardSync} />
        </View>
        <Button title="Lưu & kiểm tra kết nối" onPress={save} />
        {!!msg && <Text style={s.msg}>{msg}</Text>}

        <Text style={s.h2}>Quyền</Text>
        {status && (
          <>
            <Row label="Truy cập thông báo" ok={status.notificationAccess} action={() => NotifyModule.openNotificationAccessSettings()} />
            <Row
              label="Trạng thái điện thoại + nhật ký cuộc gọi"
              ok={status.phoneState && status.callLog}
              action={() => requestRuntime([PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE, PermissionsAndroid.PERMISSIONS.READ_CALL_LOG])}
            />
            <Row label="Danh bạ (hiện tên người gọi)" ok={status.contacts} action={() => requestRuntime([PermissionsAndroid.PERMISSIONS.READ_CONTACTS])} />
            <Row label="Trợ năng: đọc clipboard ở nền (Android → Mac tự động)" ok={status.clipboardAccessibility} action={() => NotifyModule.openAccessibilitySettings()} />
            <Row label="Không giới hạn pin (giữ kết nối nền)" ok={status.batteryUnrestricted} action={() => NotifyModule.requestIgnoreBatteryOptimizations()} />
            {Number(Platform.Version) >= 33 && (
              <Button title="Cho phép hiện thông báo dịch vụ" secondary onPress={() => requestRuntime(['android.permission.POST_NOTIFICATIONS'])} />
            )}
          </>
        )}

        <Text style={s.h2}>Thông báo</Text>
        <Button title="Chọn app được gửi thông báo (whitelist)" onPress={() => setPicker(true)} />

        <Text style={s.h2}>Clipboard</Text>
        <Text style={s.hint}>
          Mac → Android luôn tự động. Android → Mac tự động khi bật dịch vụ Trợ năng ở trên (chỉ dùng để đọc clipboard ở nền); nếu không, bấm nút dưới,
          dùng ô "Gửi clipboard" trong Quick Settings, hoặc Chia sẻ → "Gửi sang Mac".
        </Text>
        <Button title="Gửi clipboard sang Mac" onPress={sendClip} />

        <Text style={s.h2}>Giới thiệu</Text>
        <View style={s.about}>
          <Image source={require('./assets/logo-bell.png')} style={s.aboutLogo} />
          <Text style={s.aboutName}>Sync Notification</Text>
          <Text style={s.hint}>Phiên bản {version}</Text>
          <Text style={s.hint}>Tác giả: Kaga Akatsuki</Text>
          <Pressable onPress={() => Linking.openURL('mailto:admin@kokoropie.info.vn')}>
            <Text style={s.link}>admin@kokoropie.info.vn</Text>
          </Pressable>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#f5f5f7' },
  body: { padding: 16, gap: 10 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  logo: { width: 44, height: 44 },
  h1: { fontSize: 26, fontWeight: '700' },
  h2: { fontSize: 16, fontWeight: '600', marginTop: 16 },
  input: { backgroundColor: '#fff', borderRadius: 8, padding: 12, borderWidth: 1, borderColor: '#ddd', color: '#000' },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  rowLabel: { flex: 1, fontSize: 15, color: '#111' },
  btn: { backgroundColor: '#2563eb', borderRadius: 8, paddingVertical: 12, paddingHorizontal: 14, alignItems: 'center' },
  btnSecondary: { backgroundColor: '#e5e7eb' },
  btnText: { color: '#fff', fontWeight: '600' },
  btnTextSecondary: { color: '#111' },
  msg: { fontSize: 14, color: '#111' },
  about: { alignItems: 'center', gap: 4, paddingVertical: 12 },
  aboutLogo: { width: 72, height: 72 },
  aboutName: { fontSize: 18, fontWeight: '600', color: '#111' },
  link: { fontSize: 13, color: '#2563eb' },
  hint: { fontSize: 13, color: '#555' },
});

export default function App() {
  return (
    <SafeAreaProvider>
      <Main />
    </SafeAreaProvider>
  );
}
