# 💸 DebtSlayer

Aplikasi Android untuk melacak cicilan hutang harian dengan AI chatbot karakter **Sakurajima Mai** dari anime *Seishun Buta Yarou wa Bunny Girl Senpai*.

⚡ **Powered by Gemini & Claude**

---

## ✨ Fitur Utama

- 💬 **Chat dengan Mai** — AI berbasis Gemini yang merespons dengan gaya tsundere, mengingatkan target setoran harian
- 📊 **Progress Tracker** — Pantau sisa hutang, total setoran, dan persentase pelunasan secara real-time
- 📅 **Kalender Visual** — Lihat histori setoran per hari dengan indikator ✅ full / ⚠️ sebagian / ❌ kosong
- 📋 **Riwayat Transaksi** — Semua setoran tersimpan dan bisa dihapus
- 🔔 **Notifikasi Harian** — Reminder otomatis sesuai jam yang ditentukan, gaya bahasa menyesuaikan mode kepribadian Mai
- 🧠 **3 Mode Kepribadian** — Strict, Balanced, atau Gentle — bisa diubah di Settings
- 📱 **Widget** — Lihat progress hutang langsung dari home screen

---

## 🛠️ Tech Stack

| Komponen | Teknologi |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| AI | Google Gemini 2.5 Flash Lite |
| Database | Room (SQLite) |
| Preferences | DataStore |
| Architecture | MVVM |
| Notifikasi | AlarmManager + BroadcastReceiver |

---

## ⚙️ Setup & Instalasi

### 1. Clone Repository

```bash
git clone https://github.com/username/DebtSlayer.git
cd DebtSlayer
```

### 2. Dapatkan Gemini API Key

1. Buka [Google AI Studio](https://aistudio.google.com/)
2. Klik **Get API Key** → **Create API Key**
3. Copy API key yang dihasilkan

### 3. Konfigurasi API Key

Buat file `local.properties` di root project (jika belum ada):

```properties
sdk.dir=C\:\\Users\\NamaKamu\\AppData\\Local\\Android\\Sdk
GEMINI_API_KEY=masukkan_api_key_kamu_di_sini
```

> ⚠️ **JANGAN** commit file `local.properties` ke GitHub — sudah ada di `.gitignore`

### 4. Build & Run

Buka project di Android Studio, sync Gradle, lalu jalankan di emulator atau device fisik.

---

## 📱 Cara Penggunaan

### Setup Pertama
Saat pertama kali buka app, isi:
- **Total hutang** yang ingin dilunasi (contoh: Rp 12.445.000)
- **Deadline pelunasan** — tanggal target hutang lunas

Mai akan menghitung target setoran harian secara otomatis.

### Chat dengan Mai
Langsung ketik di kolom chat:
- `"setor 50rb"` → Mai mencatat setoran Rp 50.000
- `"nabung 1jt"` → Mencatat Rp 1.000.000
- `"hapus setoran"` → Menghapus setoran terakhir
- Tanya status, curhat, atau sekedar ngobrol — Mai akan merespons sesuai karakter

### Feedback
Gunakan 👍 👎 di setiap respons Mai untuk membantu sistem belajar preferensi kamu.

---

## 🏗️ Struktur Project

```
app/src/main/java/com/hyse/debtslayer/
├── data/
│   ├── dao/          # Room DAO interfaces
│   ├── database/     # DebtDatabase setup
│   ├── entity/       # Data classes (Transaction, ChatMessage, dll)
│   ├── preferences/  # DataStore configuration
│   └── repository/   # Repository layer
├── notification/     # DailyReminderReceiver, Scheduler, BootReceiver
├── personality/      # AdaptiveMaiPersonality (mode STRICT/BALANCED/GENTLE)
├── ui/
│   ├── components/   # ChatBubble, ChatInputField, DebtProgressCard
│   ├── screens/      # ChatScreen, HomeScreen, HistoryScreen, dll
│   └── theme/        # Color, Typography, Theme
├── utils/            # CurrencyFormatter
├── viewmodel/        # DebtViewModel, DebtViewModelFactory
├── widget/           # DebtWidget (home screen widget)
└── MainActivity.kt
```

---

## 🔒 Keamanan

- API Key disimpan di `local.properties` — tidak pernah masuk ke repository
- Database Room terenkripsi oleh Android secara default
- Tidak ada data pengguna yang dikirim ke server selain request ke Gemini API

---

## 🤝 Kontribusi

Pull request welcome. Untuk perubahan besar, buka issue terlebih dahulu.

---

## 📄 Lisensi

MIT License — bebas digunakan dan dimodifikasi.
