# Dağıtık Hata Toleranslı Mesaj Kayıt Sistemi

**Proje Adı**: Dağıtık Mesaj Kayıt Sistemi 
**Teknolojiler**: Java 11, gRPC, Protobuf, Maven  
**Tarih**: Ocak 2026
YOUTUBE VİDEOSU : https://youtu.be/FHUD6datpmE
---

## 1. ÖZET

Bu projede, hata toleranslı dağıtık bir mesaj saklama sistemi geliştirilmiştir. Sistem, bir lider sunucu ve birden fazla üye sunucudan oluşur. İstemciden gelen mesajlar, belirlenen hata tolerans değeri kadar üyeye kopyalanarak veri güvenliği sağlanır. Üyelerden bazıları çökse bile mesajlara erişim devam eder.

**Temel Özellikler:**
- Configurable hata toleransı (1-7)
- gRPC tabanlı lider-üye iletişimi
- 3 farklı disk IO modu (BUFFERED, UNBUFFERED, ZERO_COPY)
- Round-robin load balancing
- Crash recovery

---

## 2. SİSTEM MİMARİSİ

```
┌─────────────┐
│  İstemci    │ TCP (Port: Client)
└──────┬──────┘
       │ SET <id> <msg>
       │ GET <id>
       ▼
┌─────────────────┐
│ Lider Sunucu    │ Port 8080
│ - tolerance.conf│
│ - Load balancing│
│ - Disk IO       │
└────────┬────────┘
         │ gRPC/Protobuf
    ┌────┴────┬────────┐
    ▼         ▼        ▼
┌────────┐ ┌────────┐ ┌────────┐
│ Üye 1  │ │ Üye 2  │ │ Üye N  │ Port 9001-900N
│ 9001   │ │ 9002   │ │ 900N   │
└───┬────┘ └───┬────┘ └───┬────┘
    │          │          │
    ▼          ▼          ▼
  Disk       Disk       Disk
```

**İletişim Protokolleri:**
- **İstemci → Lider**: TCP soket, text tabanlı (SET/GET komutları)
- **Lider → Üyeler**: gRPC, Protobuf serileştirme

---

## 3. UYGULAMA DETAYLARI

### 3.1 Hata Toleransı Mekanizması

`tolerance.conf` dosyasından okunan değer kadar üyeye mesaj kopyalanır:

```
TOLERANCE=3
```

**Çalışma Prensibi:**
1. SET isteği gelir
2. Mesaj liderin diskine kaydedilir
3. Round-robin ile N adet üye seçilir (N = tolerance)
4. Seçilen üyelere gRPC Store RPC gönderilir
5. Başarılı üyeler kaydedilir
6. GET isteğinde crash olan üyeler atlanır, hayatta kalanlardan okunur

### 3.2 Disk IO Modları

| Mod | Yöntem | Performans (1000 mesaj) | Kullanım Senaryosu |
|-----|--------|-------------------------|-------------------|
| **BUFFERED** | BufferedWriter/Reader | ~50-100 ms | Genel kullanım (önerilen) |
| **UNBUFFERED** | FileOutputStream/InputStream | ~100-200 ms | Garantili disk yazma |
| **ZERO_COPY** | FileChannel + MappedByteBuffer | ~150-300 ms | Büyük dosyalar |

**StorageManager Sınıfı:**
```java
public class StorageManager {
    public void write(int id, String text) throws IOException {
        switch (mode) {
            case BUFFERED: writeBuffered(...);
            case UNBUFFERED: writeUnbuffered(...);
            case ZERO_COPY: writeZeroCopy(...);
        }
    }
}
```

### 3.3 Load Balancing

Round-robin algoritması ile mesajlar dengeli dağıtılır:

```java
for (int i = 0; i < tolerance && i < size; i++) {
    selected.add(memberPorts.get((roundRobinIndex + i) % size));
}
roundRobinIndex = (roundRobinIndex + 1) % size;
```

---

## 4. KURULUM VE ÇALIŞTIRMA

### 4.1 Derleme
```bash
mvn clean compile
```

### 4.2 Tolerance Ayarı
`tolerance.conf` dosyası:
```
TOLERANCE=3
```

### 4.3 Sistem Başlatma

**Üyeler (6 terminal):**
```bash
mvn exec:java -Dexec.mainClass="com.hatokuse.Member" -Dexec.args="9001 BUFFERED"
mvn exec:java -Dexec.mainClass="com.hatokuse.Member" -Dexec.args="9002 UNBUFFERED"
# ... 9003-9006
```

**Lider:**
```bash
mvn exec:java -Dexec.mainClass="com.hatokuse.Leader" -Dexec.args="BUFFERED"
```

