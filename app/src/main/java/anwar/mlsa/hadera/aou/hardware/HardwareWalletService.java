package anwar.mlsa.hadera.aou.hardware;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class HardwareWalletService extends Service implements SerialInputOutputManager.Listener {

    private static final String TAG = "HardwareWalletService";
    private static final String ACTION_USB_PERMISSION = "anwar.mlsa.hadera.aou.USB_PERMISSION";
    private static final int LEDGER_VID = 11415; // Vendor ID for Ledger
    private static final int TIMEOUT_MS = 20000;

    // APDU constants based on https://github.com/hashgraph/hedera-ledger-app/blob/main/src/hedera/handlers.h
    private static final byte CLA = (byte) 0xE0;
    private static final byte INS_GET_APP_CONFIGURATION = 0x01;
    private static final byte INS_GET_PUBKEY = 0x02;
    private static final byte INS_SIGN_TX = 0x04;

    public enum ConnectionStatus {DISCONNECTED, SEARCHING, CONNECTED, ERROR}
    private enum PendingOperation {NONE, GET_ACCOUNT, SIGN_TRANSACTION}

    private final IBinder binder = new LocalBinder();
    private UsbManager usbManager;
    private UsbSerialPort usbSerialPort;
    private SerialInputOutputManager serialInputOutputManager;

    private HardwareWalletListener signingListener;
    private AccountInfoListener accountInfoListener;
    private PendingOperation currentOperation = PendingOperation.NONE;
    private int activeAccountIndex = -1;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable operationTimeoutRunnable;

    private final MutableLiveData<ConnectionStatus> _connectionStatus = new MutableLiveData<>(ConnectionStatus.DISCONNECTED);
    public final LiveData<ConnectionStatus> connectionStatus = _connectionStatus;

    public interface HardwareWalletListener {
        void onSignatureReceived(byte[] signature);
        void onSignatureError(Exception e);
    }

    public interface AccountInfoListener {
        void onAccountInfoReceived(int accountIndex, String accountId);
        void onAccountInfoError(int accountIndex, Exception e);
    }

    public class LocalBinder extends Binder {
        public HardwareWalletService getService() {
            return HardwareWalletService.this;
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) connectToDevice(device);
                    } else {
                        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        unregisterReceiver(usbReceiver);
    }

    public void findAndConnectToDevice() {
        if (_connectionStatus.getValue() == ConnectionStatus.CONNECTED || _connectionStatus.getValue() == ConnectionStatus.SEARCHING) return;
        _connectionStatus.setValue(ConnectionStatus.SEARCHING);
        mainHandler.postDelayed(() -> {
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (availableDrivers.isEmpty()) {
                _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
                return;
            }
            for (UsbSerialDriver driver : availableDrivers) {
                UsbDevice device = driver.getDevice();
                if (device.getVendorId() == LEDGER_VID) {
                    if (usbManager.hasPermission(device)) {
                        connectToDevice(device);
                    } else {
                        PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                        usbManager.requestPermission(device, pi);
                    }
                    return;
                }
            }
            _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
        }, 100);
    }

    private void connectToDevice(UsbDevice device) {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            _connectionStatus.postValue(ConnectionStatus.ERROR);
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null || driver.getPorts().isEmpty()) {
            _connectionStatus.postValue(ConnectionStatus.ERROR);
            return;
        }
        usbSerialPort = driver.getPorts().get(0);
        try {
            usbSerialPort.open(connection);
            usbSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            serialInputOutputManager = new SerialInputOutputManager(usbSerialPort, this);
            Executors.newSingleThreadExecutor().submit(serialInputOutputManager);
            _connectionStatus.postValue(ConnectionStatus.CONNECTED);
        } catch (IOException e) {
            disconnect();
        }
    }

    public void disconnect() {
        cancelOperationTimeout();
        if (serialInputOutputManager != null) {
            serialInputOutputManager.stop();
            serialInputOutputManager = null;
        }
        if (usbSerialPort != null) {
            try {
                usbSerialPort.close();
            } catch (IOException ignored) {}
            usbSerialPort = null;
        }
        clearListeners();
        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
    }

    public void signTransaction(byte[] unsignedTransaction, HardwareWalletListener listener) {
        if (_connectionStatus.getValue() != ConnectionStatus.CONNECTED) {
            listener.onSignatureError(new IOException("Not connected"));
            return;
        }
        this.signingListener = listener;
        this.currentOperation = PendingOperation.SIGN_TRANSACTION;
        try {
            usbSerialPort.write(createSignTransactionApdu(unsignedTransaction), TIMEOUT_MS);
            startOperationTimeout();
        } catch (IOException e) {
            listener.onSignatureError(e);
        }
    }

    public void requestAccountInfo(int accountIndex, AccountInfoListener listener) {
        if (_connectionStatus.getValue() != ConnectionStatus.CONNECTED) {
            listener.onAccountInfoError(accountIndex, new IOException("Not connected"));
            return;
        }
        this.accountInfoListener = listener;
        this.currentOperation = PendingOperation.GET_ACCOUNT;
        this.activeAccountIndex = accountIndex;
        try {
            usbSerialPort.write(createGetPublicKeyApdu(accountIndex), TIMEOUT_MS);
            startOperationTimeout();
        } catch (IOException e) {
            listener.onAccountInfoError(accountIndex, e);
        }
    }

    private void startOperationTimeout() {
        cancelOperationTimeout();
        final String message = currentOperation == PendingOperation.GET_ACCOUNT ? "Request for account info timed out." : "Signing timed out.";
        operationTimeoutRunnable = () -> {
            Exception e = new TimeoutException(message);
            handleError(e);
        };
        mainHandler.postDelayed(operationTimeoutRunnable, TIMEOUT_MS);
    }

    private void cancelOperationTimeout() {
        if (operationTimeoutRunnable != null) {
            mainHandler.removeCallbacks(operationTimeoutRunnable);
            operationTimeoutRunnable = null;
        }
    }

    private void clearListeners() {
        signingListener = null;
        accountInfoListener = null;
        currentOperation = PendingOperation.NONE;
        activeAccountIndex = -1;
    }

    private byte[] createSignTransactionApdu(byte[] transaction) {
        byte[] header = {CLA, INS_SIGN_TX, 0x00, 0x00, (byte) transaction.length};
        byte[] apdu = new byte[header.length + transaction.length];
        System.arraycopy(header, 0, apdu, 0, header.length);
        System.arraycopy(transaction, 0, apdu, header.length, transaction.length);
        return apdu;
    }

    private byte[] createGetPublicKeyApdu(int accountIndex) {
        // Real path is 44'/3030'/accountIndex'
        // This requires a more complex serialization of the path components.
        // This is a simplified simulation.
        ByteBuffer path = ByteBuffer.allocate(4);
        path.putInt(accountIndex);
        byte[] pathBytes = path.array();

        byte[] header = {CLA, INS_GET_PUBKEY, 0x40, 0x00, (byte) pathBytes.length}; 
        byte[] apdu = new byte[header.length + pathBytes.length];
        System.arraycopy(header, 0, apdu, 0, header.length);
        System.arraycopy(pathBytes, 0, apdu, header.length, pathBytes.length);
        return apdu;
    }

    @Override
    public void onNewData(byte[] data) {
        cancelOperationTimeout();
        try {
            switch (currentOperation) {
                case SIGN_TRANSACTION:
                    if (signingListener != null) signingListener.onSignatureReceived(data);
                    break;
                case GET_ACCOUNT:
                    if (accountInfoListener != null) {
                        // TODO: Proper parsing of the response which includes public key, chain code, etc.
                        // For now, we assume the response is the account ID string which is a simplification.
                        String accountId = new String(data, StandardCharsets.UTF_8).trim();
                        accountInfoListener.onAccountInfoReceived(activeAccountIndex, accountId);
                    }
                    break;
            }
        } finally {
            clearListeners();
        }
    }

    @Override
    public void onRunError(Exception e) {
        handleError(e);
    }

    private void handleError(Exception e) {
        cancelOperationTimeout();
        switch (currentOperation) {
            case SIGN_TRANSACTION:
                if (signingListener != null) signingListener.onSignatureError(e);
                break;
            case GET_ACCOUNT:
                if (accountInfoListener != null) accountInfoListener.onAccountInfoError(activeAccountIndex, e);
                break;
        }
        clearListeners();
        disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
