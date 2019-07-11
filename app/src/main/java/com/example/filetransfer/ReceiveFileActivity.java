package com.example.filetransfer;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;


import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.example.filetransfer.adapter.DeviceAdapter;
import com.example.filetransfer.broadcast.DirectBroadcastReceiver;
import com.example.filetransfer.callback.DirectActionListener;
import com.example.filetransfer.common.LoadingDialog;
import com.example.filetransfer.common.MessageDialog;
import com.example.filetransfer.model.FileTransfer;
import com.example.filetransfer.service.WifiServerService;
import com.example.filetransfer.util.DocumentsUtils;

public class ReceiveFileActivity extends BaseActivity implements DirectActionListener{

    private WifiP2pManager wifiP2pManager;

    private WifiP2pManager.Channel channel;

    private BroadcastReceiver broadcastReceiver;

    private WifiServerService wifiServerService;

    private ProgressDialog progressDialog;

    private boolean mWifiP2pEnabled = false;

    private List<WifiP2pDevice> wifiP2pDeviceList;

    private LoadingDialog loadingDialog;

    private DeviceAdapter deviceAdapter;

    private WifiP2pDevice mWifiP2pDevice;

    private Button applyWriteSDCardPerm;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WifiServerService.MyBinder binder = (WifiServerService.MyBinder) service;
            wifiServerService = binder.getService();
            wifiServerService.setProgressChangListener(progressChangListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            wifiServerService = null;
            bindService();
        }
    };

    private WifiServerService.OnProgressChangListener progressChangListener = new WifiServerService.OnProgressChangListener() {
        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final int progress) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.setMessage("文件名： " + new File(fileTransfer.getFilePath()).getName());
                    progressDialog.setProgress(progress);
                    progressDialog.show();
                }
            });
        }

        @Override
        public void onTransferFinished(final File file) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.cancel();
                    if (file != null && file.exists()) {
                        openFile(file.getPath());
                    }
                }
            });
        }
    };

    protected static final String TAG = "ReceiveFileActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_file);
        initView();
        wifiP2pManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        channel = wifiP2pManager.initialize(this, getMainLooper(), this);
        broadcastReceiver = new DirectBroadcastReceiver(wifiP2pManager, channel, this);
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.getIntentFilter());
        bindService();
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("正在接收文件");
        progressDialog.setMax(100);
    }

    private void initView() {
        loadingDialog = new LoadingDialog(this);
        RecyclerView rv_deviceList1 = findViewById(R.id.rv_deviceList1);
        wifiP2pDeviceList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(wifiP2pDeviceList);
        deviceAdapter.setClickListener(new DeviceAdapter.OnClickListener() {
            @Override
            public void onItemClick(int position) {
                mWifiP2pDevice = wifiP2pDeviceList.get(position);
                showToast(wifiP2pDeviceList.get(position).deviceName);
                //connect();
            }
        });
        rv_deviceList1.setAdapter(deviceAdapter);
        rv_deviceList1.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    public void onBackPressed() {
        final MessageDialog messageDialog = new MessageDialog();
        messageDialog.show(null, "退出当前界面将取消文件传输，是否确认退出？", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                messageDialog.dismiss();
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    ReceiveFileActivity.super.onBackPressed();
                }
            }
        }, getSupportFragmentManager());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wifiServerService != null) {
            wifiServerService.setProgressChangListener(null);
            unbindService(serviceConnection);
        }
        unregisterReceiver(broadcastReceiver);
        removeGroup();
        stopService(new Intent(this, WifiServerService.class));
    }

    @Override
    public void wifiP2pEnabled(boolean enabled) {
        mWifiP2pEnabled = enabled;
        Log.e(TAG, "wifiP2pEnabled: " + enabled);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        Log.e(TAG, "onConnectionInfoAvailable");
        Log.e(TAG, "isGroupOwner：" + wifiP2pInfo.isGroupOwner);
        Log.e(TAG, "groupFormed：" + wifiP2pInfo.groupFormed);
        if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
            if (wifiServerService != null) {
                startService(new Intent(this, WifiServerService.class));
            }
        }
    }

    @Override
    public void onDisconnection() {
        Log.e(TAG, "onDisconnection");
    }

    @Override
    public void onSelfDeviceAvailable(WifiP2pDevice wifiP2pDevice) {
        Log.e(TAG, "onSelfDeviceAvailable");
    }

