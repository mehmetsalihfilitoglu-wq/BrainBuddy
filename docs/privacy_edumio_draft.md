# EDUmio — Gizlilik Politikası (TASLAK)

> **DRAFT — REQUIRES LEGAL REVIEW BEFORE PUBLIC RELEASE.**
> Bu taslak yalnızca **şu anda uygulanmış** ürün davranışına dayanır. Hiçbir uydurma uyumluluk beyanı,
> şirket bilgisi, adres, saklama süresi, hukuki dayanak veya üçüncü taraf işleyici içermez; bilinmeyen
> bilgiler `[PLACEHOLDER]` olarak işaretlenmiştir. Yayına çıkmadan önce bir hukuk danışmanı tarafından
> tamamlanmalı ve onaylanmalıdır.

**Ürün:** EDUmio — İtalya üniversite giriş sınavlarına (IMAT, TIL-I, CEnT-S) hazırlık için bir mobil
öğrenme uygulaması. **Ebeveyn kontrolü, uygulama engelleme, cihaz yöneticisi, çocuk ses tanıma veya reklam
YOKTUR.**

## 1. Veri Sorumlusu
- Veri sorumlusu: `[PLACEHOLDER — yasal kuruluş adı]`
- Adres: `[PLACEHOLDER — tescilli adres]`
- İletişim e-postası: `[PLACEHOLDER — iletişim/KVKK e-postası]`

## 2. İşlenen Veriler (mevcut uygulama — tamamı cihazda yereldir)
Bugün itibarıyla EDUmio verileri **yalnızca cihazınızda** (yerel Room veritabanları + tercihler) saklar ve
**hiçbir kişisel veriyi sunucuya göndermez**. Yerel olarak tutulanlar:
- Çalışma alanı/profil tercihi (seçilen sınav) ve anonim yerel kullanıcı kimliği (rastgele üretilen UUID —
  kişisel kimlik bilgisi değildir).
- Öğrenme durumu: Günün Görevi geçmişi, cevaplar, doğru/yanlış durumu, tekrar kuyruğu, seri (streak),
  bölüm dağılımı istatistikleri.
- Cihaz-içi analiz olayları: yalnızca cihazda, bellek içi, sınırlı bir tampon; **soru metinleri veya cevap
  içerikleri kaydedilmez** (yalnızca olay adı + kaba parametreler). Sunucuya iletilmez.
- Bildirim tercihleri ve yerel hatırlatıcı işaretleri.

## 3. İzinler
- **Bildirimler (Android 13+ POST_NOTIFICATIONS):** yalnızca yerel çalışma hatırlatıcıları için, bağlamsal
  olarak istenir; reddedilebilir; ayarlardan yönetilebilir.
- Uygulama; konum, kişiler, mikrofon, kamera, erişilebilirlik hizmeti veya cihaz yöneticisi izni
  **kullanmaz**.

## 4. Üçüncü Taraf Hizmetler
- **Şu an aktif üçüncü taraf veri paylaşımı yoktur.** Reklam (AdMob) kullanılmaz.
- Uygulama derlemesinde Google Play Faturalandırma (Play Billing) kütüphanesi bulunur ancak **canlı satın
  alma akışı henüz etkin değildir**; etkinleştiğinde satın alma işlemleri Google Play tarafından işlenecektir
  (bkz. Gelecekteki İşlevler).

## 5. E-posta Raporları (isteğe bağlı, yerel)
Kullanıcı bir performans raporu paylaşmayı seçerse, rapor cihazın **kendi e-posta/paylaşım istemcisi**
aracılığıyla gönderilir. EDUmio **sunucu üzerinden e-posta göndermez** ve rapor içeriğini bir sunucuda
saklamaz.

## 6. Veri Saklama ve Silme
- Tüm veriler cihazda kalır; uygulamayı kaldırdığınızda yerel veriler silinir.
- Yedekleme/geri yükleme kullanılırsa veriler yalnızca sizin seçtiğiniz konuma aktarılır.
- Sunucu tarafı saklama süreleri: `[PLACEHOLDER — backend etkinleştiğinde tanımlanacak]`.

## 7. Hukuki Dayanak (KVKK / GDPR)
- `[PLACEHOLDER — her işleme faaliyeti için hukuki dayanak; hukuk incelemesi gerektirir]`.

## 8. Haklarınız
Yerel veriler için: uygulama içi "Verilerim" ekranından dışa aktarım yapabilir ve uygulamayı kaldırarak
silebilirsiniz. Yasal veri sahibi hakları (erişim, düzeltme, silme, itiraz) için: `[PLACEHOLDER — iletişim]`.

## 9. Gelecekteki İşlevler (henüz UYGULANMADI — bugün geçerli değildir)
Aşağıdakiler planlanmıştır ancak mevcut sürümde **yoktur**; etkinleştirilmeden önce bu politika
güncellenmelidir: bulut senkronizasyonu ve sunucu kimlik doğrulama (Firebase arayüzü bağlanmamış),
sunucu tarafı abonelik/hak doğrulama, sunucu üzerinden e-posta gönderimi, uzak analiz toplama, canlı
ödeme. Bunların her biri için işleyiciler, aktarımlar ve saklama süreleri `[PLACEHOLDER]`.

## 10. Çocuklar
EDUmio bir sınav hazırlık uygulamasıdır ve çocuklara yönelik bir hizmet olarak pazarlanmaz. Hedef kitle/
yaş sınırı: `[PLACEHOLDER — hukuk incelemesi]`.

## 11. Değişiklikler ve İletişim
Politika güncellenebilir. İletişim: `[PLACEHOLDER — e-posta]`.

_Son güncelleme: `[PLACEHOLDER — tarih]` · Sürüm: TASLAK 0.1_
