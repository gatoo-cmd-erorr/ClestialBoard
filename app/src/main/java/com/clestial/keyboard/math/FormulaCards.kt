package com.clestial.keyboard.math

/**
 * Berkas data Kartu Rumus Matematika SMP Kelas 9.
 * Menyediakan ringkasan rumus inti per modul termasuk rumus lengkap Modul 10 & Modul 11
 * (gradien, persamaan garis, sejajar, tegak lurus, titik potong sumbu, dan sifat tanda pertidaksamaan).
 */
data class FormulaCard(
    val id: String,
    val moduleId: String,
    val title: String,
    val formula: String,
    val notes: String
)

object FormulaCards {

    val cards: List<FormulaCard> = listOf(
        // ---- Modul 1: SPLDV ----
        FormulaCard(
            id = "spldv_umum",
            moduleId = "spldv",
            title = "Metode Gabungan SPLDV",
            formula = "a₁x + b₁y = c₁  &  a₂x + b₂y = c₂\nEliminasi y → cari x → substitusi → cari y",
            notes = "D = a₁b₂ - a₂b₁ = 0: berimpit (tak hingga solusi) bila rasio c sama, atau sejajar (tak ada solusi) bila berbeda."
        ),
        // ---- Modul 2: Pangkat & Akar ----
        FormulaCard(
            id = "pangkat_sifat",
            moduleId = "pangkat_akar",
            title = "Sifat Pangkat, Akar & Rasionalisasi",
            formula = "aᵐ × aⁿ = aᵐ⁺ⁿ | aᵐ : aⁿ = aᵐ⁻ⁿ | (aᵐ)ⁿ = aᵐⁿ | a⁻ⁿ = 1/aⁿ\n(a / (b + √c)) × ((b - √c) / (b - √c))",
            notes = "Notasi ilmiah: a × 10ⁿ dengan 1 ≤ |a| < 10. √(p²·q) = p√q."
        ),
        // ---- Modul 3: Persamaan Kuadrat ----
        FormulaCard(
            id = "kuadrat_abc",
            moduleId = "persamaan_kuadrat",
            title = "Diskriminan & Rumus ABC",
            formula = "D = b² - 4ac\nx₁,₂ = (-b ± √D) / (2a)\nx₁ + x₂ = -b/a   |   x₁·x₂ = c/a",
            notes = "D > 0: 2 akar real; D = 0: akar kembar; D < 0: tidak ada akar real."
        ),
        // ---- Modul 4: Fungsi Kuadrat ----
        FormulaCard(
            id = "fungsi_kuadrat_puncak",
            moduleId = "fungsi_kuadrat",
            title = "Sumbu Simetri & Titik Puncak Parabola",
            formula = "y = ax² + bx + c\nxₚ = -b / (2a)   |   yₚ = -D / (4a)",
            notes = "a > 0 terbuka ke atas (nilai minimum); a < 0 terbuka ke bawah (nilai maksimum). Potong sumbu-y di (0, c)."
        ),
        // ---- Modul 10: Persamaan Garis Lurus & Fungsi Linear ----
        FormulaCard(
            id = "garis_gradien",
            moduleId = "garis_linear",
            title = "Gradien Garis Lurus (m)",
            formula = "Dua titik (x₁, y₁) & (x₂, y₂): m = (y₂ - y₁) / (x₂ - x₁)\nBentuk ax + by + c = 0 → m = -a / b\nBentuk y = mx + c → gradien langsung = m",
            notes = "Jika x₁ = x₂ (atau b = 0): garis vertikal x = k, gradien tidak terdefinisi. Jika y₁ = y₂: garis horizontal, m = 0."
        ),
        FormulaCard(
            id = "garis_persamaan",
            moduleId = "garis_linear",
            title = "Persamaan Garis Lurus",
            formula = "Melalui 1 titik (x₁, y₁) bergradien m:\n  y - y₁ = m(x - x₁)\nMelalui 2 titik:\n  (y - y₁)/(y₂ - y₁) = (x - x₁)/(x₂ - x₁)",
            notes = "Bentuk eksplisit y = mx + c dapat diubah ke bentuk umum ax + by + c = 0 dengan koefisien bulat dan a > 0."
        ),
        FormulaCard(
            id = "garis_sejajar_tegak_lurus",
            moduleId = "garis_linear",
            title = "Hubungan Dua Garis (Sejajar & Tegak Lurus)",
            formula = "Sejajar: m₁ = m₂\nTegak lurus: m₁ × m₂ = -1  (atau m₂ = -1 / m₁)",
            notes = "Garis horizontal (m = 0) selalu tegak lurus dengan garis vertikal x = k (m tak terdefinisi)."
        ),
        FormulaCard(
            id = "garis_potong_sumbu",
            moduleId = "garis_linear",
            title = "Titik Potong Sumbu & Fungsi Linear",
            formula = "Potong sumbu-x (y = 0): (-c/a, 0)\nPotong sumbu-y (x = 0): (0, -c/b)\nFungsi linear: f(x) = ax + b",
            notes = "Untuk dua nilai f(p) dan f(q): a = (f(q) - f(p)) / (q - p) dan b = f(p) - a·p. Perpotongan 2 garis diselesaikan lewat SPLDV."
        ),
        // ---- Modul 11: PLSV & Pertidaksamaan Linear ----
        FormulaCard(
            id = "plsv_aturan",
            moduleId = "plsv_ptlsv",
            title = "Persamaan Linear Satu Variabel (PLSV)",
            formula = "ax + b = cx + d  →  (a - c)x = d - b  →  x = (d - b)/(a - c)",
            notes = "Jika ada kurung, buka dulu dengan sifat distributif k(x + p) = kx + kp. Kasus 0x = 0: semua bilangan real; 0x = k (k ≠ 0): tidak ada solusi."
        ),
        FormulaCard(
            id = "ptlsv_tanda",
            moduleId = "plsv_ptlsv",
            title = "Sifat Tanda Pertidaksamaan Linear (PtLSV)",
            formula = "Jika -ax > b (dibagi/dikali bilangan negatif) → TANDA DIBALIK: x < -b/a\n< berubah ↔ >   |   ≤ berubah ↔ ≥",
            notes = "Garis bilangan: bulatan kosong (o) untuk < atau > (batas tak ikut); bulatan penuh (●) untuk ≤ atau ≥ (batas ikut)."
        ),
        // ---- Modul 5: Transformasi ----
        FormulaCard(
            id = "transformasi_dasar",
            moduleId = "transformasi",
            title = "Rumus Transformasi Geometri",
            formula = "Translasi T(a,b): (x+a, y+b) | Dilatasi [O,k]: (kx, ky)\nRefleksi sb-x: (x,-y) | sb-y: (-x,y) | y=x: (y,x) | x=h: (2h-x,y)\nRotasi 90° CCW: (-y,x) | 180°: (-x,-y) | 90° CW: (y,-x)",
            notes = "Untuk pusat (p, q): geser titik dikurangi (p, q), transformasikan terhadap O, lalu tambah (p, q)."
        ),
        // ---- Modul 6: Kesebangunan ----
        FormulaCard(
            id = "kesebangunan_rasio",
            moduleId = "kesebangunan",
            title = "Syarat Sebangun & Kongruen",
            formula = "Sebangun: a₁/a₂ = b₁/b₂ = c₁/c₂ = k\nPerbandingan senilai: a/b = c/x → x = (b·c)/a",
            notes = "Kekongruenan segitiga memenuhi SSS, SAS, atau ASA serta ketaksamaan segitiga (jumlah dua sisi > sisi ketiga)."
        ),
        // ---- Modul 7: Bangun Ruang ----
        FormulaCard(
            id = "bangun_ruang_rumus",
            moduleId = "bangun_ruang",
            title = "Tabung, Kerucut, dan Bola",
            formula = "Tabung: V = πr²t | Ls = 2πrt | Lp = 2πr(r+t)\nKerucut: V = 1/3·πr²t | Ls = πrs | s² = r²+t²\nBola: V = 4/3·πr³ | L = 4πr²",
            notes = "Gunakan simbol π untuk hasil eksak, atau π = 22/7 jika r kelipatan 7."
        ),
        // ---- Modul 8: Statistika ----
        FormulaCard(
            id = "statistika_pusat",
            moduleId = "statistika",
            title = "Pemusatan & Penyebaran Data",
            formula = "Mean = Σxᵢ / n | Jangkauan = x_maks - x_min\nIQR = Q₃ - Q₁ (Q₁ median belahan bawah, Q₃ belahan atas)",
            notes = "Urutkan data terlebih dahulu sebelum mencari Q₁, Q₂ (median), Q₃, dan jangkauan."
        ),
        // ---- Modul 9: Peluang ----
        FormulaCard(
            id = "peluang_dasar",
            moduleId = "peluang",
            title = "Peluang, Komplemen & Frekuensi Harapan",
            formula = "P(A) = n(A) / n(S)   |   P(A') = 1 - P(A)\nFrekuensi harapan: Fh = P(A) × N",
            notes = "Nilai peluang selalu 0 ≤ P(A) ≤ 1."
        )
    )

    fun forModule(moduleId: String): List<FormulaCard> =
        cards.filter { it.moduleId == moduleId.trim() }

    fun all(): List<FormulaCard> = cards
}
