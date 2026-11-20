# Teksto

Teksto adalah editor teks untuk Android yang dibuat dengan Kotlin, Jetpack Compose, dan Material 3. Kamu bisa membuat dan mengedit beberapa berkas teks dalam satu sesi, lalu menyimpannya lewat pemilih berkas sistem Android.

## Fitur

- Buat, buka, dan simpan berkas teks UTF-8.
- Edit beberapa dokumen dalam tab terpisah. Tanda titik pada tab menandai perubahan yang belum disimpan.
- Ganti semua kemunculan teks yang cocok di dokumen aktif lewat **Replace all**.
- Gunakan editor bertema gelap dengan nomor baris, font monospace, dan ukuran teks yang bisa diatur dari 11 hingga 22 sp.

Simpan perubahan sebelum menutup tab. Tab terbuka dan pengaturan editor hanya bertahan selama sesi. Teksto memuat seluruh isi berkas ke memori, sehingga berkas yang sangat besar dapat menggunakan banyak RAM.

## Menjalankan proyek

1. Buka folder proyek di Android Studio, lalu instal Android SDK 35 lewat **SDK Manager**.
2. Di pengaturan Gradle Android Studio, pilih JDK 17 dan instalasi lokal Gradle 8.9 karena proyek ini belum menyertakan Gradle Wrapper. [Kompatibilitas Android Gradle Plugin 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes#compatibility)
3. Sinkronkan Gradle, lalu jalankan modul `app` di emulator atau perangkat dengan Android 8.0 (API 26) atau lebih baru.
