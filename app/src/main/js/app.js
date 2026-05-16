// app/js/app.js  (Android — no feeds)
'use strict'

const ipc               = require('./ipc')
const hyperbeeManager   = require('./hyperbee-manager')
const identityManager   = require('./identity-manager')
const hyperswarmManager = require('./hyperswarm-manager')
const protocolManager   = require('./protocol-manager')
const pairingManager    = require('./pairing-manager')

let joiningInProgress = false

ipc.on('start', async (data) => {
  try {
    console.log('[app] booting...')
    const bee = await hyperbeeManager.initializeHyperbee(data.path)
    await identityManager.initIdentity(bee)
    const keyPair      = identityManager.getKeyPair()
    const publicKeyHex = identityManager.getPublicKeyHex()
    await hyperswarmManager.initializeHyperswarm(keyPair)
    protocolManager.setupProtocol()
    pairingManager.init(hyperswarmManager.getSwarm())
    // Rejoin known peers from previous sessions
    const knownPeers = await hyperbeeManager.getKnownPeers()
    for (const [peerHex] of knownPeers) {
      await hyperswarmManager.joinPeer(peerHex)
    }
    ipc.send('ready', { publicKey: publicKeyHex })
    console.log('[app] ready, pk:', publicKeyHex.slice(0, 12) + '...')
  } catch (err) {
    console.error('[app] startup failed:', err)
    ipc.send('startupError', { message: err.message })
  }
})

ipc.on('requestPublicKey', () => {
  ipc.send('publicKeyResponse', { publicKey: identityManager.getPublicKeyHex() })
})

ipc.on('joinPeer',  (data) => hyperswarmManager.joinPeer(data.peerPublicKey || data))
ipc.on('leavePeer', (data) => hyperswarmManager.leavePeer(data.peerPublicKey || data))

ipc.on('createInvite', async () => {
  joiningInProgress = false  // reset on new invite
  try {
    const invite = await pairingManager.createInvite(identityManager.getPublicKeyHex())
    ipc.send('inviteCreated', { invite })
  } catch (e) {
    console.error('[pairing] createInvite error:', e.message)
  }
})

ipc.on('joinWithInvite', async ({ invite }) => {
  if (joiningInProgress) {
    console.warn('[pairing] already joining, ignoring duplicate')
    return
  }
  joiningInProgress = true
  try {
    await pairingManager.joinWithInvite(invite, identityManager.getPublicKeyHex())
  } catch (e) {
    console.error('[pairing] joinWithInvite error:', e.message)
  } finally {
    setTimeout(() => { joiningInProgress = false }, 10000)
  }
})

ipc.on('locationUpdate', (data) => protocolManager.sendLocation(data))
ipc.on('placeEvent',     (data) => protocolManager.sendPlaceEvent(data))
ipc.on('sosAlert',       (data) => protocolManager.sendSOS(data))
ipc.on('batteryUpdate',  (data) => protocolManager.sendBattery(data))
ipc.on('saveProfile',    (data) => protocolManager.sendProfile(data))