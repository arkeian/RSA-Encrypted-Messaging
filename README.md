# Amaca

Amaca adalah aplikasi percakapan peer-to-peer berbasis browser. Dirancang sebagai cara sederhana untuk bertukar message secara langsung dengan pengguna lain, di mana karakteristik Amaca adalah:

- Peer-to-peer
  - Message dikirim antar-browser melalui WebRTC.
  - TURN server dapat digunakan ketika direct peer connection tidak tersedia.
- Dienkripsi menggunakan RSA
  - Setiap browser membuat RSA key pair untuk session-nya sendiri.
  - Message dienkripsi menggunakan public key penerima sebelum dikirim.
- Ephemeral
  - Message dan private key tidak disimpan setelah browser session berakhir.
- Decentralized
  - Tidak membutuhkan API server atau database untuk menjalankan fungsi dasarnya.
- [Self-hostable](#self-hosting)

Amaca menjalankan Java dan Swing di browser menggunakan [CheerpJ](https://cheerpj.com/). Peer discovery dan WebRTC connection ditangani oleh [Trystero](https://github.com/dmotz/trystero), sedangkan QR code dibuat menggunakan [ZXing](https://github.com/zxing/zxing).

## Cara menggunakan

Buka instance Amaca. Tampilan aplikasi akan muncul saat RSA key generation berjalan; tunggu sampai status menyatakan bahwa kunci siap. Masukkan display name, lalu buat room baru atau buka invitation link dari participant lain. Bagikan invitation link atau QR code kepada participant yang ingin dihubungi, bandingkan fingerprint public key pada kedua browser, lalu konfirmasi fingerprint tersebut di kedua sisi sebelum mengirim message.

Message ditandai sebagai **Delivered** apabila penerima berhasil mendekripsi dan merekonstruksi text yang dikirim. Tombol **Show RSA procedure** menampilkan input dan output proses RSA dari message yang dipilih.

Invitation link dapat disalin melalui menu klik-kanan pada panel link. Pilih message lalu tekan **Ctrl+C** atau gunakan menu klik-kanan untuk menyalin text-nya.

## Fitur

- Room privat melalui invitation link dengan identifier yang dibuat secara acak.
- QR code untuk membagikan invitation link.
- Pertukaran display name dan RSA public key secara otomatis.
- Fingerprint comparison sebelum percakapan diaktifkan.
- RSA key pair sekitar 2048-bit yang dibuat untuk setiap browser session.
- UTF-8 text dan multiple-block message.
- Enkripsi menggunakan public key penerima dan dekripsi menggunakan private key lokal.
- Delivery acknowledgement setelah message berhasil direkonstruksi.
- RSA procedure viewer untuk message yang dikirim dan diterima.
- Tidak ada analitik, tracking, atau telemetri yang digunakan.

## Anti-fitur

- Percakapan tidak disimpan ke local disk dan tidak dapat dipulihkan setelah browser session berakhir.
- Private key hanya berada di memory browser yang membuatnya.
- Amaca tidak memiliki central application server untuk menyimpan riwayat percakapan.

## Penggunaan

Amaca dapat digunakan untuk:

- Bertukar text secara langsung antara dua browser.
- Memindahkan text dari satu device ke device lain.
- Membagikan room melalui link atau QR code.
- Memeriksa RSA inputs dan outputs dari message yang benar-benar dikirim.

## Mengembangkan Amaca

Untuk menjalankan Amaca dari source, clone repository ini dan pastikan [JDK 17](https://adoptium.net/) serta [Node.js dan NPM](https://nodejs.org/) telah tersedia. Kemudian jalankan:

```bash
./build-browser.sh
```

Pada Windows, jalankan perintah tersebut melalui **Git Bash** atau **WSL**; Command Prompt dan PowerShell tidak menjalankan skrip Bash secara langsung.

Perintah tersebut mengkompilasi Java application dan membuat production files di dalam directory `dist`.

Jalankan local HTTP server dari hasil build:

```bash
npx http-server dist -p 8000
```

Buka <http://localhost:8000> menggunakan browser. Internet connection diperlukan untuk memuat CheerpJ; Trystero baru dimuat saat room dibuat atau diikuti, lalu menggunakan peer discovery services.

Startup menampilkan tahap pemuatan runtime Java. Jika proses CheerpJ berlangsung lebih dari 20 detik, halaman memberi tahu bahwa runtime masih dimulai; setelah UI terbuka, RSA key generation berjalan di background. Selama status masih **Generating keys...**, tombol room belum aktif. Klik-kanan invitation link untuk menyalinnya; pilih message lalu gunakan **Ctrl+C** atau klik-kanan untuk menyalin isi message.

## Self-hosting

Amaca dirancang sebagai static web application yang dapat di-fork dan di-host secara mandiri. Tidak ada Java process, API server, atau database yang harus dijalankan pada server.

> [!IMPORTANT]
> Peer hanya dapat bergabung ketika kedua participant menggunakan application identifier dan room identifier yang sama. Instance pada domain berbeda tidak dijamin dapat menemukan satu sama lain.

### Deployment

#### Pada GitHub

Fork repository ini. Workflow `.github/workflows/deploy-pages.yml` akan membangun directory `dist` dan men-deploy hasilnya ke GitHub Pages setiap kali branch `main` diperbarui.

#### Pada hosting selain GitHub

Build application menggunakan:

```bash
./build-browser.sh
```

Kemudian deploy seluruh isi directory `dist` ke static file hosting yang mendukung HTTPS. Struktur file di dalam `dist` harus dipertahankan agar CheerpJ dapat menemukan application JAR dan ZXing dependency.

### Runtime configuration

Amaca menggunakan direct WebRTC connection jika memungkinkan. Di mana TURN server dapat diberikan melalui `window.RSA_CHAT_TURN_SERVERS` sebelum module script pada `index.html`:

```html
<script>
    window.RSA_CHAT_TURN_SERVERS = [{
        urls: ["turn:turn-server.example:3478"],
        username: "username",
        credential: "credential"
    }];
</script>
```

TURN credentials tidak disarankan untuk disimpan di dalam public repository.

Untuk peer discovery, Amaca memakai empat Nostr relay secara default. Daftarnya dapat diganti melalui `window.RSA_CHAT_NOSTR_RELAYS` sebelum module script jika suatu relay tidak dapat dijangkau:

```html
<script>
    window.RSA_CHAT_NOSTR_RELAYS = [
        "wss://nos.lol",
        "wss://relay.primal.net",
        "wss://relay.snort.social",
        "wss://nostr.wine"
    ];
</script>
```

Participant harus memakai application identifier, room identifier, dan konfigurasi relay yang sama. Jika room ditemukan tetapi status menunjukkan **could not connect to peer**, jaringan atau firewall mungkin memblokir koneksi WebRTC langsung. Dalam kondisi tersebut, direct peer connection tidak dapat dijamin; siapkan TURN server dan konfigurasikan credentials secara lokal sebelum module script. Hindari memasukkan credentials ke repository publik. Tanpa TURN yang dapat dijangkau kedua participant, aplikasi tidak dapat menjamin koneksi pada semua jenis jaringan.
