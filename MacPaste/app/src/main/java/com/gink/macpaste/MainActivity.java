package com.gink.macpaste;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ejlchina.okhttps.HttpResult;
import com.ejlchina.okhttps.OkHttps;
import com.ejlchina.okhttps.WebSocket;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String fileName = "https://dav.jianguoyun.com/dav/DavPaste/DavPaste.txt";
    private static final String username = "这里填写你的账号";
    private static final String password = "这里填写你的密码";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(new HandlerCallback());
    private Context mContext;
    private ClipboardManager clipboard;
    private String sockUrl = null;
    private WebSocket webSocket = null;
    private TextView homeShow;
    private TextView homeUrl;
    private Button btnPush;

    public static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        homeShow = this.findViewById(R.id.homeShow);
        homeUrl = this.findViewById(R.id.homeUrl);
        btnPush = this.findViewById(R.id.btnPush);
        mContext = this;
        this.clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        btnPush.setOnClickListener(view -> executorService.execute(this::manualConnect));
        //handleShare();
    }

    private void handleShare() {
        Intent intent = getIntent();
        String action = intent.getAction();//获取Intent的Action
        String type = intent.getType();//获取Intent的Type

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            executorService.execute(() -> sendFile(uri));
        }
    }

    private void sendFile(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            byte[] bytes = toByteArray(inputStream);
            manualConnect();
            this.webSocket.send(bytes);
        } catch (Exception e) {
            Message msg = new Message();
            msg.what = -1;
            msg.obj = e.getMessage();

            handler.sendMessage(msg);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.webSocket != null
                && this.webSocket.status() != WebSocket.STATUS_CONNECTED) {
            executorService.execute(this::connect);
        }
    }


    private void manualConnect() {
        if (this.webSocket != null) {
            if (this.webSocket.status() == WebSocket.STATUS_CONNECTED) {
                return;
            }
            this.webSocket = null;
        }
        updateSocketUrl();
        connect();
    }

    private void connect() {
        if (this.sockUrl == null) {
            Message msg = new Message();
            msg.what = -1;
            msg.obj = "url为空，请手动连接";
            handler.sendMessage(msg);
            return;
        }
        this.webSocket = OkHttps.webSocket(this.sockUrl)
                .setOnMessage(new OnMessage())
                .setOnOpen(new OnOpen())
                .setOnException(new OnException())
                .listen();
    }


    private void updateSocketUrl() {
        String url = getUrlFromDav();
        this.sockUrl = url;

        //更新text
        Message msg = new Message();
        msg.what = 1;
        msg.obj = url;
        handler.sendMessage(msg);
    }

    public String getUrlFromDav() {
        Sardine sardine = new OkHttpSardine();
        sardine.setCredentials(username, password);
        try (InputStream ins = sardine.get(fileName);
             InputStreamReader isr = new InputStreamReader(ins);
             BufferedReader bf = new BufferedReader(isr)) {
            String str;
            while ((str = bf.readLine()) != null) {
                return str;
            }
        } catch (Exception e) {
            Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return null;
    }


    private class OnMessage implements WebSocket.Listener<WebSocket.Message> {

        @Override
        public void on(WebSocket ws, WebSocket.Message data) {
            if (!data.isText()) {
                return;
            }

            Message msg = new Message();
            msg.what = 2;
            msg.obj = data.toString();
            handler.sendMessage(msg);
        }
    }

    private class OnOpen implements WebSocket.Listener<HttpResult> {

        @Override
        public void on(WebSocket ws, HttpResult data) {
            Message msg = new Message();
            msg.what = -1;
            msg.obj = "connected";

            handler.sendMessage(msg);
        }
    }

    private class OnException implements WebSocket.Listener<Throwable> {

        @Override
        public void on(WebSocket ws, Throwable data) {
            Message msg = new Message();
            msg.what = -1;
            msg.obj = data.getMessage();

            handler.sendMessage(msg);
        }
    }

    private class HandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case -2: {
                    Toast.makeText(mContext, (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                }
                case -1: {
                    homeShow.setText((String) msg.obj);
                    break;
                }
                case 1: {
                    String url = (String) msg.obj;
                    homeUrl.setText(url);
                    break;
                }
                case 2: {
                    //粘贴来自mac的文本
                    String data = (String) msg.obj;
                    ClipData clipData = ClipData.newPlainText("simple text", data);
                    clipboard.setPrimaryClip(clipData);
                    Toast.makeText(mContext, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
            return false;
        }
    }
}