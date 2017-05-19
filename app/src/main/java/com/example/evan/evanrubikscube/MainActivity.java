package com.example.evan.evanrubikscube;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ListView;
import android.os.Handler;
import android.widget.Toast;
import android.widget.CheckBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private Button joinButton, leaveButton, gostopButton;
    private TextView scrambleText, timeText;
    private ListView membersListView;
    private EditText lobbyText, nameText;
    private CheckBox readyCheckBox;
    private long startTime = 0, time = 0;
    private Handler timerHandler;
    private Thread connectionThread;
    private boolean timing = false, buttonReady = false;
    private Socket socket;
    private ArrayAdapter<String> members;
    private long lastTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        joinButton = (Button) findViewById(R.id.joinbutton);
        leaveButton = (Button) findViewById(R.id.leavebutton);
        gostopButton = (Button) findViewById(R.id.gostopbutton);
        scrambleText = (TextView) findViewById(R.id.scramble);
        timeText = (TextView) findViewById(R.id.time);
        membersListView = (ListView) findViewById(R.id.members);
        lobbyText = (EditText) findViewById(R.id.lobbyText);
        nameText = (EditText) findViewById(R.id.nameText);
        readyCheckBox = (CheckBox) findViewById(R.id.readyCheckBox);

        lobbyText.setFilters(new InputFilter[] {new InputFilter.AllCaps()});

        members = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        membersListView.setAdapter(members);

        timerHandler = new Handler();

        gostopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!timing && buttonReady) {
                    buttonReady = false;
                    timing = true;
                    startTime = System.currentTimeMillis();
                    timerHandler.postDelayed(timerRunnable, 0);
                    gostopButton.setBackgroundColor(Color.RED);
                    gostopButton.setText("STOP");
                } else if(timing) {
                    lastTime = System.currentTimeMillis() - startTime;
                    timeText.setText(String.format("%.3f", (float) lastTime / 1000));
                    buttonReady = false;
                    timerHandler.removeCallbacks(timerRunnable);
                    timing = false;
                    gostopButton.setBackgroundColor(Color.RED);
                    gostopButton.setText("");
                    readyCheckBox.setChecked(false);
                    readyCheckBox.setEnabled(true);
                    setReadyToTime(false);
                    sendTime();
                }
            }
        });
        gostopButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(!timing) {
                    gostopButton.setBackgroundColor(Color.GREEN);
                    gostopButton.setText("GO");
                }
                buttonReady = true;
                return false;
            }
        });
        joinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(lobbyText.getText().toString().equals("")) {
                    Toast.makeText(getApplicationContext(), "Lobby can't be empty", Toast.LENGTH_LONG).show();
                    return;
                }
                if(nameText.getText().toString().equals("")) {
                    Toast.makeText(getApplicationContext(), "Name can't be empty", Toast.LENGTH_LONG).show();
                    return;
                }
               if(connectionThread != null) {
                   try {
                       socket.close();
                   } catch (IOException e) {
                       e.printStackTrace();
                   } catch (NullPointerException e) {
                   }
               }
               connectionThread = new Thread(connectionRunnable);
               connectionThread.start();
            }
        });
        leaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(connectionThread != null)
                try{
                    socket.close();
                    reset();
                }catch(IOException e) {
                    e.printStackTrace();
                }catch (NullPointerException e){}
            }
        });
        readyCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sendReady(isChecked);
            }
        });

        setReadyToTime(false);
    }

    private Runnable connectionRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                socket = new Socket("172.16.0.30", 8080);
                PrintStream out = new PrintStream(socket.getOutputStream());
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.println("suh dude");
                out.println(lobbyText.getText().toString());
                out.println(nameText.getText().toString());
                String line = in.readLine();
                if(line.equals("ERROR 1"))  {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),"Name already taken",Toast.LENGTH_SHORT).show();
                        }
                    });
                    socket.close();
                    return;
                }
                while((line = in.readLine()) != null) {
                    if(line.equals("MU")) { //Member update
                        ArrayList<String> m = new ArrayList<>();
                        while(!(line = in.readLine()).equals(".")) {
                            m.add(line);
                        }
                        updateMemberList(m);
                    }
                    if(line.equals("NS")) { //New scramble
                        final String s = in.readLine();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                scrambleText.setText(s);
                                readyCheckBox.setChecked(false);
                                readyCheckBox.setEnabled(false);
                            }
                        });
                        in.readLine();
                    }
                    if(line.equals("R")) {//Ready
                        setReadyToTime(true);
                        in.readLine();
                    }
                    if(line.equals("REMOVE")) {
                        reset();
                        socket.close();
                        return;
                    }
                }
            } catch(IOException e){
            }
        }
    };

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            time = System.currentTimeMillis() - startTime;

            timeText.setText(String.format("%.3f", (float) time / 1000));
            timerHandler.postDelayed(this,10);
        }
    };

    private void updateMemberList(final ArrayList<String> m) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                members.clear();
                for (int i = 0; i < m.size(); i++) {
                    members.add(m.get(i));
                }
            }
        });
    }

    private void setReadyToTime(final boolean ready) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gostopButton.setBackgroundColor(ready ? Color.RED : Color.LTGRAY);
                gostopButton.setEnabled(ready);

            }
        });
    }

    private void sendTime() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        PrintStream out = new PrintStream(socket.getOutputStream());
                        out.println("TU");//Time update
                        out.println(lastTime);
                        out.println(".");
                    }catch(IOException e){}

                }
            }).start();
    }

    private void sendReady(final boolean ready) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PrintStream out = new PrintStream(socket.getOutputStream());
                    out.println("READY");//Readt
                    out.println(ready ? 1 : 0);
                    out.println(".");
                }catch(IOException e){}
            }
        }).start();
    }

    private void reset() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scrambleText.setText("");
                timeText.setText("");
                members.clear();
                timerHandler.removeCallbacks(timerRunnable);
                gostopButton.setBackgroundColor(Color.LTGRAY);
                gostopButton.setEnabled(false);
            }
        });
    }
}
