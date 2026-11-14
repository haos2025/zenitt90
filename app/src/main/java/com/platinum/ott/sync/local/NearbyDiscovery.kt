package com.platinum.ott.sync.local

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NearbyPeer(val deviceId: String, val name: String, val type: String, val host: String, val port: Int)

/**
 * Поиск устройств рядом через NSD (mDNS, служба _zenith._tcp): регистрирует это устройство и ищет остальные.
 * Если роутер не пропускает multicast («изоляция клиентов»), список останется пустым — тогда есть ввод адреса вручную.
 */
class NearbyDiscovery(context: Context, private val identity: LocalIdentity) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val lock = Any()
    private val found = LinkedHashMap<String, NearbyPeer>()      // имя службы -> устройство
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var running = false
    private var ownServiceName: String? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null

    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers

    fun start(port: Int) {
        synchronized(lock) {
            if (running) return
            running = true
        }
        register(port)
        discover()
    }

    fun stop() {
        val reg: NsdManager.RegistrationListener?
        val disc: NsdManager.DiscoveryListener?
        synchronized(lock) {
            running = false
            reg = regListener; disc = discListener
            regListener = null; discListener = null
            found.clear(); queue.clear(); resolving = false
        }
        _peers.value = emptyList()
        try { reg?.let { nsd.unregisterService(it) } } catch (_: Exception) {}
        try { disc?.let { nsd.stopServiceDiscovery(it) } } catch (_: Exception) {}
    }

    private fun register(port: Int) {
        val myId = identity.deviceId
        val info = NsdServiceInfo().apply {
            serviceName = "Zenith-" + myId.take(8)
            serviceType = LocalProto.SERVICE_TYPE
            this.port = port
            setAttribute("id", myId)
            setAttribute("name", identity.deviceName)
            setAttribute("type", identity.deviceType)
            setAttribute("v", LocalProto.VERSION.toString())
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(si: NsdServiceInfo) { synchronized(lock) { ownServiceName = si.serviceName } }
            override fun onRegistrationFailed(si: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(si: NsdServiceInfo) {}
            override fun onUnregistrationFailed(si: NsdServiceInfo, errorCode: Int) {}
        }
        synchronized(lock) { regListener = l }
        try { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) } catch (_: Exception) {}
    }

    private fun discover() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(si: NsdServiceInfo) {
                synchronized(lock) {
                    if (!running || si.serviceName == ownServiceName) return
                    queue.addLast(si)
                }
                resolveNext()
            }

            override fun onServiceLost(si: NsdServiceInfo) {
                synchronized(lock) { found.remove(si.serviceName) }
                publish()
            }
        }
        synchronized(lock) { discListener = l }
        try { nsd.discoverServices(LocalProto.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) } catch (_: Exception) {}
    }

    // NSD разрешает по одной службе за раз (иначе FAILURE_ALREADY_ACTIVE) — идём по очереди.
    private fun resolveNext() {
        val next = synchronized(lock) {
            if (!running || resolving) return
            val n = queue.removeFirstOrNull() ?: return
            resolving = true
            n
        }
        val l = object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) { resolving = false }
                resolveNext()
            }

            override fun onServiceResolved(si: NsdServiceInfo) {
                try {
                    val attrs = si.attributes
                    val id = attrs["id"]?.let { String(it, Charsets.UTF_8) }.orEmpty()
                    val host = si.host?.hostAddress
                    if (id.isNotBlank() && id != identity.deviceId && host != null) {
                        val peer = NearbyPeer(
                            id, attrs["name"]?.let { String(it, Charsets.UTF_8) }.orEmpty().ifBlank { "Устройство" },
                            attrs["type"]?.let { String(it, Charsets.UTF_8) }.orEmpty(), host, si.port
                        )
                        synchronized(lock) { if (running) found[si.serviceName] = peer }
                        publish()
                    }
                } finally {
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }
            }
        }
        try {
            nsd.resolveService(next, l)
        } catch (_: Exception) {
            synchronized(lock) { resolving = false }
        }
    }

    private fun publish() {
        _peers.value = synchronized(lock) { found.values.toList() }
    }
}
