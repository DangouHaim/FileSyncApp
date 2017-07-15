package com.example.dangou.filesync;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.dangou.filesync.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Button startBtn;
    TextView outText;
    EditText ipET;
    String serverIP = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        init();
    }

    private void init() {
        outText = (TextView) findViewById(R.id.outText);
        startBtn = (Button)findViewById(R.id.startBtn);
        ipET = (EditText) findViewById(R.id.ipET);

        initListeners();
    }

    private void initListeners() {
        startBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.startBtn:
                startBtnClick(view);
                break;
        }
    }

    private void startBtnClick(View view) {
        try {
            serverIP = ipET.getText().toString();
            recData(serverIP);
        }
        catch (Exception ex) {}
    }

    private String getLocalIP(String[] ips) {
        String res = "127.0.0.1";
        for(int i = 0; i < ips.length; i++)
        {
            if(ips[i].indexOf(("192.168.")) > -1) {
                res = ips[i];
            }
        }
        return res;
    }

    private void recData(final String ip) {
        String localIP = getLocalIP(getAddresses().split("\n"));

        if(localIP == "127.0.0.1") {
            localIP = getAddresses().split("\n")[3];
            for (String ips : getAddresses().split("\n")) {
                show(ips);
                sendData(ips.getBytes(), serverIP);
                try {
                    Thread.sleep(10000);
                }
                catch (Exception ex) {}
            }
        }
        else {
            show(localIP);
            sendData(localIP.getBytes(), serverIP);
        }

        final String fLocalIP = localIP;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1);
                        ServerSocket s = new ServerSocket(25566);

                        while (true) {
                            Thread.sleep(1);
                            Socket c = s.accept();

                            byte[] data = new byte[1024];
                            c.getInputStream().read(data);

                            String recCommand = "get-ip";
                            recCommand = new String(data, StandardCharsets.UTF_8);

                            if(recCommand.indexOf("[command]get-ip") > -1) {
                                sendData(fLocalIP.getBytes(), serverIP);
                            }
                            if(recCommand.indexOf("[command]root") > -1) {
                                sendData(getFiles("/").getBytes(), serverIP);
                            }
                            if(recCommand.indexOf("[command]dir") > -1) {
                                sendData(getFiles(recCommand.split(":")[1].trim()).getBytes(), serverIP);
                            }
                            if(recCommand.indexOf("[command]download-dir") > -1) {
                                sendData(getDirectoryFiles(recCommand.split(":")[1].trim(), true).getBytes(), serverIP);
                            }
                            else {
                                if(recCommand.indexOf("[command]download") > -1) {
                                    try {
                                        downloadFile(recCommand.split(":")[1].trim(), serverIP);
                                    }
                                    catch (Exception ex) {}
                                }
                            }
                            if(recCommand.indexOf("[command]sdcard") > -1) {
                                sendData(getFiles(Environment.getExternalStorageDirectory().getAbsolutePath()).getBytes(), serverIP);
                            }
                            if(recCommand.indexOf("[command]sd") > -1) {
                                sendData(getFiles(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +
                                        recCommand.split(":")[1].trim()).getBytes(), serverIP);
                            }
                            if(recCommand.indexOf("[command]upload") > -1) {
                                String[] fdata = recCommand.split(":");
                                uploadFile(fdata[1].trim(), fdata[2].trim(), serverIP);
                            }
                            if(recCommand.indexOf("[command]delete") > -1) {
                                delFile(recCommand.split(":")[1].trim());
                            }
                        }
                    }
                    catch (Exception ex) {

                    }
                }
            }
        }).start();
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    private String getSize(long fileSize) {
        String size = "" + fileSize + " B";
        if(fileSize > Math.pow(2, 30)) {
            size = round((double)fileSize / Math.pow(2, 30), 3) + " GB";
        }
        else {
            if(fileSize > Math.pow(2, 20)) {
                size = round((double)fileSize / Math.pow(2, 20), 3) + " MB";
            }
            else {
                if(fileSize > Math.pow(2, 10)) {
                    size = round((double)fileSize / Math.pow(2, 10), 3) + " KB";
                }
            }
        }
        return size;
    }

    private String getFiles(String root) {
        File f = new File(root);
        File[] files = f.listFiles();
        String res = root + "[filelist]\n";

        for (File inFile : files) {
            if(inFile.isFile()) {

                res += "file : size : " + getSize(inFile.length()) + " : " + inFile.toString() + "\n";
            }
            else {
                res += "folder : " + inFile.toString() + "\n";
            }
        }
        return res;
    }

    private String getDirectoryFiles(String root, boolean first) {
        File f = new File(root);
        File[] files = f.listFiles();
        String res = "";
        if(first) {
            res = root + "[downloadfilelist]\n";
        }

        for (File inFile : files) {
            if(inFile.isFile()) {

                res += "file : size : " + getSize(inFile.length()) + " : " + inFile.toString() + "\n";
            }
            else {
                res += getDirectoryFiles(inFile.toString(), false);
            }
        }
        return res;
    }

    void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    private void delFile(String name) {
        sendData(("delete : " + name).getBytes(), serverIP);
        deleteRecursive(new File(name));
    }

    private void sendData(final byte[] data, final String ip) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket(ip, 25565);
                    s.getOutputStream().write(data);
                    s.getOutputStream().flush();
                    s.close();
                }
                catch (Exception ex) {
                    show(ex.getMessage());
                }
            }
        }).start();
    }

    private void downloadFile(final String fileName, final String ip) {
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        String[] fileParts = fileName.split("/");
                        String Name = fileParts[fileParts.length - 1];
                        File f = new File(fileName);
                        sendData(("download : " + Name + " : " + f.length()).getBytes(), serverIP);

                        InputStream in = new FileInputStream(f);
                        byte[] fdata = new byte[16 * 1024 * 1024];
                        int count = 0;
                        Thread.sleep(10);
                        Socket s = new Socket(ip, 25567);
                        s.setSendBufferSize(16 * 1024 * 1024);

                        while((count = in.read(fdata)) > 0) {
                            s.getOutputStream().write(fdata, 0, count);
                        }
                        s.getOutputStream().flush();
                        s.close();
                    }
                    catch (Exception ex) {
                        show(ex.getMessage());
                    }
                }
            }).start();
        }
        catch (Exception ex) {}
    }

    private void uploadFile(final String dir, final String fileName, final String ip) {
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File f = new File(dir, fileName);
                        f.createNewFile();
                        sendData(("upload : " + fileName).getBytes(), serverIP);

                        OutputStream out = new FileOutputStream(f);
                        byte[] fdata = new byte[16 * 1024 * 1024];
                        int count = 0;

                        Thread.sleep(10);
                        Socket s = new Socket(ip, 25568);
                        s.setReceiveBufferSize(16 * 1024 * 1024);

                        while((count = s.getInputStream().read(fdata)) > 0) {
                            out.write(fdata, 0, count);
                        }
                        s.getOutputStream().flush();
                        s.close();
                    }
                    catch (Exception ex) {
                        show(ex.getMessage());
                    }
                }
            }).start();
        }
        catch (Exception ex) {}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        menu.setGroupVisible(R.id.main_menu_group, false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        show(item.getTitle().toString());
        return super.onOptionsItemSelected(item);
    }

    private void show(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        outText.setText(text);
    }

    private String getAddresses() {
        String res = "";
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                Enumeration<InetAddress> enumIpAddr2 = intf.getInetAddresses();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    res += inetAddress.toString().split("/")[1] + "\n";
                }
            }
        }
        catch (Exception ex) {
            show(ex.getMessage());
        }
        return res;
    }
}
