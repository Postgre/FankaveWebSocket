package com.example.SecureWebSocket.wsstestapplication2;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.example.SecureWebSocket.securewebsocketlibrary.*;

import org.json.JSONException;
import org.json.JSONObject;

public class WssMainActivity extends Activity {

    TextView receivedMsg=null,status=null,errorDetails=null;
    EditText sendMessage=null;
    EditText sendMessage2 = null;
    String Msg ="";
    String sysStatusString ;
    String errorDetailsString;
    boolean taskRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wss_main);
         receivedMsg= (TextView)findViewById(R.id.receiveEditText);
         sendMessage = (EditText)findViewById(R.id.sendEditText);
         sendMessage2=(EditText)findViewById(R.id.sendEditText2);
         status = (TextView)findViewById(R.id.statusTextView);
        errorDetails=(TextView)findViewById(R.id.errorDetailsTextView);
        status.setText("");
        errorDetails.setText("");
    }

    public class DataHolderClass{
        public String team1;
        public String team2;
    }

    public void sendTextWSS(View view) throws InterruptedException {
        //Handles the action after send button is clicked.

       final HeaderConfiguration headerConfiguration = new HeaderConfiguration();
        headerConfiguration.setUserInfo("Android", "Websocket");

        DataHolderClass obj = new DataHolderClass();
        obj.team1 = sendMessage.getText().toString();
        obj.team2 = sendMessage2.getText().toString();
        final String jsonString = encodeJSONS(obj);

        status.setText("");
        errorDetails.setText("");

        Thread thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                taskRunning= true;
                String tempStatus =null;
                String tempErrorDetails =null;
                String tempMsg =null;
                try
                {
                    try {

                        new WebSocketFactory().createSocket("wss://echo.websocket.org",headerConfiguration).addListener(new com.example.SecureWebSocket.securewebsocketlibrary.WebSocketAdapter() {
                            @Override
                            public void onTextMessage(com.example.SecureWebSocket.securewebsocketlibrary.WebSocket ws, String message) {
                                // Received a response. set the received message to text of receiveEditText.
                                Msg=decodeJSON(message);

                                //receivedMsg.setText(Msg);

                                // Close the WebSocket connection.
                                ws.disconnect();
                            }
                        })
                                .connect()
                                .sendText(jsonString);
                        tempStatus="Successful";


                    }
                    catch (WebSocketException e)
                    {
                        //show message when exception occurs.
                        tempStatus="Failed1";
                        tempErrorDetails=e.toString();
                    }

                    catch (Exception e)
                    {
                        //show message when exception occurs.
                        tempErrorDetails=e.toString();
                        tempStatus="Failed2";
                    }
                }
                catch (Exception e)
                {
                    tempErrorDetails=e.toString();
                    tempStatus="Failed2";
                }
                sysStatusString=tempStatus;
                errorDetailsString=tempErrorDetails;
                Msg=tempMsg;
                taskRunning = false;
            }

        });

        thread.start();

        Thread.sleep(500);

        while(taskRunning)
        {
            try {
                Thread.sleep(1000);
            }
            catch (Exception e)
            {

            }
        }

        receivedMsg.setText(Msg);
        status.setText(sysStatusString);
        errorDetails.setText(errorDetailsString);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_wss_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
      super.onStart();
        receivedMsg.setText(Msg);

    }

    @Override
    protected void onResume() {
        super.onResume();
        receivedMsg.setText(Msg);
    }

    private String encodeJSONS(DataHolderClass obj)
    {
        //Creates and returns a JSON string.
        return "{\n" +
                "   \"PremierLeague\":\n" +
                "   {\n" +
                "      \"FirstTeam\":\""+obj.team1+"\",\n" +
                "      \"SecondTeam\":\""+obj.team2+"\"     \n" +
                "   }\n" +
                "}";

    }

    private String decodeJSON(String receviedData)
    {
        //Decode a JSON string and provides required information.
        try {
            JSONObject jsonObject = new JSONObject(receviedData);
            JSONObject PremierLeague = jsonObject.getJSONObject("PremierLeague");
            String FirstTeam = PremierLeague.getString("FirstTeam");
            String SecondTeam = PremierLeague.getString("SecondTeam");
            return FirstTeam + " Vs " + SecondTeam;
        }
        catch (JSONException je)
        {
            return "Incorrect JSON Format.";

        }
    }
}
