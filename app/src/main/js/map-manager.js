// // src/map-manager.js
// const Hypercore = require('hypercore')
// const Corestore = require('corestore')
// const BlobServer = require('hypercore-blob-server')
// const b4a = require('b4a')
// const { PMTILES_KEY } = require('./constants')
// const Hyperswarm = require('hyperswarm')
//
// async function getMaps(documentsPath) {
//   try {
//     const key = b4a.from(PMTILES_KEY, 'hex')
//     const store = new Corestore(documentsPath + '/maps')
//
//     const swarm = new Hyperswarm()
//     swarm.on('connection', (conn) => {
//       store.replicate(conn)
//     })
//
//     const server = new BlobServer(store, {
//       token: false
//     })
//     await server.listen()
//     console.log('BlobServer listening.')
//
//     const filenameOpts = {
//       filename: '/20250512.pmtiles'
//     }
//
//     const topic = Hypercore.discoveryKey(key)
//     swarm.join(topic)
//   } catch (error) {
//     console.error('Error getting maps:', error)
//     throw error
//   }
// }
//
// module.exports = {
//   getMaps
// }
