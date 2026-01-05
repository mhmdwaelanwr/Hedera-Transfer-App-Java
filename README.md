# 🪙 Hedera Transfer App (Android)

This repository contains a mobile application developed in **Java** for the Android platform, designed to facilitate and execute **asset and cryptocurrency transfer operations** (such as HBAR and custom Tokens) on the decentralized **Hedera Hashgraph** network.

This application serves as a practical example and utility demonstrating how to build robust mobile financial applications that interact with the Hedera network, leveraging the **official Hedera Java SDK** for transaction creation and the **Hedera Transfer API** for secure backend processing.

## ✨ Key Features

*   **HBAR & Token Transfers**: Perform transfers of the native network currency (HBAR) and custom Hedera Token Service (HTS) tokens between accounts.
*   **Secure Account Management**: Add, manage, and switch between multiple Hedera accounts, with credentials stored securely.
*   **Hardware Wallet Integration**: Connect and import accounts from Ledger hardware wallets via USB, enabling enhanced security for transactions.
*   **Biometric Authentication**: Authorize transactions securely using biometric credentials (fingerprint, face ID) for added protection.
*   **QR Code Capabilities**: Easily scan QR codes for recipient account IDs and generate QR codes for receiving funds.
*   **Address Book**: Maintain a contact list of frequently used Hedera account IDs for quick and error-free transfers.
*   **Detailed Transaction History**: View a comprehensive list of past transactions, with options to filter, search, and export to CSV, JSON, or log formats.
*   **Real-time Exchange Rates**: Display current HBAR to USD exchange rates.
*   **In-app Blockchain Explorer**: Access the Hedera HashScan explorer directly within the app to view transaction details on-chain.
*   **Customizable Theming**: Switch between light and dark modes for a personalized user experience.
*   **Notification System**: Receive background notifications for transaction activities (configurable).
*   **Haptic Feedback**: Toggleable haptic feedback for interactive elements.
*   **Backend API Utilization**: Built to interact with a dedicated Backend Transfer API for core logic execution and secure transaction handling.

## 🏗️ Backend/API Dependency

This application is built to interact with and utilize a dedicated Backend Transfer API for core logic execution.

