package com.naos.pikminbee.lan

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LanClientSocketsTest {
    @Test fun shutdownClosesBothRunningAndQueuedClients() {
        val clients = LanClientSockets()
        val running = Socket()
        val queued = Socket()
        assertTrue(clients.register(running))
        assertTrue(clients.register(queued))
        clients.closeAll()
        assertTrue(running.isClosed)
        assertTrue(queued.isClosed)
    }

    @Test fun registrationAfterShutdownClosesTheLateAcceptedSocket() {
        val clients = LanClientSockets()
        clients.closeAll()
        val late = Socket()
        assertFalse(clients.register(late))
        assertTrue(late.isClosed)
    }

    @Test fun releaseClosesClientWithoutClosingOtherClients() {
        val clients = LanClientSockets()
        val completed = Socket()
        val active = Socket()
        assertTrue(clients.register(completed))
        assertTrue(clients.register(active))
        clients.release(completed)
        assertTrue(completed.isClosed)
        assertFalse(active.isClosed)
        clients.closeAll()
        assertTrue(active.isClosed)
    }

    @Test fun shutdownAndLateReleaseAreIdempotent() {
        val clients = LanClientSockets()
        val client = Socket()
        assertTrue(clients.register(client))
        clients.closeAll()
        clients.closeAll()
        clients.release(client)
        assertTrue(client.isClosed)
        val late = Socket()
        assertFalse(clients.register(late))
        assertTrue(late.isClosed)
    }

    @Test fun shutdownUnblocksReadOnLoopbackConnection() {
        val clients = LanClientSockets()
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        try {
            val loopback = InetAddress.getLoopbackAddress()
            ServerSocket(0, 1, loopback).use { listener ->
                Socket(loopback, listener.localPort).use {
                    listener.accept().use { accepted ->
                        assertTrue(clients.register(accepted))
                        accepted.soTimeout = 5_000
                        val reader = executor.submit<Boolean> {
                            started.countDown()
                            try {
                                accepted.getInputStream().read()
                                false
                            } catch (_: SocketException) {
                                true
                            }
                        }
                        assertTrue(started.await(2, TimeUnit.SECONDS))
                        clients.closeAll()
                        assertTrue(reader.get(2, TimeUnit.SECONDS))
                        assertTrue(accepted.isClosed)
                    }
                }
            }
        } finally {
            clients.closeAll()
            executor.shutdownNow()
        }
    }
}
