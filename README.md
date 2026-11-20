# Teksto

Teksto adalah editor teks untuk Android yang dibuat dengan Kotlin, Jetpack Compose, dan Material 3. Kamu bisa membuat dan mengedit beberapa berkas teks dalam satu sesi, lalu menyimpannya lewat pemilih berkas sistem Android.

## Fitur

- Buat, buka, dan simpan berkas teks UTF-8. Gunakan **Save as** untuk menyimpan salinan dengan nama atau lokasi baru.
- Buka menu samping untuk mengakses tab, berkas terbaru, dan pengaturan editor.
- Lanjutkan sesi sebelumnya dengan tab, posisi kursor, dan draf yang dipulihkan. Tanda titik pada tab menandai perubahan yang belum disimpan. Saat menutup tab tersebut, pilih **Save**, **Discard**, atau **Cancel**.
- Temukan teks di dokumen aktif dengan sorotan, penghitung hasil, serta tombol sebelum dan sesudah. Gunakan **Replace one** atau **Replace all** untuk mengganti teks.
- Urungkan atau ulangi perubahan di tiap tab. **Replace all** dapat diurungkan dalam satu langkah.
- Gunakan editor bertema gelap dengan nomor baris, font monospace, ukuran teks 11 hingga 22 sp, **Word wrap**, dan **Go to line**.
- Tampilkan penyorotan sintaks JSON, Markdown, HTML, dan XML. Gunakan **Validate JSON** atau **Format JSON** dari menu samping.
- Ketuk ikon pratinjau untuk melihat hasil render Markdown dan HTML atau tampilan JSON yang dirapikan. Pratinjau HTML dan Markdown memakai konten setempat dengan JavaScript dan akses jaringan dinonaktifkan.
- Gunakan bilah pemformatan untuk menambahkan tebal, miring, garis bawah, coret, ukuran, dan warna teks di Markdown atau HTML. Kontrol indentasi juga tersedia untuk teks biasa.
- Bilah pemformatan tampil di bawah area editor secara default. Ubah posisinya lewat **Editor settings** → **Formatting toolbar position**.

Teksto menyimpan draf pemulihan di penyimpanan aplikasi. Ketuk **Save** untuk menulis perubahan ke berkas yang sedang diedit. Teksto memuat seluruh isi berkas ke memori, sehingga berkas yang sangat besar dapat menggunakan banyak RAM.

## Menjalankan proyek

1. Buka folder proyek di Android Studio, lalu instal Android SDK 35 lewat **SDK Manager**.
2. Di pengaturan Gradle Android Studio, pilih JDK 17 dan instalasi lokal Gradle 8.9 karena proyek ini belum menyertakan Gradle Wrapper. [Kompatibilitas Android Gradle Plugin 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes#compatibility)
3. Sinkronkan Gradle, lalu jalankan modul `app` di emulator atau perangkat dengan Android 8.0 (API 26) atau lebih baru.