*   **API Repository**: [Hedera Transfer API](https://github.com/Mohammed-Ehap-Ali-Zean-Al-Abdin/Hedera-Transfer-API.git)
*   **Role**: This external API handles the complex processing and secure generation of transactions before execution on the Hedera network, abstracting Hedera SDK complexities from the mobile client. The API endpoint used by this application is `https://mlsa-hedera-transfer-api.vercel.app`.

## 🛠️ Tech Stack

*   **Language**: Java 8
*   **Platform**: Android
*   **Blockchain SDK**: Hedera Hashgraph SDK (Java)
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **UI/UX Frameworks**:  Android Jetpack (AppCompat, Material Design)  ConstraintLayout  SwipeRefreshLayout
*   **Networking**:  OkHttp3  Volley (for some API calls)  Hedera Mirror Node API (for history and exchange rates)
*   **Data Serialization**: Google Gson
*   **Image Loading**: Glide
*   **QR Code Handling**:  [Code Scanner](https://github.com/yuriy-budiyev/code-scanner)  [ZXing Core](https://github.com/zxing/zxing)
*   **Background Processing**: Android WorkManager
*   **Security**:  Android Biometric (for authentication)  AndroidX Security Crypto (for secure data storage)
*   **Hardware Connectivity**:  [USB Serial for Android](https://github.com/mik3y/usb-serial-for-android) (for Ledger hardware wallet integration)
*   **Logging**: Timber
*   **Utility Library**: Google Guava

## 🚀 Installation

To build and run this Android application locally, you will need the following:

### Prerequisites

*   **Java Development Kit (JDK):** Version 17 or newer.
*   **Android Studio:** Latest stable version recommended.
*   **Android SDK:** API Level 26 or higher.
*   **Hedera Account:** An active Hedera account ID and Private Key (for Testnet or Mainnet) if you plan to use software accounts.

### Steps

1.  **Clone the Repository:**`git clone https://github.com/mhmdwaelanwr/Hedera-Transfer-App.git
cd Hedera-Transfer-App
`
2.  **Open in Android Studio:**  Launch Android Studio.  Select `File > Open...` and navigate to the `Hedera-Transfer-App` directory you just cloned.  Android Studio will automatically sync the Gradle project. Wait for the process to complete.
3.  **Build and Run:**  Connect an Android device via USB or start an Android Virtual Device (AVD).  Click the "Run" button (green triangle) in Android Studio's toolbar.  The application will be built and installed on your selected device or emulator.**Note:** This application requires an internet connection to interact with the Hedera network and the backend API.

## 💡 Usage Guide

Once the app is installed and launched:

1.  **Welcome Screen**:  If no accounts are saved, you will be prompted to either **Login** with an existing Hedera Account ID and Private Key (which will be securely stored) or **Connect Hardware Wallet** to import an account from a compatible device.  If accounts are saved, it will display a "Welcome Back" message.
2.  **Add/Manage Accounts**:  From the Welcome screen, use "Login" to add a new software account using its Account ID and Private Key.  Use "Connect Hardware Wallet" to scan for and import accounts from a USB-connected Ledger device.  In the `Settings` menu, you can switch between accounts, add new ones, or delete existing accounts.
3.  **Perform a Transfer (Send HBAR/Tokens)**:  Navigate to the "Send" section (typically the main screen or a dedicated button).  **Recipient**: Enter the recipient's Hedera Account ID manually, use the QR scanner, or select from your Address Book. The app will verify the account ID.  **Amount**: Enter the amount of HBAR or custom token to send.  **Memo**: Optionally add a memo to your transaction.  **Confirm**: Review the transaction details and confirm.  **Authenticate**: If enabled, use biometric authentication or confirm on your hardware wallet.  A success screen will show the transaction ID and a link to HashScan.
4.  **Receive Funds**:  Go to the "Receive" section to view your current Hedera Account ID and its corresponding QR code.  You can copy your Account ID to the clipboard or share the QR code image with others.
5.  **View Transaction History**:  Access the "History" section to see a list of your past transactions.  Filter transactions by date, amount, or party.  Export your history to CSV, JSON, or plain text.
6.  **Settings**:  Customize app theme (Light/Dark).  Toggle notifications and haptic feedback.  Manage your saved Hedera accounts (add, switch, delete).  Configure export settings for transaction history.

## 📁 Project Structure

The project follows a standard Android application structure:

```
Hedera-Transfer-App/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/anwar/mlsa/hadera/aou/    # Main Java source code
│   │   │   │   ├── data/repository/          # Data layer: Repository implementations
│   │   │   │   ├── domain/use_case/          # Domain layer: Business logic use cases
│   │   │   │   ├── hardware/                 # Hardware wallet integration logic
│   │   │   │   ├── network/                  # Network request and API configuration
│   │   │   │   ├── parser/                   # JSON parsing utilities for API responses
│   │   │   │   ├── viewmodel/                # ViewModel implementations for UI logic
│   │   │   │   ├── MyApplication.java        # Custom Application class
│   │   │   │   ├── MainActivity.java         # Account login/setup
│   │   │   │   ├── TransferActivity.java     # Main wallet screen
│   │   │   │   ├── IdpayActivity.java        # Send/transfer funds screen
│   │   │   │   ├── HistoryActivity.java      # Transaction history view
│   │   │   │   ├── SettingsActivity.java     # Application settings
│   │   │   │   ├── ... (other activities, utils)
│   │   │   ├── res/                          # Android resources (layouts, drawables, values, etc.)
│   │   │   ├── AndroidManifest.xml           # Application manifest
│   │   └── build.gradle.kts                  # Module-level Gradle build configuration
│   ├── .gitignore
│   └── ... (other module-level files)
├── .gitignore                                # Git ignore file
├── LICENSE                                   # Project license information
├── README.md                                 # This README file
└── ... (other root-level files)

```

## 🤝 Contributing

Contributions are welcome! If you have suggestions, feature ideas, or bug fixes, please follow these steps:

1.  **Fork** the repository.
2.  Create a new feature branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a **Pull Request**.

Please ensure your code adheres to the project's coding style and includes appropriate tests.

## 📄 License

This project is licensed under the **MIT License**.

See the [LICENSE](LICENSE) file for full details regarding usage and distribution rights.

---

## © 2025 Mohamed Anwar
