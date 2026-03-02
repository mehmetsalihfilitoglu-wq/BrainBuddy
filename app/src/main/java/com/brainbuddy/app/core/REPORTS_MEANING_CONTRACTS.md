# Rapor Widget Anlam Sözleşmeleri (Meaning Contracts)

Her rapor bileşeni için:
- **Metrik adı + birim**
- **Tarih aralığı** (Bugün / 7 gün / 30 gün)
- **Nasıl okunur** (1 satır yardımcı)
- **Her nokta/bar neyi temsil eder**

---

## A) Haftalık Başarı
- **Metrik:** Doğru, Yanlış, Boş sayıları; Doğruluk %; Test sayısı; Engellenen deneme sayısı
- **Birim:** sayı, %
- **Aralık:** Bugün / Son 7 gün / Son 30 gün (cihaz yerel gece yarısı)
- **Nasıl okunur:** Seçili aralıktaki toplam doğru, yanlış, boş; doğruluk = doğru/(doğru+yanlış+boş)
- **Her bar/nokta:** Yok (özet kart)

## B) Konulara Göre
- **Metrik:** Konu bazlı doğru/toplam oranı
- **Birim:** doğru/toplam (ör. 12/20)
- **Aralık:** Tüm geçmiş (filtre uygulanmaz)
- **Nasıl okunur:** Her bar bir konuyu gösterir; yeşil kısım doğru, gri kısım toplam
- **Yardımcı metin:** "Her bar bir konudur. Doğru/toplam oranı."

## C) Son 10 Test Başarı Oranı
- **Metrik:** Test başarı oranı (%)
- **Birim:** % (0–100)
- **Aralık:** Seçili filtreye göre son 10 test
- **Y ekseni:** 0, 25, 50, 75, 100 (görünür çizgiler)
- **X ekseni:** Test sırası (1, 2, 3…) veya kısa tarih
- **Her nokta:** 1 test oturumu
- **Tooltip (dokunma):** "Test adı • tarih • doğru/toplam • %"
- **Mini KPIs (sağ üst):** Ortalama %, Son test %, Trend (↑/↓/→)
- **Yardımcı metin:** "Her nokta 1 testtir. Başarı = doğru/(doğru+yanlış)"

## D) Son Testler
- **Metrik:** Tamamlanan test listesi
- **Birim:** Test başına skor (doğru/toplam)
- **Aralık:** Bugün / 7 gün / 30 gün
- **Nasıl okunur:** Her satır bir test; tıklayınca detay
- **Her satır:** Bir tamamlanmış test

## E) En Çok Denenen Uygulamalar
- **Metrik:** Engellenen uygulama deneme sayısı
- **Birim:** sayı
- **Aralık:** Bugün / 7 gün / 30 gün
- **Nasıl okunur:** Bar uzunluğu en yüksek denemeye göre oranlanır
- **Her satır:** Bir engellenen uygulama, kaç kez açılmaya çalışıldığı

## Boş Durum
- **Metin:** "Bu aralıkta veri yok. Test çözünce burada görünecek."
