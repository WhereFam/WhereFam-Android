// app/js/protocol-manager.js  (Android — no feeds)
'use strict'

const c               = require('compact-encoding')
const hyperswarmManager = require('./hyperswarm-manager')
const ipc             = require('./ipc')

const PROTOCOL = 'wherefam/v1'
const channels  = new Map() // peerHex → { locationMsg, placeMsg, sosMsg, batteryMsg, profileMsg }

let ownProfile = null

function setupProtocol () {
  console.log('[protocol] registering protocol handler')
  hyperswarmManager.registerProtocol(PROTOCOL, (mux, peerHex) => {
    console.log('[protocol] setting up channel for:', peerHex.slice(0, 12))

    const channel = mux.createChannel({
      protocol: PROTOCOL,
      onopen () {
        console.log('[protocol] open:', peerHex.slice(0, 12))
        // Send our profile immediately on channel open
        if (ownProfile) {
          try { profileMsg.send(ownProfile) } catch (e) {
            console.warn('[protocol] profile send on open failed:', e.message)
          }
        }
      },
      onclose () {
        channels.delete(peerHex)
        console.log('[protocol] closed:', peerHex.slice(0, 12))
      }
    })

    const locationMsg = channel.addMessage({
      encoding: c.json,
      onmessage (msg) {
        if (!msg.timestamp) msg.timestamp = Date.now()
        ipc.send('locationUpdate', msg)
      }
    })

    const placeMsg = channel.addMessage({
      encoding: c.json,
      onmessage (msg) { ipc.send('placeEvent', msg) }
    })

    const sosMsg = channel.addMessage({
      encoding: c.json,
      onmessage (msg) { ipc.send('sosAlert', msg) }
    })

    const batteryMsg = channel.addMessage({
      encoding: c.json,
      onmessage (msg) { ipc.send('batteryUpdate', msg) }
    })

    const profileMsg = channel.addMessage({
      encoding: c.json,
      onmessage (msg) {
        console.log('[protocol] received profile from:', peerHex.slice(0, 12))
        ipc.send('profileSync', msg)
      }
    })

    channels.set(peerHex, { locationMsg, placeMsg, sosMsg, batteryMsg, profileMsg })
    channel.open()
  })
}

function setOwnProfile (profile) {
  ownProfile = profile
  // Push to already-open channels
  for (const [, ch] of channels) {
    try { ch.profileMsg.send(profile) } catch (_) {}
  }
}

function sendLocation (data) {
  broadcast('locationMsg', {
    id:              data.id,
    name:            data.name,
    latitude:        data.latitude,
    longitude:       data.longitude,
    altitude:        data.altitude        ?? null,
    speed:           data.speed           ?? null,
    accuracy:        data.accuracy        ?? null,
    batteryLevel:    data.batteryLevel    ?? null,
    batteryCharging: data.batteryCharging ?? null,
    timestamp:       data.timestamp       || Date.now()
  })
}

function sendPlaceEvent (data) { broadcast('placeMsg',   data) }
function sendSOS         (data) { broadcast('sosMsg',     data) }
function sendBattery     (data) { broadcast('batteryMsg', data) }
function sendProfile     (data) { setOwnProfile(data); broadcast('profileMsg', data) }

function broadcast (msgKey, payload) {
  for (const [key, ch] of channels) {
    try { ch[msgKey].send(payload) } catch (e) {
      console.error('[protocol] send error to', key.slice(0, 12), e.message)
    }
  }
}

module.exports = {
  setupProtocol,
  sendLocation,
  sendPlaceEvent,
  sendSOS,
  sendBattery,
  sendProfile
}