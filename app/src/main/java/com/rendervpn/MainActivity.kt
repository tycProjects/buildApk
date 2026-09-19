package com.rendervpn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetAddresses
import com.wireguard.config.InetEndpoint
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var name: EditText
    private lateinit var configBox: EditText
    private var backend: Backend? = null
    private var tunnel: Tunnel? = null
    private var parsedConfig: Config? = null

    companion object { const val PICK_CONFIG=1001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status=findViewById(R.id.status)
        name=findViewById(R.id.name)
        configBox=findViewById(R.id.config)
        backend=GoBackend(this)

        findViewById<Button>(R.id.importButton).setOnClickListener {
            val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type="text/plain"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(i,PICK_CONFIG)
        }
        findViewById<Button>(R.id.parseButton).setOnClickListener { parseConfig() }
        findViewById<Button>(R.id.connectButton).setOnClickListener { toggleTunnel() }
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==PICK_CONFIG && resultCode==RESULT_OK && data?.data!=null) {
            val text=contentResolver.openInputStream(data.data!!)?.use {
                BufferedReader(InputStreamReader(it)).readText()
            } ?: return
            configBox.setText(text)
            parseConfig()
        }
    }

    private fun parseConfig() {
        try {
            parsedConfig=Config.parse(configBox.text.toString().byteInputStream())
            status.text="PROFILE READY"
            Toast.makeText(this,"WireGuard profile loaded.",Toast.LENGTH_SHORT).show()
        } catch(e:Exception) {
            parsedConfig=null
            status.text="INVALID PROFILE"
            Toast.makeText(this,"Invalid WireGuard configuration: ${e.message}",Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleTunnel() {
        val cfg=parsedConfig ?: run { parseConfig(); return }
        try {
            if(tunnel==null) {
                val t=object: Tunnel {
                    override fun getName()=name.text.toString().ifBlank{"render"}
                    override fun onStateChange(newState:Tunnel.State) {}
                }
                tunnel=t
            }
            val t=tunnel!!
            val current=backend!!.getState(t)
            if(current==Tunnel.State.UP) {
                backend!!.setState(t,Tunnel.State.DOWN,null)
                status.text="DISCONNECTED"
            } else {
                backend!!.setState(t,Tunnel.State.UP,cfg)
                status.text="CONNECTED"
            }
        } catch(e:Exception) {
            status.text="ERROR"
            Toast.makeText(this,e.message ?: "VPN error",Toast.LENGTH_LONG).show()
        }
    }
}