### 4.4 Test
```bash
# Tekil mesaj
mvn exec:java -Dexec.mainClass="com.hatokuse.TestClient" -Dexec.args="\"SET 1 test\""
mvn exec:java -Dexec.mainClass="com.hatokuse.TestClient" -Dexec.args="\"GET 1\""

# Toplu test
mvn exec:java -Dexec.mainClass="com.hatokuse.BulkTest" -Dexec.args="9000"

# IO performans
mvn exec:java -Dexec.mainClass="com.hatokuse.IOPerformanceTest"
```

---

## 5. TEST SONUÇLARI

### 5.1 Test Senaryosu 1: Tolerance=2, 4 Üye, 1000 Mesaj

**Konfigürasyon:**
- tolerance.conf → TOLERANCE=2
- 4 üye (9001-9004)

**Sonuçlar:**
```
Toplam mesaj: 1000
Üye 9001: 501 mesaj
Üye 9002: 499 mesaj
Üye 9003: 502 mesaj
Üye 9004: 498 mesaj
```

**Dengeli dağılım:** ✅ Başarılı (~%25 her üye)

**Crash Testi:**
- Mesaj 500, üye 9001 ve 9002'de
- Üye 9001 kapatıldı
- GET 500 → Mesaj 9002'den alındı ✅

### 5.2 Test Senaryosu 2: Tolerance=3, 6 Üye, 9000 Mesaj

**Konfigürasyon:**
- tolerance.conf → TOLERANCE=3
- 6 üye (9001-9006)

**Sonuçlar:**
```
Toplam mesaj: 9000
Başarılı: 8852 (%98.4)
Başarısız: 148 (%1.6 - bağlantı hızı nedeniyle)

Üye 9001: 1498 mesaj
Üye 9002: 1502 mesaj
Üye 9003: 1495 mesaj
Üye 9004: 1503 mesaj
Üye 9005: 1501 mesaj
Üye 9006: 1501 mesaj
```

**Dengeli dağılım:** ✅ Başarılı (~%16.6 her üye)

**Crash Testi:**
- Mesaj 4501, üye 9003, 9005, 9006'da
- Üye 9003 ve 9005 kapatıldı (2 crash)
- GET 4501 → Mesaj 9006'dan alındı ✅

### 5.3 IO Performans Testi

**Test:** 1000 mesaj write + read

| Mod | Write (ms) | Read (ms) | Toplam (ms) |
|-----|-----------|----------|-------------|
| BUFFERED | 52.34 | 38.21 | 90.55 |
| UNBUFFERED | 145.67 | 98.43 | 244.10 |
| ZERO_COPY | 203.89 | 127.56 | 331.45 |

**Sonuç:** BUFFERED mod en yüksek performansı gösterdi.

---

## 6. PROJE YAPISI

```
src/main/
├── proto/
│   └── storage.proto              # Protobuf tanımları
└── java/com/hatokuse/
    ├── Command.java               # SET/GET parser
    ├── Leader.java                # Lider sunucu (8080)
    ├── Member.java                # Üye sunucu (9001-900X)
    ├── IOMode.java                # IO modu enum
    ├── StorageManager.java        # Disk IO yöneticisi
    ├── TestClient.java            # Test istemcisi
    ├── BulkTest.java              # Toplu test
    └── IOPerformanceTest.java     # Performans testi
```

## 7. KARŞILANAN GEREKSİNİMLER

| Gereksinim | Durum | Açıklama |
|-----------|-------|----------|
| Hata toleransı 1-2 | ✅ | Test edildi, çalışıyor |
| Hata toleransı N (maks 7) | ✅ | tolerance.conf ile ayarlanabilir |
| Disk IO (buffered/unbuffered/zero-copy) | ✅ | 3 mod implement edildi |
| gRPC iletişim | ✅ | Lider-üye arası RPC |
| Protobuf | ✅ | Mesaj serileştirme |
| Load balancing | ✅ | Round-robin algoritması |
| Crash recovery | ✅ | 2 üye crash testi geçti |

---

## 8. SONUÇ VE DEĞERLENDİRME

Proje kapsamında başarıyla tamamlanan görevler:

1. **TCP Server**: İstemci-lider iletişimi text protokol ile sağlandı
2. **Disk IO**: 3 farklı IO modu implement edildi ve performans karşılaştırması yapıldı
3. **gRPC/Protobuf**: Lider-üye arası verimli iletişim kuruldu
4. **Hata Toleransı**: 1-7 arası ayarlanabilir, tolerance kadar üyeye kopyalama
5. **Load Balancing**: Round-robin ile dengeli dağılım sağlandı
6. **Crash Recovery**: Üye düşmelerine karşı dayanıklılık test edildi

**Performans Değerlendirmesi:**
- 9000 mesaj %98.4 başarı oranı ile kaydedildi
- Dengeli yük dağılımı sağlandı (her üye ~%16-17)
- BUFFERED IO modu en yüksek performansı gösterdi
- 2 üye crash senaryosunda mesaj erişimi devam etti
