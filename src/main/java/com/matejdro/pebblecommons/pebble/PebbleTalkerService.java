package com.matejdro.pebblecommons.pebble;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;

import com.getpebble.android.kit.util.PebbleDictionary;
import com.matejdro.pebblecommons.util.TimeoutService;

import org.json.JSONException;

import java.net.URISyntaxException;
import java.util.HashMap;

import timber.log.Timber;

public abstract class PebbleTalkerService extends TimeoutService
{
    public static final String INTENT_PEBBLE_PACKET = "PebblePacket";
    public static final String INTENT_PEBBLE_ACK = "PebbleAck";
    public static final String INTENT_PEBBLE_NACK = "PebbleNack";

    private SharedPreferences settings;

    protected PebbleDeveloperConnection devConn;

    private PebbleCommunication pebbleCommunication;

    private SparseArray<CommModule> modules = new SparseArray<CommModule>();
    private HashMap<String, CommModule> registeredIntents = new HashMap<String, CommModule>();

    private Handler handler;

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onDestroy()
    {
        if (devConn != null)
            devConn.close();
    }


    @Override
    public void onCreate()
    {
        settings = PreferenceManager.getDefaultSharedPreferences(this);

        pebbleCommunication = new PebbleCommunication(this);

        handler = new Handler();

        initDeveloperConnection();
        registerModules();

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
       if (intent != null && intent.getAction() != null)
       {
           if (intent.getAction().equals(INTENT_PEBBLE_PACKET))
           {
               String json = intent.getStringExtra("packet");
               receivedPacketFromPebble(json);
           }
           else if (intent.getAction().equals(INTENT_PEBBLE_ACK))
           {
               int transactionId = intent.getIntExtra("transactionId", -1);
               getPebbleCommunication().receivedAck(transactionId);
           }
           else if (intent.getAction().equals(INTENT_PEBBLE_NACK))
           {
               int transactionId = intent.getIntExtra("transactionId", -1);
               getPebbleCommunication().receivedNack(transactionId);
           }
           else
           {
               CommModule receivingModule = registeredIntents.get(intent.getAction());
               if (receivingModule != null)
                   receivingModule.gotIntent(intent);
           }
       }

        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    protected abstract void registerModules();

    protected void addModule(CommModule module, int id)
    {
        modules.put(id, module);
    }

    public CommModule getModule(int id)
    {
        return modules.get(id);
    }

    public void registerIntent(String action, CommModule module)
    {
        registeredIntents.put(action, module);
    }

    public SparseArray<CommModule> getAllModules()
    {
        return modules;
    }

    public SharedPreferences getGlobalSettings()
    {
        return settings;
    }

    public PebbleCommunication getPebbleCommunication()
    {
        return pebbleCommunication;
    }

    public void runOnMainThread(Runnable runnable)
    {
        handler.post(runnable);
    }

    public void runOnMainThreadDelayed(Runnable runnable, int time)
    {
        handler.postDelayed(runnable, time);
    }

    public Handler getHandler()
    {
        return handler;
    }

    protected void initDeveloperConnection()
    {
        try
        {
            devConn = new PebbleDeveloperConnection(this);
            devConn.connectBlocking();
        } catch (InterruptedException e)
        {
        } catch (URISyntaxException e)
        {
        }
    }


    public PebbleDeveloperConnection getDeveloperConnection()
    {
        if (!devConn.isOpen())
            initDeveloperConnection();

            return devConn;
    }

	private void receivedPacketFromPebble(String jsonPacket)
	{
        PebbleDictionary data = null;
        try
        {
            data = PebbleDictionary.fromJson(jsonPacket);
        } catch (Exception e)
        {
            Timber.e(e, "Error while parsing PebbleDictionary! %s", jsonPacket);
            e.printStackTrace();
            return;
        }


        int destination = data.getUnsignedIntegerAsLong(0).intValue();
        Timber.d("Pebble packet for %d", destination);

        CommModule module = modules.get(destination);
        if (module == null)
        {
            Timber.w("Destination module does not exist: %d  Packet: (%s).",destination, data.toJsonString());
            return;
        }

        module.gotMessageFromPebble(data);

	}
}
