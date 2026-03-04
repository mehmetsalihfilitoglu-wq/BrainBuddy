# Test Senaryoları: Accessibility Bypass Koruması

## Uygulanan Çözümler Özeti

### A) AccessibilityService KAPALI Algılama
- **MainActivity**: Açılışta `LockModeMonitor.checkAndSetLockIfNeeded()` + `isLockModeActive()` kontrol edilir → ParentLockActivity (PinLockActivity) açılır
- **PermissionMonitorLauncher**: Her app resume'da (ön plana gelişte) aynı kontrol → PIN zorunlu

### B) Sürekli İzleme
- **AccessibilityMonitorService**: ForegroundService ile 10 sn aralıklarla AccessibilityService durumu kontrol edilir
- **AccessibilityCheckWorker**: WorkManager ile 15 dk periyodik fallback kontrol
- Servis kapalıysa `lockModeEnabled` DataStore'a yazılır

### C) Persistent State (LockModeDataStore)
- `lockModeEnabled` (bool)
- `lastKnownServiceEnabled` (bool)
- `parentPinHash` / PIN durumu (syncParentPinFromManager ile)

### D) Reboot / Update Sonrası
- **BootReceiver**: BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, MY_PACKAGE_REPLACED
- ReloadSettingsWorker: lockModeEnabled set eder
- AccessibilityMonitorService + AccessibilityCheckWorker başlatılır

### E) Race Condition Fix
- **ForegroundAppBlockerService**: `AtomicBoolean gateLaunchGuard` ile çift GateActivity/QuizActivity launch engellendi

---

## Test Senaryoları

### 1. Accessibility Kapat → Engellenen App Açılmayı Dene → Lock Gelir
**Adımlar:**
1. Koruma açık, AccessibilityService açık, engellenen uygulama listesinde bir app var
2. Ayarlardan BrainBuddy Accessibility iznini kapat
3. Engellenen uygulamayı açmayı dene
4. **Beklenen:** ParentLockActivity (PinLockActivity) açılır, PIN girilmeden devam edilemez

### 2. Force-Stop BrainBuddy → Tekrar Aç → lockMode Devam Etmeli
**Adımlar:**
1. Koruma açık, Accessibility kapalı (lockMode aktif)
2. Ayarlar → Uygulamalar → BrainBuddy → Force Stop
3. BrainBuddy'yi tekrar başlat
4. **Beklenen:** ParentLockActivity açılır (DataStore lockModeEnabled persistent)

### 3. Reboot → lockMode Devam Etmeli
**Adımlar:**
1. Koruma açık, Accessibility kapalı
2. Cihazı yeniden başlat
3. BrainBuddy'yi aç
4. **Beklenen:** ParentLockActivity açılır

### 4. Ana Ekrana Dön → Tekrar Lock
**Adımlar:**
1. lockMode aktif iken PIN girmeden ana ekrana git
2. BrainBuddy'yi tekrar aç veya bildirime dokun
3. **Beklenen:** PinLockActivity tekrar gösterilir

### 5. Çift Event (Race Condition)
**Adımlar:**
1. Accessibility açık, koruma açık
2. Engellenen uygulamayı çok hızlı iki kez aç (veya iki event aynı anda gelsin)
3. **Beklenen:** Tek GateActivity/QuizActivity açılır, çift launch olmaz
