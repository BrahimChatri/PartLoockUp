# PartLookUp  📦🔍

**PartLookUp** is an Android application that allows users to quickly search for parts and retrieve their corresponding EMP locations via barcode scanning or manual input. It supports data uploads via both `.csv` and `.xlsx` files and is optimized for warehouse or manufacturing environments.

---

## 🚀 Features

- 📁 **CSV & XLSX Upload Support**
  - **CSV Format:** Must contain headers: `PartNumber,EMP_Location`
  - **XLSX Format:** Must contain headers in the first row:
    - `Partnumber` (e.g., `P00012345` or `412345678`)
    - `Harmoniser` (new reference only for old parts starting with `4`)
    - `EMP` (EMP location for the part)

- 🔍 **Search Function**
  - Enter part number manually to get EMP location.
  
- 📷 **Barcode Scanner**
  - Handles formats like `P00012345`, `PP00012345`, and `P412345678`.
  - Converts older references (starting with `4`) using the `Harmoniser` column in `.xlsx` files.

- ⚠️ **Error Handling**
  - Displays "Part Not Found" if the scanned/entered part number is not in the database.

---

## 📂 File Handling Logic

### CSV
- Header: `PartNumber,EMP_Location`
- Scanned part number is matched directly after handling format (e.g., trimming `P`, `PP` as needed).

### XLSX
- Header Row: `Partnumber | Harmoniser | EMP`
- If part starts with `4`, app fetches its new reference from the `Harmoniser` column.
- Otherwise, matches the part number directly to retrieve its EMP.

---

## 📷 Barcode Input Examples

| Scanned Value   | Interpreted As | Action                                           |
|-----------------|----------------|--------------------------------------------------|
| `PP00012345`    | `P00012345`    | Looks up as-is                                  |
| `P00012345`     | `P00012345`    | Looks up as-is                                  |
| `P412345678`    | `412345678`    | Looks up `412345678`, converts using `Harmoniser`|

---

## 🧪 Development

### Tech Stack
- Kotlin
- Jetpack Compose
- AndroidX
- MVVM Architecture

---

## 📦 Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/BrahimChatri/PartLoockUp.git
   cd PartLookUp-V2.0
   ```
2. Open the project in **Android Studio**.

3. Sync Gradle and build the project.

---

## 📄 License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

## 🤝 Contributions

Pull requests are welcome! If you have improvements or suggestions, feel free to open an issue or PR.


## 👨‍💻 Maintainer

**Brahim**
GitHub: [@BrahimChatri](https://github.com/BrahimChatri)