//    @Override
//    public void onPeersAvailable(Collection<WifiP2pDevice> wifiP2pDeviceList) {
//        Log.e(TAG, "onPeersAvailable :" + wifiP2pDeviceList.size());
//        this.wifiP2pDeviceList.clear();
//        this.wifiP2pDeviceList.addAll(wifiP2pDeviceList);
//        deviceAdapter.notifyDataSetChanged();
//        loadingDialog.cancel();
//    }

    @Override
    public void onChannelDisconnected() {
        Log.e(TAG, "onChannelDisconnected");
    }

    public void createGroup(View view) {
        showLoadingDialog("正在创建群组");
        wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.e(TAG, "createGroup onSuccess");
                dismissLoadingDialog();
                showToast("onSuccess");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "createGroup onFailure: " + reason);
                dismissLoadingDialog();
                showToast("onFailure");
            }
        });
    }

//    public void createGroup(View view) {
//        if (!mWifiP2pEnabled) {
//            showToast("需要先打开Wifi");
//            return ;
//        }
//        loadingDialog.show("正在等待其他设备连接", true, false);
//        wifiP2pDeviceList.clear();
//        deviceAdapter.notifyDataSetChanged();
//        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
//            @Override
//            public void onSuccess() {
//                showToast("Success");
//            }
//
//            @Override
//            public void onFailure(int reasonCode) {
//                showToast("Failure");
//                loadingDialog.cancel();
//            }
//        });
//    }

    public void removeGroup(View view) {
        removeGroup();
    }

    private void bindService() {
        Intent intent = new Intent(ReceiveFileActivity.this, WifiServerService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void removeGroup() {
        wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.e(TAG, "removeGroup onSuccess");
                showToast("onSuccess");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "removeGroup onFailure");
                showToast("onFailure");
            }
        });
    }

    private void openFile(String filePath) {
        String ext = filePath.substring(filePath.lastIndexOf('.')).toLowerCase(Locale.US);
        try {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            String mime = mimeTypeMap.getMimeTypeFromExtension(ext.substring(1));
            mime = TextUtils.isEmpty(mime) ? "" : mime;
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(filePath)), mime);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "文件打开异常：" + e.getMessage());
            showToast("文件打开异常：" + e.getMessage());
        }
    }

    private void applyWriteSDCardPerm(){
        if (DocumentsUtils.checkWritableRootPath(this, Environment.DIRECTORY_DOCUMENTS)) {
            showOpenDocumentTree();
        }
    }
    private void showOpenDocumentTree() {
        Intent intent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            StorageManager sm = this.getSystemService(StorageManager.class);

            StorageVolume volume = sm.getStorageVolume(new File(Environment.DIRECTORY_DOCUMENTS));

            if (volume != null) {
                intent = volume.createAccessIntent(null);
            }
        }

        if (intent == null) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        }
        startActivityForResult(intent, DocumentsUtils.OPEN_DOCUMENT_TREE_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case DocumentsUtils.OPEN_DOCUMENT_TREE_CODE:
                if (data != null && data.getData() != null) {
                    Log.e(TAG, "apply perm success");
                    Uri uri = data.getData();
                    DocumentsUtils.saveTreeUri(this, Environment.DIRECTORY_DOCUMENTS, uri);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onPeersAvailable(Collection<WifiP2pDevice> wifiP2pDeviceList) {
        Log.e(TAG, "onPeersAvailable :" + wifiP2pDeviceList.size());
        this.wifiP2pDeviceList.clear();
        this.wifiP2pDeviceList.addAll(wifiP2pDeviceList);
        deviceAdapter.notifyDataSetChanged();
        loadingDialog.cancel();
    }
}
