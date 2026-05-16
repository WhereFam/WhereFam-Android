// app/js/hyperswarm-manager.js
'use strict'

const Hyperswarm  = require('hyperswarm')
const Protomux    = require('protomux')
const b4a         = require('b4a')
const ipc         = require('./ipc')
const hyperbeeManager = require('./hyperbee-manager')

let swarm = null
const connections    = new Map() // hex → { mux, conn }
const protocolHandlers = new Map()

async function initializeHyperswarm (keyPair) {
  if (swarm) return swarm
  swarm = new Hyperswarm({ keyPair })
  swarm.on('connection', handleConnection)
  console.log('[hyperswarm] ready')
  return swarm
}

function handleConnection (conn, info) {
  const keyBuf = info.publicKey
  const key    = b4a.toString(keyBuf, 'hex')
  console.log('[hyperswarm] connected:', key.slice(0, 12))

  const mux = new Protomux(conn)
  connections.set(key, { mux, conn })

  // Register all protocol handlers
  for (const handler of protocolHandlers.values()) handler(mux, key)

  // Persist peer for reconnection on reboot
  hyperbeeManager.savePeer(key).catch(console.error)

  conn.once('close', () => {
    connections.delete(key)
    ipc.send('peerDisconnected', { peerKey: key })
    console.log('[hyperswarm] disconnected:', key.slice(0, 12))
  })

  conn.on('error', (e) => console.warn('[hyperswarm] conn error:', e.message))
}

async function joinPeer (peerHex) {
  if (!swarm) return
  try {
    const topic = deriveTopic(peerHex, getOwnHex())
    swarm.join(topic)
    console.log('[hyperswarm] joining peer:', peerHex.slice(0, 12))
  } catch (e) {
    console.error('[hyperswarm] joinPeer error:', e.message)
  }
}

async function leavePeer (peerHex) {
  if (!swarm) return
  try {
    const conn = connections.get(peerHex)
    if (conn) conn.conn.destroy()
    connections.delete(peerHex)
  } catch (e) {
    console.error('[hyperswarm] leavePeer error:', e.message)
  }
}

function getOwnHex () {
  return b4a.toString(swarm.keyPair.publicKey, 'hex')
}

function deriveTopic (hexA, hexB) {
  // Deterministic shared topic = hash of sorted pair of keys
  const crypto = require('hypercore-crypto')
  const sorted = [hexA, hexB].sort().join('')
  return crypto.hash(b4a.from(sorted, 'hex'))
}

function registerProtocol (name, handler) {
  protocolHandlers.set(name, handler)
}

function getSwarm () {
  if (!swarm) throw new Error('Hyperswarm not initialized')
  return swarm
}

module.exports = {
  initializeHyperswarm,
  joinPeer,
  leavePeer,
  getSwarm,
  registerProtocol
}